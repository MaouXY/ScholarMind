# ============================================================================
# 端到端验证：文献全文解析（PDF → 正文分块）
# 流程：教师上传 PDF → 新建公开文献并绑定（自动进入待审核）→ 管理员审核通过 →
#       重建索引（RustFS 取回对象 + PDF 文本抽取）→ 读取分块，校验正文来自 PDF 而非题录兜底
# 前置：后端已启动、RustFS 在线、演示账号可用（teacher01 / admin，密码 123456）
# 说明：管理员不能作为文献上传者（LiteratureService.create 会拒绝 ADMIN），因此分两个会话
# ============================================================================
param(
    [string]$BaseUrl = 'http://127.0.0.1:8992/api/v1',
    [string]$TeacherUsername = 'teacher01',
    [string]$AdminUsername = 'admin',
    [string]$Password = '123456',
    [string]$PdfPath = '',
    [string]$ExpectedKeyword = 'RECIPROCAL RANK FUSION',
    [long]$CategoryId = 1100000000000000002
)

$ErrorActionPreference = 'Stop'

if (-not $PdfPath) { $PdfPath = Join-Path $PSScriptRoot 'sample-hybrid-retrieval.pdf' }
if (-not (Test-Path $PdfPath)) { throw "示例 PDF 不存在：$PdfPath（请先运行 scripts/make-sample-pdf.ps1）" }

function Login($username) {
    $response = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -ContentType 'application/json' `
        -Body (@{ username = $username; password = $Password; role = 'USER' } | ConvertTo-Json)
    if ($response.code -ne 0) { throw "登录失败($username): $($response.message)" }
    return $response.data
}

Write-Host '==> 教师登录（文献上传者）'
$teacher = Login $TeacherUsername
$teacherToken = $teacher.token
$teacherHeaders = @{ Authorization = "Bearer $teacherToken" }
Write-Host "    $($teacher.user.nickname) / $($teacher.user.role)"

Write-Host "==> 上传 PDF：$([IO.Path]::GetFileName($PdfPath))"
$uploadRaw = & curl.exe -s -X POST "$BaseUrl/files/upload" -H "Authorization: Bearer $teacherToken" -F "file=@$PdfPath;type=application/pdf"
$upload = $uploadRaw | ConvertFrom-Json
if ($upload.code -ne 0) { throw "上传失败: $($upload.message)" }
$fileId = $upload.data.id
Write-Host "    fileId=$fileId contentType=$($upload.data.contentType) size=$($upload.data.sizeBytes)"

Write-Host '==> 教师新建公开文献并绑定该 PDF（公开文献自动进入待审核）'
$payload = @{
    title        = '端到端验证：PDF 正文解析与 RRF 融合（含专有名词）'
    author       = 'E2E Verifier'
    source       = 'E2E Pipeline'
    publishYear  = 2026
    language     = 'en-US'
    categoryId   = $CategoryId
    visibility   = 'PUBLIC'
    abstractText = '本摘要刻意与 PDF 正文用词不同，用于验证索引语料确实来自 PDF 解析结果而非题录兜底。'
    keywords     = 'E2E,PDF解析,RRF'
    fileId       = $fileId
} | ConvertTo-Json
$created = Invoke-RestMethod -Uri "$BaseUrl/literatures" -Method Post -Headers $teacherHeaders -ContentType 'application/json' -Body $payload
if ($created.code -ne 0) { throw "新建文献失败: $($created.message)" }
$literatureId = $created.data.id
Write-Host "    literatureId=$literatureId status=$($created.data.status) fileId=$($created.data.fileId)"

Write-Host '==> 管理员登录并审核通过'
$admin = Login $AdminUsername
$adminHeaders = @{ Authorization = "Bearer $($admin.token)" }
$approved = Invoke-RestMethod -Uri "$BaseUrl/admin/literatures/$literatureId/approve" -Method Post -Headers $adminHeaders
if ($approved.code -ne 0) { throw "审核失败: $($approved.message)" }
Write-Host "    status=$($approved.data.status) reviewedAt=$($approved.data.reviewedAt)"

Write-Host '==> 重建索引（触发 RustFS 取回 + PDF 文本抽取）'
$index = Invoke-RestMethod -Uri "$BaseUrl/ai/literature/$literatureId/index" -Method Post -Headers $teacherHeaders -TimeoutSec 300
if ($index.code -ne 0) { throw "重建索引失败: $($index.message)" }
Write-Host "    chunkCount=$($index.data.chunkCount) parseStatus=$($index.data.parseStatus)"

Write-Host '==> 读取分块正文并校验'
$chunks = Invoke-RestMethod -Uri "$BaseUrl/literatures/$literatureId/chunks" -Headers $teacherHeaders
if ($chunks.code -ne 0) { throw "读取分块失败: $($chunks.message)" }
$allText = ($chunks.data | ForEach-Object { $_.content }) -join "`n"
$hit = $allText -match [regex]::Escape($ExpectedKeyword)
$detail = Invoke-RestMethod -Uri "$BaseUrl/literatures/$literatureId" -Headers $teacherHeaders
$parseError = if ($null -eq $detail.data.parseError) { '<null>（未回退，语料来自 PDF）' } else { $detail.data.parseError }

Write-Host "    分块数=$($chunks.data.Count) 正文长度=$($allText.Length)"
Write-Host "    期望关键词 '$ExpectedKeyword' 命中: $hit"
Write-Host "    parse_error=$parseError"
$excerpt = ($allText -replace '\s+', ' ')
if ($excerpt.Length -gt 150) { $excerpt = $excerpt.Substring(0, 150) + '…' }
Write-Host "    正文节选: $excerpt"

Write-Host '=== 后端日志（解析分支）==='
Select-String -Path (Join-Path (Split-Path $PSScriptRoot -Parent) 'ScholarMindApi\target\e2e.log') `
    -Pattern 'literature index content resolved|literature text extracted' -ErrorAction SilentlyContinue |
    Select-Object -Last 4 | ForEach-Object { "    " + ($_.Line.Trim() -replace '\s+', ' ') }

if (-not $hit) {
    throw "❌ 未在分块正文中找到 PDF 中的关键词，可能走了题录兜底（检查 parse_error 与日志 scenario=literature-text-extract）"
}
Write-Host "✅ 通过：分块正文来自 PDF 解析结果（literatureId=$literatureId）"
