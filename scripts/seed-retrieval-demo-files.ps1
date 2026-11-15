# ============================================================================
# A1 配套：为「检索演示文献」绑定原文文件
# 用途：让 12 篇检索演示文献也能演示「在线阅读 / 下载原文」，
#       且下载到的 .txt 内容与库内分块正文完全一致（不覆盖手工撰写的分块文本）。
# 前置：后端已启动（默认 8992）、MySQL 与 RustFS 可用、已执行 sql/10_seed_retrieval_demo.sql
# 说明：幂等——已绑定 file_id 的文献跳过；重复执行不会重复上传。
# ============================================================================
param(
    [string]$BaseUrl = 'http://127.0.0.1:8992/api/v1',
    [string]$Username = 'admin',
    [string]$Password = '123456',
    [string]$MysqlPath = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
    [string]$DbUser = 'root',
    [string]$DbPassword = '1829002',
    [string]$DbName = 'scholar_mind'
)

$ErrorActionPreference = 'Stop'
$tempDir = Join-Path $env:TEMP 'scholarmind-retrieval-demo'
New-Item -ItemType Directory -Path $tempDir -Force | Out-Null

Write-Host '==> 登录'
$login = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -ContentType 'application/json' `
    -Body (@{ username = $Username; password = $Password } | ConvertTo-Json)
if ($login.code -ne 0) { throw "登录失败: $($login.message)" }
$token = $login.data.token
$headers = @{ Authorization = "Bearer $token" }

Write-Host '==> 拉取未绑定原文的演示文献'
$list = Invoke-RestMethod -Uri "$BaseUrl/literatures?page=1&size=100" -Headers $headers
if ($list.code -ne 0) { throw "文献列表获取失败: $($list.message)" }
$targets = @($list.data.items | Where-Object {
    $_.id -ge '1200000000000000040' -and $_.id -le '1200000000000000051' -and -not $_.fileId
})
Write-Host "    待绑定 $($targets.Count) 篇"

$bound = 0
foreach ($item in $targets) {
    $fileName = "literature-$($item.id).txt"
    $filePath = Join-Path $tempDir $fileName

    # 正文 = 该文献在库内的分块文本按 chunk_index 拼接，保证「下载原文」与「在线阅读」一致
    $chunkSql = "SELECT chunk_text FROM literature_chunk WHERE literature_id=$($item.id) AND deleted=0 AND enabled=1 ORDER BY chunk_index;"
    $chunks = & $MysqlPath --default-character-set=utf8mb4 "-u$DbUser" "-p$DbPassword" -N $DbName -e $chunkSql 2>$null
    $body = ($chunks | Where-Object { $_ -and $_.Trim() }) -join "`r`n`r`n"
    if (-not $body) {
        Write-Warning "    无分块正文（跳过）: $($item.title)"
        continue
    }

    $content = @"
$($item.title)
作者：$($item.author)
来源：$($item.source)（$($item.publishDate)）
关键词：$($item.keywords)

$body
"@
    Set-Content -Path $filePath -Value $content -Encoding UTF8

    # 后端按内容类型白名单校验，必须显式声明 text/plain
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
        literatureType    = $item.literatureType
        publishDate       = $item.publishDate
        onlinePublishDate = $item.onlinePublishDate
        language          = $item.language
        categoryId        = $item.categoryId
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
        "update file_metadata set biz_type='LITERATURE', biz_id=$($item.id) where id=$fileId;" 2>&1 |
        Where-Object { $_ -notmatch 'Warning' } | Out-Null

    $bound++
    Write-Host "    已绑定: $($item.title) -> $fileName（$($body.Length) 字符）"
}

Write-Host "==> 完成，本次绑定 $bound 篇"
& $MysqlPath --default-character-set=utf8mb4 "-u$DbUser" "-p$DbPassword" -N $DbName -e `
    "select concat('演示文献已绑定原文=', count(*)) from literature where deleted=0 and file_id is not null and id between 1200000000000000040 and 1200000000000000051;" 2>&1 |
    Where-Object { $_ -notmatch 'Warning' }
