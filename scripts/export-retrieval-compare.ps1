# ============================================================================
# 检索对比数据导出（论文用）
# 用途：批量跑一批探针 Query，调 POST /api/v1/search/retrieval-compare，
#       把「稀疏（BM25）/ 向量（Milvus）/ RRF 融合」三列的名次与一致性落成 CSV，
#       直接用于论文里的算法对比表格与消融分析。
# 前置：后端已启动（默认 8992）、演示数据已就绪、Milvus 在线（离线会退化为纯 BM25，
#       此时 denseAvailable=false，脚本会如实记录并在结尾提示）。
# 用法：
#   pwsh -NoProfile -File scripts\export-retrieval-compare.ps1
#   pwsh -NoProfile -File scripts\export-retrieval-compare.ps1 -QueriesFile .\my-queries.txt -Limit 10
# 说明：
#   - 探针默认内置三类（术语型 / 改写型 / 口语型），对应 sql/10 的三种词面设计；
#   - 输出 UTF-8 BOM 的 CSV，Excel 双击打开中文不乱码；
#   - 只读接口，不改数据。
# ============================================================================
param(
    [string]$BaseUrl = 'http://127.0.0.1:8992/api/v1',
    [string]$Username = 'student01',
    [string]$Password = '123456',
    [int]$Limit = 5,
    [string]$QueriesFile = '',
    [string]$OutputPath = '',
    [string]$CategoryId = ''
)

$ErrorActionPreference = 'Stop'

# ---- 默认探针集：三类词面，分别压测「精确词面」「语义改写」「口语化表达」 ----
$builtinQueries = @(
    @{ label = '术语型';   query = 'BM25 k1 参数对倒排索引检索效果的影响' }
    @{ label = '术语型';   query = 'IDF 文档频率 逆文档频率 权重计算' }
    @{ label = '术语型';   query = 'DOI 查重 学术不端 检测' }
    @{ label = '术语型';   query = 'reciprocal rank fusion 倒数排名融合' }
    @{ label = '术语型';   query = '稠密向量 稀疏向量 混合检索 召回率' }
    @{ label = '改写型';   query = '怎样把几个检索器给出的候选清单合成为一个更可靠的排序' }
    @{ label = '改写型';   query = '怎样合并多路检索结果' }
    @{ label = '改写型';   query = '两路结果量纲不一样怎么比较' }
    @{ label = '改写型';   query = '排序分数没法直接相加怎么办' }
    @{ label = '改写型';   query = '不调参数也能把结果合起来吗' }
    @{ label = '口语型';   query = '新用户没有行为数据怎么推荐' }
    @{ label = '口语型';   query = '刚注册的读者怎么给他推荐' }
    @{ label = '口语型';   query = '研究生检索式怎么写' }
    @{ label = '口语型';   query = '怎么判断一篇论文值不值得读' }
    @{ label = '口语型';   query = '同一篇文章中文和英文都能搜到吗' }
)

if ($QueriesFile) {
    if (-not (Test-Path $QueriesFile)) { throw "探针文件不存在: $QueriesFile" }
    $builtinQueries = Get-Content -LiteralPath $QueriesFile -Encoding UTF8 |
        Where-Object { $_ -and $_.Trim() -and -not $_.TrimStart().StartsWith('#') } |
        ForEach-Object {
            $line = $_.Trim()
            # 支持 "标签|问题" 形式，未写标签时归为「自定义」
            if ($line -match '^(?<label>[^|]+)\|(?<query>.+)$') {
                @{ label = $Matches.label.Trim(); query = $Matches.query.Trim() }
            } else {
                @{ label = '自定义'; query = $line }
            }
        }
}

Write-Host '================ 检索对比数据导出 ================'
Write-Host '==> 登录'
$login = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -ContentType 'application/json' `
    -Body (@{ username = $Username; password = $Password } | ConvertTo-Json) -TimeoutSec 15
if ($login.code -ne 0) { throw "登录失败: $($login.message)" }
$headers = @{ Authorization = "Bearer $($login.data.token)" }
Write-Host "    当前账号: $($login.data.user.nickname) / $($login.data.user.role)"

$rows = New-Object System.Collections.Generic.List[object]
$index = 0
$diffCount = 0
$denseMissing = 0

foreach ($probe in $builtinQueries) {
    $index++
    $payload = @{ query = $probe.query; limit = $Limit }
    if ($CategoryId) { $payload.categoryId = $CategoryId }
    $body = $payload | ConvertTo-Json

    try {
        $response = Invoke-RestMethod -Uri "$BaseUrl/search/retrieval-compare" -Method Post `
            -Headers $headers -ContentType 'application/json' -Body $body -TimeoutSec 120
    } catch {
        Write-Warning "    [$index] 请求失败（跳过）: $($probe.query) -> $($_.Exception.Message)"
        continue
    }
    if ($response.code -ne 0) {
        Write-Warning "    [$index] 接口返回 code=$($response.code)（跳过）: $($probe.query) -> $($response.message)"
        continue
    }

    $data = $response.data
    $sparse = @($data.sparse)
    $dense = @($data.dense)
    $hybrid = @($data.hybrid)

    $sparseTop1 = if ($sparse.Count -gt 0) { $sparse[0] } else { $null }
    $denseTop1 = if ($dense.Count -gt 0) { $dense[0] } else { $null }
    $hybridTop1 = if ($hybrid.Count -gt 0) { $hybrid[0] } else { $null }

    # 三列是否指向同一篇文献（DIFF 就是论文里「两路互补」的证据）
    $sparseTitle = if ($sparseTop1) { $sparseTop1.literatureTitle } else { '' }
    $denseTitle = if ($denseTop1) { $denseTop1.literatureTitle } else { '' }
    $hybridTitle = if ($hybridTop1) { $hybridTop1.literatureTitle } else { '' }
    $sameTop1 = ($sparseTitle -ne '' -and $sparseTitle -eq $denseTitle)
    if (-not $sameTop1) { $diffCount++ }
    if (-not $data.denseAvailable) { $denseMissing++ }

    # RRF 是否纠回了「稀疏与向量分歧」——hybrid top1 落在哪一列的第一名上
    $hybridSource = '其他'
    if ($hybridTitle -ne '' -and $hybridTitle -eq $sparseTitle) { $hybridSource = '稀疏' }
    elseif ($hybridTitle -ne '' -and $hybridTitle -eq $denseTitle) { $hybridSource = '向量' }

    $rows.Add([pscustomobject]@{
        序号          = $index
        类型          = $probe.label
        查询          = $probe.query
        语料条数      = $data.limit
        稀疏候选数    = $data.sparseCandidateCount
        向量候选数    = $data.denseCandidateCount
        向量可用      = $data.denseAvailable
        稀疏Top1      = $sparseTitle
        向量Top1      = $denseTitle
        融合Top1      = $hybridTitle
        稀疏向量一致  = $sameTop1
        融合跟随      = $hybridSource
        稀疏Top1分数  = if ($sparseTop1) { [math]::Round($sparseTop1.score, 4) } else { $null }
        向量Top1分数  = if ($denseTop1) { [math]::Round($denseTop1.score, 4) } else { $null }
        融合Top1RRF分 = if ($hybridTop1 -and $null -ne $hybridTop1.rrfScore) { [math]::Round($hybridTop1.rrfScore, 6) } else { $null }
    })

    $mark = if ($sameTop1) { '一致' } else { '分歧 -> 融合跟随' + $hybridSource }
    Write-Host ("    [{0}] {1} | 稀疏{2} 向量{3} | {4}" -f $index, $probe.label, $data.sparseCandidateCount, $data.denseCandidateCount, $mark)
}

if ($rows.Count -eq 0) { throw '没有任何探针成功返回，无法导出。请先检查后端与登录链路。' }

if (-not $OutputPath) {
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $OutputPath = Join-Path (Join-Path (Split-Path -Parent $PSScriptRoot) 'doc\数据') "retrieval-compare-$stamp.csv"
}
$outputDir = Split-Path -Parent $OutputPath
if ($outputDir -and -not (Test-Path $outputDir)) { New-Item -ItemType Directory -Path $outputDir -Force | Out-Null }

# utf8BOM：Excel 双击打开中文不乱码；用 noheader 之外的默认列名即上表的属性名
$rows | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding utf8BOM

Write-Host ''
Write-Host "==> 已导出 $($rows.Count) 条探针结果"
Write-Host "    文件: $OutputPath"
Write-Host ("    两路 Top1 分歧: {0} 条（论文里可作为「两路互补」的证据）" -f $diffCount)
if ($denseMissing -gt 0) {
    Write-Warning "    有 $denseMissing 条 denseAvailable=false：Milvus 未启用或向量召回失败，这批数据只能当纯 BM25 结果用"
}
Write-Host '提示：正文片段较长，CSV 只落名次与分数；需要引用原文片段请用 Knife4j 单条调 /search/retrieval-compare 查看。'
