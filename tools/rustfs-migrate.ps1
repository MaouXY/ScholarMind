<#
.SYNOPSIS
  用 mc（MinIO 客户端）把 ScholarMind 的 RustFS 桶连对象带策略迁移到另一台实例。

.DESCRIPTION
  适用场景：两台机器的 RustFS 卷布局不同（多卷纠删码 vs 单卷直存），磁盘目录不能直接拷，必须按桶做逻辑迁移。

  脚本动作：
    ① 必要时下载 mc（dl.min.io 在部分网络不可达，因此优先从 GitHub release 取）
    ② 配置源/目标别名  ③ 目标端建桶  ④ mirror 对象（增量、幂等、保留对象 key 与时间戳）
    ⑤ 迁移桶访问策略（mirror 不带策略）  ⑥ 对比两端对象数与总大小

  注意事项：ScholarMind 的对象 key 形如 `scholar-mind/<role>/<date>/<userId>/<uuid>.<ext>`
  （base-path 与桶名同为 `scholar-mind`），因此镜像后**相对路径就是对象 key**，无需额外处理。

.EXAMPLE
  pwsh tools/rustfs-migrate.ps1 -TargetEndpoint http://192.168.1.50:9990 -DryRun
  pwsh tools/rustfs-migrate.ps1 -TargetEndpoint http://192.168.1.50:9990
#>
param(
    [Parameter(Mandatory = $true)][string]$TargetEndpoint,
    [string]$SourceEndpoint = 'http://127.0.0.1:9990',
    [string]$Bucket = 'scholar-mind',
    [string]$AccessKey = 'rustfsadmin',
    [string]$SecretKey = 'rustfsadmin',
    [string]$McPath = '',
    [switch]$SkipPolicy,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

# ---------- ① mc 可执行文件（优先复用本机/其他项目里已下载的副本，取不到再尝试下载） ----------
. (Join-Path $PSScriptRoot 's3-common.ps1')
$McPath = Resolve-McPath -Preferred $McPath
if (-not $McPath) {
    throw '未找到可用的 mc.exe。受限网络下建议改用零依赖模式：tools/rustfs-export.ps1 + tools/rustfs-import.ps1 -Mode sigv4'
}
Write-Host ("[1/6] 使用 mc：{0}" -f $McPath) -ForegroundColor Cyan
& $McPath --version 2>&1 | Select-Object -First 1 | ForEach-Object { "      $_" }

function Invoke-Mc { param([string[]]$Arguments)
    & $McPath @Arguments 2>&1
}

# ---------- ② 别名 ----------
Write-Host '[2/6] 配置源/目标别名 ...' -ForegroundColor Cyan
Invoke-Mc @('alias', 'set', 'rfs-src', $SourceEndpoint, $AccessKey, $SecretKey) | Select-Object -Last 1 | ForEach-Object { "      $_" }
Invoke-Mc @('alias', 'set', 'rfs-dst', $TargetEndpoint, $AccessKey, $SecretKey) | Select-Object -Last 1 | ForEach-Object { "      $_" }

Write-Host '      源端对象统计：' -ForegroundColor Cyan
Invoke-Mc @('ls', '--recursive', '--summarize', "rfs-src/$Bucket") | Select-Object -Last 2 | ForEach-Object { "        $_" }

if ($DryRun) {
    Write-Host '（-DryRun）仅检查配置与源端统计，未做任何写入。' -ForegroundColor Yellow
    return
}

# ---------- ③ 目标端建桶 ----------
Write-Host '[3/6] 目标端建桶（已存在会提示跳过）...' -ForegroundColor Cyan
$existing = Invoke-Mc @('ls', 'rfs-dst/')
if ($existing -match "$([regex]::Escape($Bucket))/") {
    Write-Host "      桶 $Bucket 已存在，直接增量同步" -ForegroundColor Yellow
} else {
    Invoke-Mc @('mb', "rfs-dst/$Bucket") | ForEach-Object { "      $_" }
}

# ---------- ④ 镜像对象 ----------
Write-Host '[4/6] 开始镜像对象（增量、幂等，可重复执行）...' -ForegroundColor Cyan
Invoke-Mc @('mirror', '--preserve', '--overwrite', "rfs-src/$Bucket", "rfs-dst/$Bucket") | ForEach-Object { "      $_" }

# ---------- ⑤ 桶策略 ----------
if (-not $SkipPolicy) {
    Write-Host '[5/6] 迁移桶访问策略（mirror 不包含策略）...' -ForegroundColor Cyan
    $policyPath = Join-Path $env:TEMP 'rustfs-bucket-policy.json'
    $policy = Invoke-Mc @('anonymous', 'get-json', "rfs-src/$Bucket")
    if ($policy -and ($policy -join '').Trim() -match '^\{') {
        Set-Content -Path $policyPath -Value ($policy -join "`n") -Encoding UTF8
        Invoke-Mc @('anonymous', 'set-json', $policyPath, "rfs-dst/$Bucket") | ForEach-Object { "      应用策略：$_" }
    } else {
        Write-Host "      源端策略读取结果：$($policy -join ' ')，跳过（如需显式关闭本步可加 -SkipPolicy）" -ForegroundColor Yellow
    }
} else {
    Write-Host '[5/6] 已跳过桶策略迁移（-SkipPolicy）' -ForegroundColor Yellow
}

# ---------- ⑥ 校验 ----------
Write-Host '[6/6] 对比两端对象数与总大小 ...' -ForegroundColor Cyan
foreach ($side in 'rfs-src', 'rfs-dst') {
    $label = if ($side -eq 'rfs-src') { '源端' } else { '目标端' }
    Write-Host ("      {0}：" -f $label)
    Invoke-Mc @('ls', '--recursive', '--summarize', "$side/$Bucket") | Select-Object -Last 2 | ForEach-Object { "        $_" }
}

Write-Host ''
Write-Host '迁移完成。请再确认：' -ForegroundColor Green
Write-Host '  1) 目标端 RustFS 控制台（默认 :9991）里该桶对象数与源端一致；'
Write-Host '  2) 浏览器打开任一文件的 public_url 应返回 200（403 说明桶策略没应用）；'
Write-Host '  3) 若目标机存储地址/端口与源机不同，MySQL 里的历史 URL 需要批量替换：'
Write-Host "     UPDATE file_metadata SET public_url = REPLACE(public_url, '$SourceEndpoint', '$TargetEndpoint');"
Write-Host '  4) 若桶名或 base-path 也变了，需同时同步 application.yaml 的 app.storage.rustfs.* 与 app.storage.base-path。'
