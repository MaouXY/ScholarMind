<#
.SYNOPSIS
  目标机侧：把离线迁移包里的对象灌回 RustFS 桶，并应用桶访问策略。

.DESCRIPTION
  源目录既可以是迁移包根目录（自动定位 <包>\rustfs），也可以是 rustfs 子目录本身。
  两种模式，自动择优（与 rustfs-export.ps1 对称）：
    * mc    —— 复用本机/其他项目已下载的 mc.exe，`mc mirror` 增量灌入；
    * sigv4 —— 纯 PowerShell SigV4 逐对象 PUT（零依赖）。

.EXAMPLE
  # 演练：只列出将要上传的对象与目标连通性
  pwsh tools/rustfs-import.ps1 -SourceDir backup\migration-bundle-20260927-2156 -DryRun

.EXAMPLE
  pwsh tools/rustfs-import.ps1 -SourceDir backup\migration-bundle-20260927-2156 -Endpoint http://127.0.0.1:9990
#>
param(
    [Parameter(Mandatory = $true)][string]$SourceDir,
    [string]$Endpoint = 'http://127.0.0.1:9990',
    [string]$AccessKey = 'rustfsadmin',
    [string]$SecretKey = 'rustfsadmin',
    [string]$Bucket = 'scholar-mind',
    [ValidateSet('auto', 'mc', 'sigv4')][string]$Mode = 'auto',
    [string]$McPath = '',
    [switch]$SkipPolicy,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 's3-common.ps1')

# ---- 定位包内的 rustfs 目录（允许目标桶名与源桶名不同） ----
$candidateRoots = @(
    (Join-Path $SourceDir 'rustfs'),
    $SourceDir
)
$rustfsDir = $null
$sourceBucketDir = $null
foreach ($candidate in $candidateRoots) {
    $s3Root = Join-Path $candidate 's3'
    if (-not (Test-Path $s3Root)) { continue }
    $exact = Join-Path $s3Root $Bucket
    if (Test-Path $exact) { $rustfsDir = $candidate; $sourceBucketDir = $exact; break }
    # 桶名不一致（例如迁移到改名后的桶）：包内只有一个 s3 子目录时直接复用，并给出提示
    $bucketDirs = @(Get-ChildItem $s3Root -Directory -ErrorAction SilentlyContinue)
    if ($bucketDirs.Count -eq 1) {
        $rustfsDir = $candidate
        $sourceBucketDir = $bucketDirs[0].FullName
        Write-Host ("  提示：包内对象目录为 s3\{0}，与目标桶 {1} 不同名，将按对象 key 原样导入目标桶" -f `
                $bucketDirs[0].Name, $Bucket) -ForegroundColor Yellow
        break
    }
}
if (-not $rustfsDir) {
    $available = @()
    foreach ($candidate in $candidateRoots) {
        $s3Root = Join-Path $candidate 's3'
        if (Test-Path $s3Root) { $available += (@(Get-ChildItem $s3Root -Directory) | ForEach-Object { $_.Name }) }
    }
    throw ("在 {0} 下找不到可用的 s3\<bucket> 目录（包内现有桶：{1}）。请检查 -SourceDir 与 -Bucket。" -f `
            $SourceDir, (($available | Select-Object -Unique) -join ', '))
}
$s3Dir = $sourceBucketDir
$policyFile = Join-Path $rustfsDir 'bucket-policy.json'
$files = @(Get-ChildItem $s3Dir -Recurse -File)
$totalBytes = ($files | Measure-Object -Property Length -Sum).Sum
Write-Host ("源目录：{0}" -f $s3Dir) -ForegroundColor Cyan
Write-Host ("待导入：{0} 个对象 / {1:N1} KB" -f $files.Count, ($totalBytes / 1KB))
if (Test-Path $policyFile) { Write-Host '  发现 bucket-policy.json，导入后会自动应用' }

if ($DryRun) {
    Write-Host '（-DryRun）仅预览，不写入。前 5 个对象 key：' -ForegroundColor Yellow
    $files | Select-Object -First 5 | ForEach-Object {
        "    {0}" -f $_.FullName.Substring($s3Dir.Length + 1).Replace('\', '/')
    }
    return
}

if (-not (Test-S3Endpoint -Endpoint $Endpoint)) {
    throw @"
无法连接 RustFS 端点：$Endpoint
  可能原因：RustFS 未启动 / 端口不对 / 容器未映射到该端口。
  排查：用 Docker 的话先 `docker ps` 看容器的端口映射（常见是容器 9000 映射到宿主 9990），
        再用 `curl.exe -s -o NUL -w "%{http_code}" $Endpoint/<bucket>` 验证（连接被拒=没起）。
  注意：端点不可达时 mc 会把 `dst/xxx` 当成本地目录并"成功"建目录，最后得到 0 个对象与
        一串 filesystem 相关报错，请先修好端点再重跑本脚本。
"@
}

$usedMode = $Mode
$mc = $null
if ($Mode -in 'auto', 'mc') {
    $mc = Resolve-McPath -Preferred $McPath
    if (-not $mc -and $Mode -eq 'mc') { throw '未找到可用的 mc.exe；可改用 -Mode sigv4（零依赖）' }
    if ($mc) { $usedMode = 'mc' }
    else { Write-Host '  未找到 mc，改用 SigV4 模式（零依赖）' -ForegroundColor Yellow; $usedMode = 'sigv4' }
}

if ($usedMode -eq 'mc') {
    Write-Host ("使用 mc 导入（{0}）" -f $mc) -ForegroundColor Cyan
    & $mc alias set sm-dst $Endpoint $AccessKey $SecretKey 2>&1 | Select-Object -Last 1 | ForEach-Object { "  $_" }
    if ($LASTEXITCODE -ne 0) {
        throw "mc 别名配置失败（HTTP 探测未通过）。请确认 RustFS 已在 $Endpoint 运行；若继续执行，mc 会把 `"sm-dst/...`" 当成本地路径而静默写错位置。"
    }
    & $mc mb --ignore-existing "sm-dst/$Bucket" 2>&1 | Select-Object -Last 1 | ForEach-Object { "  $_" }
    & $mc mirror --preserve --overwrite $s3Dir "sm-dst/$Bucket" 2>&1 | Select-Object -Last 3 | ForEach-Object { "  $_" }
    if ((Test-Path $policyFile) -and -not $SkipPolicy) {
        & $mc anonymous set-json $policyFile "sm-dst/$Bucket" 2>&1 | Select-Object -Last 1 | ForEach-Object { "  应用桶策略：$_" }
    }
    & $mc ls --recursive --summarize "sm-dst/$Bucket" 2>&1 | Select-Object -Last 2 | ForEach-Object { "  目标端：$_" }
} else {
    Write-Host '使用 SigV4 模式导入（零依赖）' -ForegroundColor Cyan
    $status = New-S3Bucket -Endpoint $Endpoint -AccessKey $AccessKey -SecretKey $SecretKey -Bucket $Bucket
    Write-Host ("  建桶返回：HTTP {0}（200=新建，409=已存在）" -f $status)
    $done = 0
    foreach ($file in $files) {
        $key = $file.FullName.Substring($s3Dir.Length + 1).Replace('\', '/')
        $written = Put-S3Object -Endpoint $Endpoint -AccessKey $AccessKey -SecretKey $SecretKey -Bucket $Bucket `
            -Key $key -InFile $file.FullName
        $done++
    }
    Write-Host ("  已写入 {0} 个对象" -f $done) -ForegroundColor Green
    if ((Test-Path $policyFile) -and -not $SkipPolicy) {
        $policyStatus = Set-S3BucketPolicy -Endpoint $Endpoint -AccessKey $AccessKey -SecretKey $SecretKey -Bucket $Bucket -PolicyFile $policyFile
        Write-Host ("  应用桶策略返回：HTTP {0}" -f $policyStatus)
    }
    $objects = Get-S3ObjectList -Endpoint $Endpoint -AccessKey $AccessKey -SecretKey $SecretKey -Bucket $Bucket
    Write-Host ("  目标端对象数：{0}" -f $objects.Count) -ForegroundColor Green
}

Write-Host ''
Write-Host '导入完成。请再确认：' -ForegroundColor Green
Write-Host '  1) 目标端对象数与包内一致；'
Write-Host '  2) 直接打开任一文件的 public_url 应返回 200（403 说明桶策略未应用）；'
Write-Host '  3) 若目标机存储地址/端口与源机不同，请批量替换历史 URL：'
Write-Host '     UPDATE file_metadata SET public_url = REPLACE(public_url, ''<源endpoint>'', ''<目标endpoint>'');'
