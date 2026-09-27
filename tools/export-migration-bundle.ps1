<#
.SYNOPSIS
  一键导出 ScholarMind 的「离线迁移包」：MySQL 全库 dump + Milvus 向量（JSONL/Attu JSON）+ RustFS 桶对象与策略。

.DESCRIPTION
  产出一个可整包拷走的目录（可选打 zip），目标机按包内 README-迁移步骤.md 执行即可恢复：
    backup\migration-bundle-<时间戳>\
      ├─ README-迁移步骤.md          （按本次真实数据自动生成）
      ├─ mc.exe                      （可选，-IncludeMcExe，离线目标机必需）
      ├─ mysql\scholar_mind.sql      （mysqldump：业务数据 + 文件元数据）
      ├─ milvus\milvus-<集合>.jsonl  （含 __schema 头，导入脚本用）
      ├─ milvus\attu-<集合>.json     （同一份数据的 JSON 数组，Attu 图形界面用）
      ├─ milvus\*.ps1                （导出/导入/格式转换脚本）
      ├─ rustfs\s3\<bucket>\...      （对象按 key 原样落盘）
      ├─ rustfs\bucket-policy.json   （桶访问策略，漏了会导致文件 403）
      └─ rustfs-migrate.ps1          （跨实例在线迁移脚本）

.EXAMPLE
  pwsh tools/export-migration-bundle.ps1
  pwsh tools/export-migration-bundle.ps1 -Zip -IncludeMcExe
  pwsh tools/export-migration-bundle.ps1 -SkipMilvus        # 只导 MySQL 与对象存储
#>
param(
    [string]$OutputDir = '',
    [string]$Bucket = 'scholar-mind',
    [string]$MilvusUrl = 'http://127.0.0.1:19530',
    [string]$RustfsEndpoint = 'http://127.0.0.1:9990',
    [string]$RustfsAccessKey = 'rustfsadmin',
    [string]$RustfsSecretKey = 'rustfsadmin',
    [string]$DbHost = '127.0.0.1',
    [int]$DbPort = 3306,
    [string]$DbUser = 'root',
    [string]$DbPassword = '1829002',
    [string]$DbName = 'scholar_mind',
    [string]$MysqlDumpPath = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe',
    [string]$MysqlPath = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
    [string]$McPath = '',
    [ValidateSet('auto', 'mc', 'sigv4')][string]$RustfsMode = 'auto',
    [switch]$SkipMysql,
    [switch]$SkipMilvus,
    [switch]$SkipRustfs,
    [switch]$IncludeMcExe,
    [switch]$Zip
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$stamp = Get-Date -Format 'yyyyMMdd-HHmm'
if (-not $OutputDir) { $OutputDir = Join-Path $root "backup\migration-bundle-$stamp" }
$milvusDir = Join-Path $OutputDir 'milvus'
$mysqlDir = Join-Path $OutputDir 'mysql'
$rustfsDir = Join-Path $OutputDir 'rustfs'
New-Item -ItemType Directory -Force -Path $milvusDir, $mysqlDir, $rustfsDir | Out-Null

$facts = [ordered]@{
    generatedAt   = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
    bucket        = $Bucket
    milvus        = @()
    objectCount   = 'n/a'
    objectSize    = 'n/a'
    mysqlFile     = 'n/a'
    mysqlSizeMb   = 'n/a'
    tableCounts   = @{}
}

Write-Host '==================== 导出 ScholarMind 迁移包 ====================' -ForegroundColor Cyan
Write-Host ("输出目录：{0}" -f $OutputDir) -ForegroundColor Cyan

# ---------------------------------------------------------------- ① MySQL
Write-Host ''
Write-Host '[1/5] 导出 MySQL ...' -ForegroundColor Cyan
if ($SkipMysql) {
    Write-Host '      已跳过（-SkipMysql）' -ForegroundColor Yellow
} elseif (-not (Test-Path $MysqlDumpPath)) {
    Write-Host ("      未找到 mysqldump：{0}，跳过（可用 -MysqlDumpPath 指定）" -f $MysqlDumpPath) -ForegroundColor Yellow
} else {
    $sqlFile = Join-Path $mysqlDir "$DbName.sql"
    & $MysqlDumpPath "--host=$DbHost" "--port=$DbPort" "-u$DbUser" "-p$DbPassword" `
        '--default-character-set=utf8mb4' '--single-transaction' '--routines' '--triggers' `
        '--databases' $DbName 2>$null | Set-Content -Path $sqlFile -Encoding UTF8
    if (-not (Test-Path $sqlFile) -or (Get-Item $sqlFile).Length -lt 1024) {
        throw "mysqldump 结果异常（文件过小）：$sqlFile"
    }
    $facts.mysqlFile = "$DbName.sql"
    $facts.mysqlSizeMb = [math]::Round((Get-Item $sqlFile).Length / 1MB, 2)
    Write-Host ("      已导出 {0}（{1} MB）" -f $sqlFile, $facts.mysqlSizeMb) -ForegroundColor Green

    if (Test-Path $MysqlPath) {
        $sql = @"
select 'literature', count(*) from literature where deleted=0
union all select 'literature_chunk', count(*) from literature_chunk where deleted=0
union all select 'user_account', count(*) from user_account where deleted=0
union all select 'file_metadata', count(*) from file_metadata where deleted=0
union all select 'citation_record', count(*) from citation_record where deleted=0
union all select 'ai_task_record', count(*) from ai_task_record where deleted=0;
"@
        & $MysqlPath --default-character-set=utf8mb4 "-u$DbUser" "-p$DbPassword" -N -e "use $DbName; $sql" 2>$null |
            Where-Object { $_ -notmatch 'Warning' } | ForEach-Object {
                $parts = $_ -split "`t"
                if ($parts.Count -ge 2) { $facts.tableCounts[$parts[0]] = [int]$parts[1] }
            }
        Write-Host ("      表计数：{0}" -f (($facts.tableCounts.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join ', '))
    }
}

# ---------------------------------------------------------------- ② Milvus
Write-Host ''
Write-Host '[2/5] 导出 Milvus 向量 ...' -ForegroundColor Cyan
if ($SkipMilvus) {
    Write-Host '      已跳过（-SkipMilvus）' -ForegroundColor Yellow
} else {
    $exportScript = Join-Path $PSScriptRoot 'milvus-export.ps1'
    $attuScript = Join-Path $PSScriptRoot 'milvus-jsonl-to-attu.ps1'
    & $exportScript -All -MilvusUrl $MilvusUrl -OutputDir $milvusDir
    foreach ($jsonl in Get-ChildItem $milvusDir -Filter 'milvus-*.jsonl' -ErrorAction SilentlyContinue) {
        $schema = (Get-Content $jsonl.FullName -TotalCount 1 -Encoding UTF8 | ConvertFrom-Json).__schema
        $facts.milvus += [pscustomobject]@{
            collection = $schema.collectionName
            entities   = $schema.entityCount
            dimension  = $schema.dimension
            file       = $jsonl.Name
            sizeMb     = [math]::Round($jsonl.Length / 1MB, 2)
        }
        if (-not $SkipMilvus -and (Test-Path $attuScript)) {
            & $attuScript -Source $jsonl.FullName | Out-Null
        }
    }
    if ($facts.milvus.Count -eq 0) { Write-Host '      未发现 scholar_mind* 集合（该实例可能还没有写入过向量）' -ForegroundColor Yellow }
}

# ---------------------------------------------------------------- ③ RustFS
Write-Host ''
Write-Host '[3/5] 导出 RustFS 对象 ...' -ForegroundColor Cyan
if ($SkipRustfs) {
    Write-Host '      已跳过（-SkipRustfs）' -ForegroundColor Yellow
} else {
    $exportResult = & (Join-Path $PSScriptRoot 'rustfs-export.ps1') -Endpoint $RustfsEndpoint `
        -AccessKey $RustfsAccessKey -SecretKey $RustfsSecretKey -Bucket $Bucket `
        -OutDir $rustfsDir -Mode $RustfsMode -McPath $McPath
    if ($exportResult) {
        $facts.objectCount = $exportResult.ObjectCount
        $facts.objectSize = ('{0:N1} KB' -f ($exportResult.TotalBytes / 1KB))
        $usedMc = $exportResult.McPath
        Write-Host ("      模式={0}，对象 {1} 个" -f $exportResult.Mode, $exportResult.ObjectCount) -ForegroundColor Green
    }
}

# ---------------------------------------------------------------- ④ 脚本与工具
Write-Host ''
Write-Host '[4/5] 收集脚本与工具 ...' -ForegroundColor Cyan
foreach ($script in 'milvus-export.ps1', 'milvus-import.ps1', 'milvus-jsonl-to-attu.ps1') {
    Copy-Item (Join-Path $PSScriptRoot $script) $milvusDir -Force
}
foreach ($script in 'rustfs-migrate.ps1', 'rustfs-import.ps1', 'rustfs-export.ps1', 's3-common.ps1') {
    Copy-Item (Join-Path $PSScriptRoot $script) $OutputDir -Force
}
if ($IncludeMcExe) {
    if (-not $usedMc) { $usedMc = Resolve-McPath -Preferred $McPath }
    if ($usedMc) {
        Copy-Item $usedMc $OutputDir -Force
        Write-Host ("      已附带 mc.exe（复用已有副本：{0}）" -f $usedMc) -ForegroundColor Green
    } else {
        Write-Host '      未找到 mc.exe，未附带（可用 -McPath 指定，或目标机走 SigV4 模式）' -ForegroundColor Yellow
    }
}
Write-Host '      已复制 milvus 与 rustfs 迁移脚本（含零依赖 SigV4 客户端 s3-common.ps1）'

# ---------------------------------------------------------------- ⑤ README
Write-Host ''
Write-Host '[5/5] 生成目标机操作说明 ...' -ForegroundColor Cyan
$milvusTable = if ($facts.milvus.Count -gt 0) {
    ($facts.milvus | ForEach-Object { "| ``$($_.file)`` | $($_.collection) | $($_.entities) 条 | 维度 $($_.dimension) | $($_.sizeMb) MB |" }) -join "`n"
} else { '| （本次未导出向量集合） | - | - | - | - |' }
$tableCounts = if ($facts.tableCounts.Count -gt 0) {
    ($facts.tableCounts.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join '、'
} else { '（未统计）' }

$readme = @"
# ScholarMind · 离线迁移包（目标机操作说明）

> 本包由 `tools/export-migration-bundle.ps1` 于 **$($facts.generatedAt)** 生成，适合两台机器不通网的场景：
> 用 U 盘 / 网盘把整个目录拷到目标机，按下面 5 步执行即可。若目标机能联网，也可以直接用
> `tools/rustfs-migrate.ps1` 做桶对桶的在线迁移，效果相同。

## 包内容（本次真实数据）

| 目录/文件 | 内容 | 规模 |
| --- | --- | --- |
| ``mysql\$($facts.mysqlFile)`` | MySQL 全库导出（含业务数据与文件元数据） | $($facts.mysqlSizeMb) MB |
| ``milvus\*.jsonl`` | Milvus 向量导出（首行 ``__schema``，供导入脚本用） | 见下表 |
| ``milvus\attu-*.json`` | 同一份数据的 JSON 数组（Attu「导入数据」用） | 见下表 |
| ``milvus\*.ps1`` | 导出 / 导入 / 格式转换脚本 | — |
| ``rustfs\s3\$($facts.bucket)\...`` | RustFS 桶对象（按对象 key 原样落盘） | $($facts.objectCount) 个对象，$($facts.objectSize) |
| ``rustfs\bucket-policy.json`` | 桶访问策略（**漏了会导致文件 403**） | — |
| ``rustfs-import.ps1`` / ``rustfs-export.ps1`` | RustFS 对象导入/导出（mc 与零依赖 SigV4 双模式） | — |
| ``s3-common.ps1`` | 纯 PowerShell SigV4 客户端（列/取/放/删对象、读写桶策略），无外部依赖 | — |
| ``rustfs-migrate.ps1`` | 跨实例在线迁移脚本（mc mirror + 策略 + 校验） | — |
| ``mc.exe`` | S3 客户端（仅在带 ``-IncludeMcExe`` 导出时存在；缺失时用 SigV4 模式即可） | — |

向量集合明细：

| 文件 | 集合 | 实体数 | 维度 | 大小 |
| --- | --- | --- | --- | --- |
$milvusTable

MySQL 关键表计数：$tableCounts

**前置条件**：目标机已装 JDK 21 + Maven + Node.js + MySQL 8 + RustFS + （可选）Milvus；
项目代码可从 GitHub 克隆：``git clone https://github.com/MaouXY/ScholarMind.git``。

---

## 第 1 步：导入 MySQL

``````powershell
mysql --host=127.0.0.1 --port=3306 --user=root --password=<目标库密码> --default-character-set=utf8mb4 < .\mysql\$($facts.mysqlFile)
``````

校验：``SELECT COUNT(*) FROM literature WHERE deleted=0;`` 应为 **$($facts.tableCounts['literature'])**

> 注意：SQL 脚本里含 ``CREATE DATABASE``，库名固定为 ``$DbName``；如目标机库名不同需先改脚本或改 ``application.yaml``。

## 第 2 步：导入 RustFS 对象（务必按对象导入，不要拷磁盘目录）

> 原因：不同机器的 RustFS 卷布局可能不同（多卷纠删码 vs 单卷直存），磁盘格式不通用，只能按对象做逻辑导入。

**方式 A：脚本（推荐，mc 与零依赖两种模式自动选择）**

``````powershell
cd <包目录>
pwsh -NoProfile -File .\rustfs-import.ps1 -SourceDir . -Bucket $($facts.bucket)
# 若本机没有 mc.exe，会自动改用零依赖的 SigV4 模式（纯 PowerShell 实现，无需任何外部程序）
# 也可强制指定：-Mode sigv4  或  -Mode mc -McPath <mc.exe 路径>
pwsh -NoProfile -File .\rustfs-import.ps1 -SourceDir . -Bucket $($facts.bucket) -DryRun   # 可先演练
``````

**方式 B：手工 mc 命令（与方式 A 等价，便于逐条排查）**

``````powershell
cd <包目录>\rustfs

# 配置目标别名（RustFS 默认账号密码，改过请替换）
..\mc.exe alias set dst http://127.0.0.1:9990 rustfsadmin rustfsadmin

# 建桶（已存在会提示 already exists，忽略即可）
..\mc.exe mb dst/$($facts.bucket)

# 灌入对象（--preserve 保留时间戳；s3 目录下的相对路径就是对象 key）
..\mc.exe mirror --preserve .\s3 dst/$($facts.bucket)

# 应用桶访问策略
..\mc.exe anonymous set-json .\bucket-policy.json dst/$($facts.bucket)

# 校验：对象数应为 $($facts.objectCount)，总大小 $($facts.objectSize)
..\mc.exe ls --recursive --summarize dst/$($facts.bucket)
``````

> ``mc.exe`` 只在本包由 ``-IncludeMcExe`` 导出时存在；若缺失且又下载不到（国内网络常见），
> 用方式 A 的 SigV4 模式即可，功能完全等价（列对象 / 取对象 / 放对象 / 读写桶策略）。

## 第 3 步：导入 Milvus 向量（二选一，需 Milvus 已启动）

**方式 A：脚本（推荐，自动建集合 + 索引 + 校验）**

``````powershell
cd <包目录>\milvus
pwsh -NoProfile -File .\milvus-import.ps1 -MilvusUrl http://127.0.0.1:19530 ``
     -Source .\milvus-scholar_mind_knowledge_literature.jsonl ``
     -Collection scholar_mind_knowledge_literature -CreateCollection
# 结束会打印 count(*)，应与上表实体数一致
pwsh -NoProfile -File .\milvus-import.ps1 -Source .\milvus-scholar_mind_knowledge_literature.jsonl -DryRun   # 可先演练
``````

**方式 B：Attu 图形界面**
1. 先建集合（字段必须与源集合一致，少一个都会报 ``some field does not exist``）：
   ``chunk_id`` Int64 主键且**关闭自动 ID**、``literature_id`` Int64、``chunk_index`` Int32、``content`` VarChar、``vector`` FloatVector(维度见上表，本项目为 **2560**)
2. 建索引：AUTOINDEX + **COSINE**
3. 加载集合 → 「导入数据」→ 选 ``attu-*.json``

校验（应为上表实体数）：
``````powershell
curl.exe -s -X POST http://127.0.0.1:19530/v2/vectordb/entities/query -H "Content-Type: application/json" ``
  -d '{\"collectionName\":\"scholar_mind_knowledge_literature\",\"filter\":\"\",\"outputFields\":[\"count(*)\"]}'
``````

## 第 4 步：核对目标机配置（``ScholarMindApi/src/main/resources/application.yaml``）

| 配置 | 期望值 |
| --- | --- |
| ``spring.datasource.url`` | 目标机 MySQL（库 ``$DbName``） |
| ``app.storage.rustfs.endpoint`` | 目标机 RustFS（默认 ``http://127.0.0.1:9990``） |
| ``app.storage.rustfs.bucket`` / ``app.storage.base-path`` | 均为 ``$($facts.bucket)`` |
| ``app.storage.rustfs.access-key`` / ``secret-key`` | 目标机 RustFS 密钥（默认 ``rustfsadmin``） |
| ``app.agent.rag.milvus.uri`` | 目标机 Milvus（默认 ``http://127.0.0.1:19530``） |
| ``app.agent.rag.milvus.collection-name`` | ``scholar_mind_knowledge``（文献集合自动为其后缀 ``_literature``） |
| ``app.agent.rag.milvus.dimension`` | **2560**（与 embedding 模型实际输出一致，填错会报 ``Incorrect dimension for field 'vector'``） |
| ``app.ai.ark.api-key`` / ``app.ai.ark.model-name`` | 可用同一账号；不可用时 AI 走本地兜底，功能不中断 |

## 第 5 步：启动与验收

``````powershell
cd ScholarMindApi; mvn spring-boot:run      # 后端 8992
cd ScholarMindWeb; npm run dev              # 前端 5173
pwsh -NoProfile -File .\scripts\preflight-check.ps1   # 一键体检（详见 doc ...-07-系统验收流程.md）
``````

- [ ] 前端 ``http://localhost:5173`` 可用 ``admin / student01 / teacher01``（密码 ``123456``）登录
- [ ] 文献列表有数据；打开任一文献详情能看到**正文分块**（Milvus 未导入也能看，只是检索退化为纯 BM25）
- [ ] 「下载原文」能取回对象（这一步同时验证第 2 步对象与桶策略）
- [ ] 检索页「混合检索对比」的向量列有结果（验证第 3 步向量导入）
- [ ] 管理端「AI 调用日志」「运营分析」「数据大屏」有数据（验证第 1 步 MySQL 导入）

## 常见问题

| 现象 | 原因/处理 |
| --- | --- |
| ``mc`` 报证书/连接错误 | endpoint 必须用 ``http://``（不是 https），9990 是 RustFS 的 S3 端口（控制台是 9991） |
| 文件 403 | 桶策略没应用 → 重跑 ``anonymous set-json`` |
| 文件 404 | 对象没灌进去（``mc ls --recursive`` 看数量）或桶名/base-path 与配置不一致 |
| 检索没有向量列结果 | Milvus 未导入或未启动；管理端执行一次 ``POST /api/v1/admin/rag/literature-vectors/rebuild`` 可重建 |
| 向量写入报维度错误 | ``app.agent.rag.milvus.dimension`` 与 embedding 模型不一致（本项目为 2560）；写入路径已带维度自愈（自动删表重建） |
| 中文乱码 | 导入 SQL 时必须加 ``--default-character-set=utf8mb4`` |
| 列表为空/图表全 0 | MySQL 没导入或库名不对；也可重跑 ``sql/00~08`` 与 ``scripts/seed-demo-files.ps1`` |
"@

$readmePath = Join-Path $OutputDir 'README-迁移步骤.md'
[System.IO.File]::WriteAllText($readmePath, $readme, (New-Object System.Text.UTF8Encoding($false)))
Write-Host ("      已生成 {0}" -f $readmePath) -ForegroundColor Green

# ---------------------------------------------------------------- 汇总
Write-Host ''
Write-Host '==================== 导出完成 ====================' -ForegroundColor Green
$bundleSize = (Get-ChildItem $OutputDir -Recurse -File | Measure-Object -Property Length -Sum).Sum
Write-Host ("  目录：{0}" -f $OutputDir)
Write-Host ("  大小：{0:N1} MB（{1} 个文件）" -f ($bundleSize / 1MB), (Get-ChildItem $OutputDir -Recurse -File | Measure-Object).Count)
if ($facts.milvus.Count -gt 0) {
    Write-Host ("  Milvus：{0}" -f (($facts.milvus | ForEach-Object { "$($_.collection)=$($_.entities) 条/$($_.sizeMb)MB" }) -join '，'))
}
if ($facts.objectCount -ne 'n/a') { Write-Host ("  RustFS：{0} 个对象，{1}" -f $facts.objectCount, $facts.objectSize) }
if ($facts.mysqlSizeMb -ne 'n/a') { Write-Host ("  MySQL：{0} MB" -f $facts.mysqlSizeMb) }

if ($Zip) {
    $zipPath = "$OutputDir.zip"
    if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
    Compress-Archive -Path (Join-Path $OutputDir '*') -DestinationPath $zipPath -CompressionLevel Optimal
    Write-Host ("  已打包：{0}（{1:N1} MB）" -f $zipPath, ((Get-Item $zipPath).Length / 1MB)) -ForegroundColor Green
}
Write-Host ''
Write-Host '下一步：把该目录拷到目标机，按包内 README-迁移步骤.md 执行 5 步即可。' -ForegroundColor Cyan
