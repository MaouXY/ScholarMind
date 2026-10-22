# ScholarMind 工具集（`tools/`）

环境迁移与数据导出工具，覆盖 **Milvus 向量库** 与 **RustFS 对象存储**（外加 MySQL 全库），
既支持「导出成离线迁移包拷到另一台机器」，也支持「两台机器在线对拷」。

**零外部依赖设计**：除可选复用的 `mc.exe` 外，所有功能都用纯 PowerShell 实现（SigV4 签名见 `s3-common.ps1`），
国内网络下载不到 `mc` 也能完整跑通。

## 一、一键导出离线迁移包

```powershell
cd F:\javaWeb\毕设接单\ScholarMind
pwsh -NoProfile -File tools\export-migration-bundle.ps1 -IncludeMcExe -Zip
```

产出 `backup/migration-bundle-<时间戳>/`（`backup/` 已在 `.gitignore` 中，不会入库）：

| 内容 | 说明 |
| --- | --- |
| `README-迁移步骤.md` | **按本次真实数据自动生成**的目标机操作说明（5 步 + 验收清单 + 排错表） |
| `mysql/scholar_mind.sql` | MySQL 全库 dump（业务数据 + `file_metadata` 文件元数据） |
| `milvus/milvus-<集合>.jsonl` | 向量导出（首行 `__schema` 含字段定义与维度，导入脚本据此重建集合） |
| `milvus/attu-<集合>.json` | 同一份数据的 JSON 数组（Attu「导入数据」用，UTF-8 无 BOM） |
| `milvus/*.ps1` | 导出 / 导入 / 格式转换脚本 |
| `rustfs/s3/<bucket>/...` | 桶内对象（**相对路径即对象 key**） |
| `rustfs/bucket-policy.json` | 桶访问策略（漏了会导致文件 403） |
| `rustfs-import.ps1` / `rustfs-export.ps1` | 对象导入/导出（mc 与零依赖 SigV4 双模式） |
| `s3-common.ps1` | 纯 PowerShell SigV4 客户端（列/取/放/删对象、读写桶策略） |
| `rustfs-migrate.ps1` | 跨实例在线迁移（mc mirror + 策略 + 校验） |
| `mc.exe` | 可选，`-IncludeMcExe` 时附带（本机若无 mc，会自动复用其他项目里已有的副本） |

常用变体：

```powershell
pwsh tools\export-migration-bundle.ps1                         # 只出目录，不打 zip
pwsh tools\export-migration-bundle.ps1 -SkipMilvus             # 只导 MySQL 与对象存储
pwsh tools\export-migration-bundle.ps1 -RustfsMode sigv4       # 强制零依赖模式
pwsh tools\export-migration-bundle.ps1 -OutputDir D:\bundle    # 指定输出目录
```

## 二、目标机恢复（对应包内 README 的第 1~3 步）

```powershell
# 1) MySQL
mysql --host=127.0.0.1 --port=3306 --user=root --password=<密码> --default-character-set=utf8mb4 < .\mysql\scholar_mind.sql

# 2) RustFS 对象（自动选 mc 或零依赖 SigV4；桶名可与源不同）
pwsh -NoProfile -File .\rustfs-import.ps1 -SourceDir . -Bucket scholar-mind

# 3) Milvus 向量（自动建集合 + 索引 + 校验）
cd milvus
pwsh -NoProfile -File .\milvus-import.ps1 -MilvusUrl http://127.0.0.1:19530 `
     -Source .\milvus-scholar_mind_knowledge_literature.jsonl `
     -Collection scholar_mind_knowledge_literature -CreateCollection
```

## 三、分步工具

| 脚本 | 用途 |
| --- | --- |
| `milvus-export.ps1` | Milvus REST v2 导出集合（含向量）为 JSONL；`-All` 自动导出全部 `scholar_mind*` 集合；`-OutputDir` 指定目录 |
| `milvus-import.ps1` | JSONL → 目标 Milvus：按源 schema 建集合（AUTOINDEX + COSINE）→ 分批插入 → 加载并校验；`-DryRun` 演练 |
| `milvus-jsonl-to-attu.ps1` | JSONL → Attu 可导入的 JSON 数组（UTF-8 无 BOM）；`-Verify` 校验维度与首字节 |
| `rustfs-export.ps1` | 桶 → 本地目录（对象 + 策略）；`-Mode auto\|mc\|sigv4` |
| `rustfs-import.ps1` | 本地目录 → 桶（建桶 + 逐对象上传 + 应用策略 + 校验）；支持目标桶改名；`-DryRun` 演练 |
| `rustfs-migrate.ps1` | 桶 → 桶在线迁移（mc mirror，增量幂等），并打印历史 URL 的 `REPLACE` 语句 |
| `s3-common.ps1` | 公共库：SigV4 签名请求、列/取/放/删对象、读写桶策略、`Resolve-McPath`（mc 自动发现） |

## 四、mc 从哪来（不必自己下载）

`Resolve-McPath` 会依次探测，取到第一个可用副本：

1. 调用方 `-McPath` 指定；
2. `tools/mc.exe`；
3. `%TEMP%\mc\mc.exe`；
4. **其他项目已下载的副本**（默认探测 `..\CampusRepair\backup\migration-bundle\mc.exe`）；
5. 都没有才尝试从 `dl.min.io` / GitHub release 下载（国内网络常不可达）。

> 实测：本机 `dl.min.io` 与 GitHub release 都下不动，但 CampusRepair 的迁移包里已有一份 `mc.exe`，
> 脚本会自动复用；即便完全没有 mc，SigV4 模式也可全功能替代。

## 五、要点与坑（实测踩过）

1. **不要拷 RustFS 磁盘目录**：不同机器的卷布局（多卷纠删码 / 单卷直存）不通用，必须按对象逻辑迁移。
2. **桶策略必须迁**：`mirror` 不带策略，漏了会导致文件 403。
3. **Milvus 维度必须匹配**：本项目 embedding 模型实际维度为 **2560**，与 `app.agent.rag.milvus.dimension` 不一致会报
   `Incorrect dimension for field 'vector'`（写入路径已带维度自愈：自动删表重建）。
4. **Milvus REST 分页上限**：`query` 的 `offset` 上限 16384，更大数据量需改用 `pymilvus` 的 `query_iterator`。
5. **MySQL 导入要指定字符集**：`--default-character-set=utf8mb4`，否则中文报 `Incorrect string value`。
6. **目标机地址变化**：存储端口/主机变了要批量替换历史文件 URL（脚本会打印 `UPDATE file_metadata ... REPLACE(...)`）。
7. **零字节 body 的签名细节**：PowerShell 会把空数组绑成 `$null`，`s3-common.ps1` 对零字节请求改为不带 Content
   （`Content-Length: 0`），否则建桶等操作会抛 `Value cannot be null (Parameter 'content')`。
8. **端点不可达时 mc 会"假装成功"**：`mc alias set` 失败后，`dst/xxx` 会被当成本地路径——`mb dst/x` 会在当前目录建文件夹
   并提示成功、`mirror` 报 `Preserve ... object storage to object storage transfer only`、`anonymous set-json`
   报 `not supported for filesystem`、`ls` 显示 0 对象。**此时 RustFS 里什么都没写进去**。
   新版本已在 `rustfs-export/import/migrate` 开头做 TCP 连通性预检，并对 `mc alias set` 的退出码做校验，
   直接给出「RustFS 未启动或端口不对」的明确报错；如遇历史版本，请先 `docker ps` 确认端口映射，
   删除误建的本地 `dst\` 目录后重试。

## 六、与其他脚本的关系

`scripts/` 专注「运行与验收」：`preflight-check.ps1`（一键体检）、`seed-demo-files.ps1`（绑定演示文件）、
`make-sample-pdf.ps1` / `verify-pdf-parsing.ps1`（全文解析验证）。
本目录 `tools/` 专注**环境迁移与数据导出**。
