# ============================================================================
# ScholarMind 验收前体检（Preflight Check）
# 用途：演示/答辩前一条命令确认「服务是否就绪、演示数据是否够用、关键链路是否通」
# 用法：pwsh -NoProfile -File scripts\preflight-check.ps1
# 退出码：0 = 关键项全部通过；1 = 存在关键项失败（详见输出）
# ============================================================================
param(
    [string]$BaseUrl = 'http://127.0.0.1:8992/api/v1',
    [string]$FrontendUrl = 'http://127.0.0.1:5173',
    [string]$MysqlPath = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
    [string]$DbUser = 'root',
    [string]$DbPassword = '1829002',
    [string]$DbName = 'scholar_mind',
    [string]$StudentUsername = 'student01',
    [string]$AdminUsername = 'admin',
    [string]$Password = '123456',
    [int]$DownloadProbeCount = 3
)

$ErrorActionPreference = 'Continue'
$script:criticalFailed = 0
$script:warnings = 0

function Report($level, $name, $detail) {
    switch ($level) {
        'ok'   { Write-Host ("  [OK]   {0,-28} {1}" -f $name, $detail) }
        'warn' { Write-Host ("  [WARN] {0,-28} {1}" -f $name, $detail); $script:warnings++ }
        'fail' { Write-Host ("  [FAIL] {0,-28} {1}" -f $name, $detail); $script:criticalFailed++ }
    }
}

function Test-Port($port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try { $client.Connect('127.0.0.1', $port); $client.Close(); return $true } catch { return $false } finally { $client.Dispose() }
}

Write-Host '================ ScholarMind 验收前体检 ================'

Write-Host '--- 1. 依赖服务 ---'
if (Test-Port 3306) { Report ok 'MySQL 3306' '在线（关键依赖）' } else { Report fail 'MySQL 3306' '未启动：后端将无法工作' }
if (Test-Port 9990) { Report ok 'RustFS 9990' '在线（文件上传/下载可用）' } else { Report warn 'RustFS 9990' '未启动：上传/下载将不可用（其余功能正常）' }
if (Test-Port 19530) { Report ok 'Milvus 19530' '在线（混合检索向量路可用）' } else { Report warn 'Milvus 19530' '未启动：检索将退化为纯 BM25（功能不中断）' }

Write-Host '--- 2. 应用与前端 ---'
$backendUp = $false
$healthUrl = ($BaseUrl -replace '/api/v1/?$', '') + '/actuator/health'
try {
    $health = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 8
    if ($health.status -eq 'UP') { Report ok '后端 8992' '健康检查 UP'; $backendUp = $true } else { Report fail '后端 8992' "健康检查返回 $($health.status)" }
} catch { Report fail '后端 8992' "无法访问 $healthUrl：$($_.Exception.Message)" }

try {
    $front = Invoke-WebRequest -Uri $FrontendUrl -TimeoutSec 8 -SkipHttpErrorCheck
    if ($front.StatusCode -eq 200) { Report ok '前端 5173' 'HTTP 200' } else { Report fail '前端 5173' "HTTP $($front.StatusCode)" }
} catch { Report fail '前端 5173' "无法访问：$($_.Exception.Message)" }

if ($backendUp) {
    try {
        $login = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -ContentType 'application/json' `
            -Body (@{ username = $StudentUsername; password = $Password; role = 'STUDENT' } | ConvertTo-Json) -TimeoutSec 10
        if ($login.code -eq 0) { Report ok '登录与代理链路' "$($login.data.user.nickname) / $($login.data.user.role)" }
        else { Report fail '登录与代理链路' "code=$($login.code) $($login.message)" }
    } catch { Report fail '登录与代理链路' $_.Exception.Message }
}

Write-Host '--- 3. 演示数据规模 ---'
$counts = @{}
if (Test-Path $MysqlPath) {
    $sql = @"
select 'literatures', count(*) from literature where deleted=0
union all select 'published', count(*) from literature where status='PUBLISHED' and deleted=0
union all select 'pending_review', count(*) from literature where status='PENDING_REVIEW' and deleted=0
union all select 'chunks', count(*) from literature_chunk where deleted=0
union all select 'users', count(*) from user_account where deleted=0
union all select 'favorites', count(*) from literature_favorite where deleted=0
union all select 'reading_history', count(*) from reading_history where deleted=0
union all select 'notes', count(*) from reading_note where deleted=0
union all select 'citations', count(*) from citation_record where deleted=0
union all select 'files', count(*) from file_metadata where deleted=0
union all select 'rag_docs', count(*) from rag_knowledge_document where deleted=0
union all select 'ai_tasks', count(*) from ai_task_record where deleted=0
union all select 'search_logs', count(*) from literature_search_log where deleted=0;
"@
    & $MysqlPath --default-character-set=utf8mb4 "-u$DbUser" "-p$DbPassword" -N -e "use $DbName; $sql" 2>$null |
        Where-Object { $_ -notmatch 'Warning' } | ForEach-Object {
            $parts = $_ -split "`t"
            if ($parts.Count -ge 2) { $counts[$parts[0]] = [int]$parts[1] }
        }
    $expectations = @(
        @{ key = 'published';      label = '已发布文献';   min = 5 },
        @{ key = 'pending_review'; label = '待审核文献';   min = 1 },
        @{ key = 'chunks';         label = '正文分块';     min = 10 },
        @{ key = 'users';          label = '用户账号';     min = 5 },
        @{ key = 'favorites';      label = '收藏记录';     min = 1 },
        @{ key = 'reading_history';label = '阅读历史';     min = 1 },
        @{ key = 'notes';          label = '阅读笔记';     min = 1 },
        @{ key = 'citations';      label = '引用收藏';     min = 1 },
        @{ key = 'rag_docs';       label = 'RAG 知识文档'; min = 1 },
        @{ key = 'ai_tasks';       label = 'AI 调用日志';  min = 1 },
        @{ key = 'search_logs';    label = '检索行为日志'; min = 1 }
    )
    foreach ($e in $expectations) {
        $value = if ($counts.ContainsKey($e.key)) { $counts[$e.key] } else { 0 }
        if ($value -ge $e.min) { Report ok $e.label ("{0} 条" -f $value) }
        else { Report warn $e.label ("{0} 条（建议 ≥ {1}，可执行 sql/08_seed_demo_scale.sql 或 scripts/seed-demo-files.ps1）" -f $value, $e.min) }
    }
} else { Report warn '数据库统计' "未找到 mysql 客户端：$MysqlPath（可加 -MysqlPath 指定）" }

Write-Host '--- 4. 关键链路抽检 ---'
if ($backendUp) {
    $token = (Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -ContentType 'application/json' `
            -Body (@{ username = $StudentUsername; password = $Password; role = 'STUDENT' } | ConvertTo-Json)).data.token
    $headers = @{ Authorization = "Bearer $token" }

    # 4.1 下载链路
    try {
        $published = Invoke-RestMethod -Uri "$BaseUrl/literatures?status=PUBLISHED&page=1&size=12" -Headers $headers
        $bound = @($published.data.items | Where-Object { $_.fileId })
        if ($bound.Count -eq 0) {
            Report warn '下载链路' '没有已绑定原文文件的文献（执行 scripts/seed-demo-files.ps1）'
        } else {
            $probe = $bound | Select-Object -First $DownloadProbeCount
            $okCount = 0
            $tempFile = Join-Path $env:TEMP 'preflight-download.bin'
            foreach ($item in $probe) {
                $ticket = Invoke-RestMethod -Uri "$BaseUrl/literatures/$($item.id)/download" -Headers $headers
                $httpCode = & curl.exe -s -o $tempFile -w '%{http_code}' $ticket.data.url
                if ($httpCode -eq '200') { $okCount++ }
            }
            Remove-Item $tempFile -Force -ErrorAction SilentlyContinue
            if ($okCount -eq $probe.Count) { Report ok '下载链路（预签名）' "抽检 $okCount/$($probe.Count) 篇成功取回对象" }
            else { Report warn '下载链路（预签名）' "仅 $okCount/$($probe.Count) 成功（RustFS 离线或对象缺失）" }
        }
    } catch { Report warn '下载链路（预签名）' $_.Exception.Message }

    # 4.2 混合检索向量路
    try {
        $compare = Invoke-RestMethod -Uri "$BaseUrl/search/retrieval-compare" -Method Post -Headers $headers -ContentType 'application/json' `
            -Body (@{ query = '混合检索 RRF'; limit = 3 } | ConvertTo-Json) -TimeoutSec 120
        if ($compare.data.denseAvailable -and $compare.data.denseCandidateCount -gt 0) {
            Report ok '混合检索向量路' ("denseAvailable=true，稠密候选 {0}（稀疏候选 {1}）" -f $compare.data.denseCandidateCount, $compare.data.sparseCandidateCount)
        } else {
            Report warn '混合检索向量路' 'denseAvailable=false：Milvus 离线或分块未写入向量（管理端执行 /admin/rag/literature-vectors/rebuild）'
        }
    } catch { Report warn '混合检索向量路' $_.Exception.Message }

    # 4.3 AI 能力（不主动消耗额度，只看历史记录）
    try {
        $adminToken = (Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -ContentType 'application/json' `
                -Body (@{ username = $AdminUsername; password = $Password; role = 'ADMIN' } | ConvertTo-Json)).data.token
        $summary = Invoke-RestMethod -Uri "$BaseUrl/admin/ai-tasks/summary" -Headers @{ Authorization = "Bearer $adminToken" }
        if ($summary.data.total -gt 0) {
            Report ok 'AI 任务健康度' ("总数 {0}，成功 {1}，失败 {2}，成功率 {3}%，平均 {4}ms" -f $summary.data.total, $summary.data.success, $summary.data.failed, $summary.data.successRate, $summary.data.avgDurationMs)
        } else { Report warn 'AI 任务健康度' '暂无 AI 调用记录（可先生成一次文献摘要验证 AI Key）' }
        $dash = Invoke-RestMethod -Uri "$BaseUrl/admin/statistics/dashboard?days=14&limit=3" -Headers @{ Authorization = "Bearer $adminToken" }
        Report ok '统计看板' ("用户 {0}、文献 {1}、待审核 {2}（数值字段为数字类型）" -f $dash.data.overview.userTotal, $dash.data.overview.literatureTotal, $dash.data.overview.pendingReviewTotal)
    } catch { Report warn 'AI 任务健康度' $_.Exception.Message }
}

Write-Host '--- 5. 结论 ---'
if ($script:criticalFailed -eq 0) {
    Write-Host ("  ✅ 关键项全部通过（警告 {0} 项）：http://localhost:5173 可用 admin / student01 / teacher01 登录（密码 123456）" -f $script:warnings)
    exit 0
} else {
    Write-Host ("  ❌ 有 {0} 项关键检查失败，请先按上方提示处理；警告 {1} 项。" -f $script:criticalFailed, $script:warnings)
    exit 1
}
