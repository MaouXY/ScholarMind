# ============================================================================
# 生成用于「全文解析端到端验证」的最小示例 PDF（纯 ASCII 文本，无第三方依赖）
# 输出：scripts/sample-hybrid-retrieval.pdf
# 说明：手写 PDF 结构并计算正确的 xref 偏移，确保 PDFBox 等解析器能正常抽取文本
# ============================================================================
param(
    [string]$OutPath = (Join-Path $PSScriptRoot 'sample-hybrid-retrieval.pdf')
)

$ErrorActionPreference = 'Stop'

$textLines = @(
    'Hybrid Retrieval with Reciprocal Rank Fusion for Academic Search',
    'This PDF is generated for end-to-end verification of full-text extraction.',
    'The retriever combines BM25 sparse ranking with dense vector ranking.',
    'Reciprocal Rank Fusion merges both rank lists with score = 1 / (k + rank).',
    'RECIPROCAL RANK FUSION reduces the bias of any single retriever.',
    'Dense retrieval alone is weak on exact match of proper nouns and acronyms.'
)

# 构造内容流（BT/ET 文本块，每行下移 18pt）
$contentBuilder = New-Object System.Text.StringBuilder
[void]$contentBuilder.Append("BT`n/F1 12 Tf`n72 720 Td`n")
for ($i = 0; $i -lt $textLines.Count; $i++) {
    $escaped = $textLines[$i] -replace '\\', '\\\\' -replace '\(', '\(' -replace '\)', '\)'
    if ($i -gt 0) { [void]$contentBuilder.Append("0 -18 Td`n") }
    [void]$contentBuilder.Append("($escaped) Tj`n")
}
[void]$contentBuilder.Append("ET`n")
$content = $contentBuilder.ToString()
$contentBytes = [System.Text.Encoding]::ASCII.GetByteCount($content)

$objects = @(
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>',
    "<< /Length $contentBytes >>`nstream`n$content" + 'endstream'
)

$pdf = New-Object System.Text.StringBuilder
[void]$pdf.Append("%PDF-1.4`n")
$offsets = @()
for ($i = 0; $i -lt $objects.Count; $i++) {
    $offsets += [System.Text.Encoding]::ASCII.GetByteCount($pdf.ToString())
    $number = $i + 1
    [void]$pdf.Append("$number 0 obj`n$($objects[$i])`nendobj`n")
}

$xrefOffset = [System.Text.Encoding]::ASCII.GetByteCount($pdf.ToString())
[void]$pdf.Append("xref`n0 $($objects.Count + 1)`n")
[void]$pdf.Append("0000000000 65535 f `n")
foreach ($offset in $offsets) {
    [void]$pdf.Append(("{0:D10} 00000 n `n" -f $offset))
}
[void]$pdf.Append("trailer`n<< /Size $($objects.Count + 1) /Root 1 0 R >>`nstartxref`n$xrefOffset`n%%EOF`n")

[System.IO.File]::WriteAllText($OutPath, $pdf.ToString(), [System.Text.Encoding]::ASCII)
Write-Host "已生成示例 PDF：$OutPath（$((Get-Item $OutPath).Length) 字节）"
Write-Host "包含关键词：RECIPROCAL RANK FUSION"
