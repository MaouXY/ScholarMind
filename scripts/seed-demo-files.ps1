# ============================================================================
# ScholarMind 演示数据：为已发布文献上传并绑定原文文件
# 用途：让「我的书房 / 文献详情」的下载按钮有真实文件可下载（RustFS 预签名地址）
# 前置：后端已启动（默认 8992）、MySQL 与 RustFS 可用、已执行 01~08 号 SQL
# 说明：脚本可重复执行；已绑定文件的文献会跳过；上传后会把 file_metadata 的业务归属改为 LITERATURE
# ============================================================================
param(
    [string]$BaseUrl = 'http://127.0.0.1:8992/api/v1',
    [string]$Username = 'admin',
    [string]$Password = '123456',
    [string]$MysqlPath = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
    [string]$DbUser = 'root',
    [string]$DbPassword = '1829002',
    [string]$DbName = 'scholar_mind',
    [int]$Limit = 20
)

$ErrorActionPreference = 'Stop'
$tempDir = Join-Path $env:TEMP 'scholarmind-demo-files'
New-Item -ItemType Directory -Path $tempDir -Force | Out-Null

Write-Host '==> 登录'
$login = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -ContentType 'application/json' `
    -Body (@{ username = $Username; password = $Password; role = 'ADMIN' } | ConvertTo-Json)
if ($login.code -ne 0) { throw "登录失败: $($login.message)" }
$token = $login.data.token
$headers = @{ Authorization = "Bearer $token" }
Write-Host "    登录成功: $($login.data.user.nickname)"

Write-Host '==> 拉取已发布文献'
$list = Invoke-RestMethod -Uri "$BaseUrl/literatures?status=PUBLISHED&page=1&size=$Limit" -Headers $headers
if ($list.code -ne 0) { throw "文献列表获取失败: $($list.message)" }
$targets = @($list.data.items | Where-Object { -not $_.fileId })
Write-Host "    已发布 $($list.data.total) 篇，其中未绑定原文 $($targets.Count) 篇"

$bound = 0
foreach ($item in $targets) {
    $fileName = "literature-$($item.id).txt"
    $filePath = Join-Path $tempDir $fileName
    $abstract = if ($item.abstractText) { $item.abstractText } else { '（本文献暂无摘要）' }
    $content = @"
$($item.title)
作者：$($item.author)
来源：$($item.source)（$($item.publishYear)）
关键词：$($item.keywords)

摘要
$abstract

正文（演示文本）
本文围绕上述主题展开讨论，说明研究背景、方法与结论。
本文件由演示数据脚本生成，用于验证文献下载链路（RustFS 预签名地址）。
"@
    Set-Content -Path $filePath -Value $content -Encoding UTF8

    # 注意：后端对上传文件做内容类型白名单校验，必须显式声明 text/plain（PowerShell -Form 会发 application/octet-stream）
    $uploadRaw = & curl.exe -s -X POST "$BaseUrl/files/upload" -H "Authorization: Bearer $token" -F "file=@$filePath;type=text/plain"
    $upload = $uploadRaw | ConvertFrom-Json
    if ($upload.code -ne 0) {
        Write-Warning "    上传失败（跳过）: $($item.title) -> $($upload.message)"
        continue
    }
    $fileId = $upload.data.id

    $payload = @{
        title        = $item.title
        author       = $item.author
        source       = $item.source
        publishYear  = $item.publishYear
        language     = $item.language
        categoryId   = $item.categoryId
        visibility   = $item.visibility
        abstractText = $item.abstractText
        keywords     = $item.keywords
        fileId       = $fileId
    } | ConvertTo-Json
    $updated = Invoke-RestMethod -Uri "$BaseUrl/literatures/$($item.id)" -Method Put -Headers $headers -ContentType 'application/json' -Body $payload
    if ($updated.code -ne 0) {
        Write-Warning "    绑定失败（跳过）: $($item.title) -> $($updated.message)"
        continue
    }

    & $MysqlPath --default-character-set=utf8mb4 "-u$DbUser" "-p$DbPassword" $DbName -e `
        "update file_metadata set biz_type='LITERATURE', biz_id=$($item.id) where id=$fileId;" 2>&1 | Where-Object { $_ -notmatch 'Warning' } | Out-Null

    $bound++
    Write-Host "    已绑定: $($item.title) -> $fileName"
}

Write-Host "==> 完成，本次绑定 $bound 篇"
& $MysqlPath --default-character-set=utf8mb4 "-u$DbUser" "-p$DbPassword" -N $DbName -e `
    "select concat('文件总数=', count(*)) from file_metadata where deleted=0;" 2>&1 | Where-Object { $_ -notmatch 'Warning' }
