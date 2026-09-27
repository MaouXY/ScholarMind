<#
.SYNOPSIS
  用 Milvus REST v2 接口把 ScholarMind 的向量集合（含向量本体）导出为 JSONL，无需安装 pymilvus。

.DESCRIPTION
  Milvus 2.4+ 在 19530 端口同时提供 gRPC 与 REST v2 接口，本脚本：
    1. describe 集合，拿到字段定义（导入端据此重建同构集合）；
    2. 分页 query 全量实体（含 vector 字段）；
    3. 写出 JSONL：首行为 __schema 元信息，其后每行一个实体。

  ScholarMind 的向量集合：
    * scholar_mind_knowledge_literature —— 文献正文分块（混合检索的稠密召回），字段
      chunk_id / literature_id / chunk_index / content / vector(2560)
    * scholar_mind_knowledge            —— RAG 知识库分块（若启用过知识库向量），可能不存在

.EXAMPLE
  # 导出全部 scholar_mind* 集合（默认行为）
  pwsh tools/milvus-export.ps1 -All

.EXAMPLE
  # 只导出文献分块集合到指定文件
  pwsh tools/milvus-export.ps1 -Collection scholar_mind_knowledge_literature -Output backup\lit.jsonl
#>
param(
    [string]$MilvusUrl = 'http://127.0.0.1:19530',
    [string]$Collection = 'scholar_mind_knowledge_literature',
    [string]$Output = '',
    [switch]$All,
    [string]$Prefix = 'scholar_mind',
    [string]$OutputDir = '',
    [int]$PageSize = 500
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Invoke-Milvus([string]$path, $payload) {
    $json = if ($payload) { $payload | ConvertTo-Json -Depth 10 -Compress } else { '{}' }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    try {
        return Invoke-RestMethod -Uri "$MilvusUrl$path" -Method Post -ContentType 'application/json' -Body $bytes -TimeoutSec 300
    } catch {
        Write-Host "  [请求失败] POST $path" -ForegroundColor Red
        Write-Host "  请求体: $json" -ForegroundColor Red
        Write-Host "  响应: $($_.ErrorDetails.Message)" -ForegroundColor Red
        throw
    }
}

function Export-OneCollection([string]$name, [string]$outFile) {
    if (-not $outFile) {
        if ($OutputDir) { $outFile = Join-Path $OutputDir "milvus-$name.jsonl" }
        else { $outFile = Join-Path $root "backup\milvus-$name.jsonl" }
    }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outFile) | Out-Null

    Write-Host ("导出集合 {0}（{1}）" -f $name, $MilvusUrl) -ForegroundColor Cyan
    $describe = Invoke-Milvus '/v2/vectordb/collections/describe' @{ collectionName = $name }
    if ($describe.code -ne 0) { throw "describe 失败：$($describe | ConvertTo-Json -Compress)" }
    $fields = @($describe.data.fields)
    $fieldNames = $fields | ForEach-Object { $_.name }
    $vectorField = ($fields | Where-Object { $_.type -eq 'FloatVector' } | Select-Object -First 1)
    # describe 返回的 params 是 [{key,value}] 数组而不是对象；不同版本偶有缺失，需容错
    $dimension = 0
    if ($vectorField) {
        $dimParam = @($vectorField.params | Where-Object { $_.key -eq 'dim' }) | Select-Object -First 1
        if ($dimParam) { $dimension = [int]$dimParam.value }
    }
    Write-Host ("  字段：{0}   向量维度：{1}" -f ($fieldNames -join ', '), $dimension)

    $rows = New-Object System.Collections.Generic.List[object]
    $offset = 0
    while ($true) {
        $response = Invoke-Milvus '/v2/vectordb/entities/query' @{
            collectionName = $name
            filter         = ''
            outputFields   = $fieldNames
            limit          = $PageSize
            offset         = $offset
        }
        if ($response.code -ne 0) { throw "query 失败：$($response | ConvertTo-Json -Compress)" }
        $page = @($response.data | Where-Object { $_ -ne $null })
        if ($page.Count -eq 0) { break }
        foreach ($row in $page) { $rows.Add($row) }
        Write-Host ("  已读取 {0} 条 ..." -f $rows.Count)
        if ($page.Count -lt $PageSize) { break }
        $offset += $PageSize
        if ($offset -ge 16384) {
            Write-Host '  已达 REST query 的 offset 上限 16384；更大数据量请改用 pymilvus 的 query_iterator' -ForegroundColor Yellow
            break
        }
    }

    $header = @{
        __schema = @{
            collectionName = $name
            dimension      = $dimension
            fields         = $fields
            exportedAt     = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
            entityCount    = $rows.Count
            source         = $MilvusUrl
            project        = 'ScholarMind'
        }
    }

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add(($header | ConvertTo-Json -Depth 10 -Compress))
    foreach ($row in $rows) { $lines.Add(($row | ConvertTo-Json -Depth 10 -Compress)) }
    Set-Content -Path $outFile -Value $lines -Encoding UTF8

    Write-Host ("  完成：{0} 条 -> {1}（{2} MB）" -f $rows.Count, $outFile, [math]::Round((Get-Item $outFile).Length / 1MB, 2)) -ForegroundColor Green
    return $outFile
}

if ($All) {
    $list = Invoke-Milvus '/v2/vectordb/collections/list' @{ dbName = 'default' }
    if ($list.code -ne 0) { throw "列举集合失败：$($list | ConvertTo-Json -Compress)" }
    $targets = @($list.data | Where-Object { $_ -like "$Prefix*" })
    if ($targets.Count -eq 0) {
        Write-Host ("未发现以 {0} 开头的集合（该实例现有：{1}）" -f $Prefix, (@($list.data) -join ', ')) -ForegroundColor Yellow
        return
    }
    Write-Host ("发现 {0} 个待导出集合：{1}" -f $targets.Count, ($targets -join ', ')) -ForegroundColor Cyan
    $exported = @()
    foreach ($name in $targets) { $exported += Export-OneCollection -name $name -outFile '' }
    Write-Host ''
    Write-Host '导出汇总：' -ForegroundColor Green
    $exported | ForEach-Object { "  {0}" -f $_ }
} else {
    Export-OneCollection -name $Collection -outFile $Output | Out-Null
}
