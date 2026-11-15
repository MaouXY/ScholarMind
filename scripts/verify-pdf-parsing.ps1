# ============================================================================
# 端到端验证：文献全文解析（PDF → 正文分块）
# 流程：管理员登录 → 上传 PDF → 新建文献并绑定原文（上传即发布）→
#       后端自动重建索引（RustFS 取回对象 + PDF 文本抽取）并生成 AI 摘要 →
#       读取分块校验正文来自 PDF → 调用管理端删除接口清理本次验证数据（-KeepData 可保留）
# 前置：后端已启动、RustFS 在线、演示账号可用（admin，密码 123456）
# 说明：客户 2026-11-15 口径调整后，文献只能由管理员上传，且上传即发布、自动建索引与摘要，
#       不再有「学生上传 → 管理员审核」两段式流程。
# ============================================================================
param(
    [string]$BaseUrl = 'http://127.0.0.1:8992/api/v1',
    [string]$AdminUsername = 'admin',
    [string]$Password = '123456',
    [string]$PdfPath = '',
    [string]$ExpectedKeyword = 'RECIPROCAL RANK FUSION',
    [long]$CategoryId = 1100000000000000002,
    [string]$LogPath = '',
    [switch]$KeepData
)

$ErrorActionPreference = 'Stop'

if (-not $PdfPath) { $PdfPath = Join-Path $PSScriptRoot 'sample-hybrid-retrieval.pdf' }
if (-not (Test-Path $PdfPath)) { throw "示例 PDF 不存在：$PdfPath（请先运行 scripts/make-sample-pdf.ps1）" }

function Login($username) {
    $response = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -ContentType 'application/json' `
        -Body (@{ username = $username; password = $Password } | ConvertTo-Json)
    if ($response.code -ne 0) { throw "登录失败($username): $($response.message)" }
    return $response.data
}

Write-Host '==> 管理员登录'
$admin = Login $AdminUsername
if ($admin.user.role -ne 'ADMIN') { throw "该脚本需要用管理员账号执行（当前角色 $($admin.user.role)）" }
$adminToken = $admin.token
$adminHeaders = @{ Authorization = "Bearer $adminToken" }
Write-Host "    $($admin.user.nickname) / $($admin.user.role)"

Write-Host "==> 上传 PDF：$([IO.Path]::GetFileName($PdfPath))"
$uploadRaw = & curl.exe -s -X POST "$BaseUrl/files/upload" -H "Authorization: Bearer $adminToken" -F "file=@$PdfPath;type=application/pdf"
$upload = $uploadRaw | ConvertFrom-Json
if ($upload.code -ne 0) { throw "上传失败: $($upload.message)" }
$fileId = $upload.data.id
Write-Host "    fileId=$fileId contentType=$($upload.data.contentType) size=$($upload.data.sizeBytes)"

Write-Host '==> 新建文献并绑定该 PDF（上传即发布，同步建索引与摘要）'
$payload = @{
    title             = '端到端验证：PDF 正文解析与 RRF 融合（含专有名词）'
    author            = 'E2E Verifier'
    source            = 'E2E Pipeline'
    literatureType    = '会议论文'
    publishDate       = '2026-01-01'
    onlinePublishDate = '2026-02-01'
    language          = 'en-US'
    categoryId        = $CategoryId
    abstractText      = '本摘要刻意与 PDF 正文用词不同，用于验证索引语料确实来自 PDF 解析结果而非题录兜底。'
    keywords          = 'E2E,PDF解析,RRF'
    fileId            = $fileId
} | ConvertTo-Json
$created = Invoke-RestMethod -Uri "$BaseUrl/literatures" -Method Post -Headers $adminHeaders -ContentType 'application/json' -Body $payload -TimeoutSec 300
if ($created.code -ne 0) { throw "新建文献失败: $($created.message)" }
$literatureId = $created.data.id
Write-Host "    literatureId=$literatureId uploaderId=$($created.data.uploaderId) fileId=$($created.data.fileId)"
Write-Host "    literatureType=$($created.data.literatureType) publishDate=$($created.data.publishDate) onlinePublishDate=$($created.data.onlinePublishDate)"
if ($created.data.uploaderId -ne $admin.user.id) { throw "上传者应记为当前管理员，实际为 $($created.data.uploaderId)" }

Write-Host '==> 读取分块正文并校验（索引由上传事件自动重建）'
$chunks = Invoke-RestMethod -Uri "$BaseUrl/literatures/$literatureId/chunks" -Headers $adminHeaders
if ($chunks.code -ne 0) { throw "读取分块失败: $($chunks.message)" }
$allText = ($chunks.data | ForEach-Object { $_.content }) -join "`n"
$hit = $allText -match [regex]::Escape($ExpectedKeyword)
$detail = Invoke-RestMethod -Uri "$BaseUrl/literatures/$literatureId" -Headers $adminHeaders
$parseError = if ($null -eq $detail.data.parseError) { '<null>（未回退，语料来自 PDF）' } else { $detail.data.parseError }
$summaryState = if ($detail.data.aiSummary) { "已生成（$($detail.data.aiSummary.Length) 字符）" } else { '未生成' }

Write-Host "    分块数=$($chunks.data.Count) 正文长度=$($allText.Length)"
Write-Host "    期望关键词 '$ExpectedKeyword' 命中: $hit"
Write-Host "    parse_error=$parseError"
Write-Host "    ai_summary=$summaryState"
$excerpt = ($allText -replace '\s+', ' ')
if ($excerpt.Length -gt 150) { $excerpt = $excerpt.Substring(0, 150) + '…' }
Write-Host "    正文节选: $excerpt"

if ($LogPath -and (Test-Path $LogPath)) {
    Write-Host "=== 后端日志（$LogPath，解析分支）==="
    Select-String -Path $LogPath -Pattern 'literature index content resolved|literature text extracted|literature uploaded' |
        Select-Object -Last 5 | ForEach-Object { "    " + ($_.Line.Trim() -replace '\s+', ' ') }
} else {
    Write-Host '=== 后端日志（未指定 -LogPath 或文件不存在，跳过；可传 -LogPath 指向启动日志）==='
}

if (-not $hit) {
    throw "❌ 未在分块正文中找到 PDF 中的关键词，可能走了题录兜底（检查 parse_error 与日志 scenario=literature-text-extract）"
}

if ($KeepData) {
    Write-Host "✅ 通过：分块正文来自 PDF 解析结果（literatureId=$literatureId，已保留）"
    return
}

Write-Host '==> 清理：管理端级联删除本次验证数据'
$deleted = Invoke-RestMethod -Uri "$BaseUrl/admin/literatures/$literatureId" -Method Delete -Headers $adminHeaders
if ($deleted.code -ne 0) { throw "清理失败: $($deleted.message)" }
$gone = Invoke-RestMethod -Uri "$BaseUrl/literatures/$literatureId" -Headers $adminHeaders
Write-Host "    删除后查询返回 code=$($gone.code)（14101 表示已不存在）"
Write-Host "✅ 通过：分块正文来自 PDF 解析结果，验证数据已清理（literatureId=$literatureId）"
