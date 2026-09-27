<#
.SYNOPSIS
  把 RustFS 桶（对象 + 访问策略）导出到本地目录，供离线迁移包使用。

.DESCRIPTION
  两种模式，自动择优：
    * mc     —— 复用本机或**其他项目已下载的 mc.exe**（例如 CampusRepair 的离线迁移包里那份），
                `mc mirror --preserve` 增量镜像，最稳；
    * sigv4  —— 纯 PowerShell 实现 AWS SigV4 签名（见 tools/s3-common.ps1），零外部依赖，
                适合 mc 下载不到的机器。
  -Mode auto（默认）会先找 mc，找不到就用 sigv4。

  导出结果：<OutDir>\s3\<bucket>\<对象 key>（相对路径即对象 key）与 <OutDir>\bucket-policy.json。

.EXAMPLE
  pwsh tools/rustfs-export.ps1 -OutDir backup\migration-bundle-x\rustfs
  pwsh tools/rustfs-export.ps1 -OutDir D:\dump -Mode sigv4
#>
param(
    [string]$Endpoint = 'http://127.0.0.1:9990',
    [string]$AccessKey = 'rustfsadmin',
    [string]$SecretKey = 'rustfsadmin',
    [string]$Bucket = 'scholar-mind',
    [Parameter(Mandatory = $true)][string]$OutDir,
    [ValidateSet('auto', 'mc', 'sigv4')][string]$Mode = 'auto',
    [string]$McPath = '',
    [switch]$SkipPolicy
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 's3-common.ps1')

$s3Dir = Join-Path $OutDir "s3\$Bucket"
New-Item -ItemType Directory -Force -Path $s3Dir | Out-Null

$usedMode = $Mode
$mc = $null
if ($Mode -in 'auto', 'mc') {
    $mc = Resolve-McPath -Preferred $McPath
    if (-not $mc -and $Mode -eq 'mc') { throw '未找到可用的 mc.exe；可改用 -Mode sigv4（零依赖）' }
    if ($mc) { $usedMode = 'mc' }
    else { Write-Host '      未找到 mc，改用 SigV4 模式（零依赖）' -ForegroundColor Yellow; $usedMode = 'sigv4' }
}

$objectCount = 0
$totalBytes = 0

if ($usedMode -eq 'mc') {
    Write-Host ("[RustFS] 使用 mc 导出（{0}）" -f $mc) -ForegroundColor Cyan
    & $mc alias set sm-src $Endpoint $AccessKey $SecretKey 2>&1 | Select-Object -Last 1 | ForEach-Object { "      $_" }
    & $mc mb --ignore-existing "sm-src/$Bucket" 2>&1 | Select-Object -Last 1 | ForEach-Object { "      $_" }
    $mirrorOut = & $mc mirror --preserve --overwrite "sm-src/$Bucket" $s3Dir 2>&1
    $mirrorOut | Select-Object -Last 3 | ForEach-Object { "      $_" }
    $summary = & $mc ls --recursive --summarize "sm-src/$Bucket" 2>&1 | Select-Object -Last 2
    $summary | ForEach-Object { "      源端统计：$_" }
    $objLine = $summary | Where-Object { $_ -match 'Total Objects' } | Select-Object -First 1
    $sizeLine = $summary | Where-Object { $_ -match 'Total Size' } | Select-Object -First 1
    if ($objLine) { $objectCount = [int](($objLine -replace '.*:\s*', '').Trim()) }
    if ($sizeLine) {
        $sizeText = ($sizeLine -replace '.*:\s*', '').Trim()
        $facts = $sizeText -split ' '
        $value = [double]$facts[0]
        switch ($facts[1]) {
            'KiB' { $totalBytes = $value * 1KB } 'MiB' { $totalBytes = $value * 1MB } 'GiB' { $totalBytes = $value * 1GB } 'B' { $totalBytes = $value } default { $totalBytes = $value }
        }
    }
    if (-not $SkipPolicy) {
        $policy = & $mc anonymous get-json "sm-src/$Bucket" 2>&1
        if ($policy -and ($policy -join '').Trim() -match '^\{') {
            Set-Content -Path (Join-Path $OutDir 'bucket-policy.json') -Value ($policy -join "`n") -Encoding UTF8
            Write-Host '      已导出桶访问策略 bucket-policy.json' -ForegroundColor Green
        } else {
            Write-Host ("      源端未设置匿名访问策略（{0}），目标机沿用默认即可" -f (($policy -join ' ').Trim())) -ForegroundColor Yellow
        }
    }
} else {
    Write-Host '[RustFS] 使用 SigV4 模式导出（零依赖）' -ForegroundColor Cyan
    $objects = Get-S3ObjectList -Endpoint $Endpoint -AccessKey $AccessKey -SecretKey $SecretKey -Bucket $Bucket
    Write-Host ("      发现 {0} 个对象" -f $objects.Count)
    foreach ($object in $objects) {
        $target = Join-Path $s3Dir ($object.Key -replace '/', '\')
        $written = Get-S3Object -Endpoint $Endpoint -AccessKey $AccessKey -SecretKey $SecretKey -Bucket $Bucket `
            -Key $object.Key -OutFile $target
        $objectCount++
        $totalBytes += $written
    }
    Write-Host ("      已导出 {0} 个对象" -f $objectCount) -ForegroundColor Green
    if (-not $SkipPolicy) {
        $policy = Get-S3BucketPolicy -Endpoint $Endpoint -AccessKey $AccessKey -SecretKey $SecretKey -Bucket $Bucket
        if ($policy -and $policy.Trim() -match '^\{') {
            Set-Content -Path (Join-Path $OutDir 'bucket-policy.json') -Value $policy -Encoding UTF8
            Write-Host '      已导出桶访问策略 bucket-policy.json' -ForegroundColor Green
        } else {
            Write-Host '      源端未设置匿名访问策略（或无权限读取），目标机沿用默认即可' -ForegroundColor Yellow
        }
    }
}

$localFiles = @(Get-ChildItem $s3Dir -Recurse -File -ErrorAction SilentlyContinue)
$localBytes = ($localFiles | Measure-Object -Property Length -Sum).Sum

Write-Host ("[RustFS] 完成：模式={0}，对象 {1} 个 / {2:N1} KB，落盘文件 {3} 个 / {4:N1} KB" -f `
        $usedMode, $objectCount, ($totalBytes / 1KB), $localFiles.Count, ($localBytes / 1KB)) -ForegroundColor Green

return [pscustomobject]@{
    Mode        = $usedMode
    ObjectCount = $objectCount
    TotalBytes  = [int64]$totalBytes
    LocalFiles  = $localFiles.Count
    LocalBytes  = [int64]$localBytes
    S3Dir       = $s3Dir
    McPath      = $mc
}
