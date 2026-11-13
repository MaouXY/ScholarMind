-- 开放获取论文元数据与摘要译文演示集
-- 内容来自 arXiv 官方论文页面；正文片段是中文摘要译文，不是论文全文。
-- 不绑定伪造原文文件。下载入口仍保持禁用，直到管理员上传合法原文。
-- 执行：完成 sql/00~10 后执行本文件，再通过管理端重建文献向量。
-- 来源：https://arxiv.org/abs/2004.04906
-- 来源：https://arxiv.org/abs/2005.11401
-- 来源：https://arxiv.org/abs/2104.08663

INSERT INTO literature (
  id, title, author, source, publish_year, language, category_id, uploader_id,
  visibility, status, abstract_text, keywords, read_count, download_count,
  reviewed_by, reviewed_at, deleted
) VALUES
  (1200000000000000060,
   'Dense Passage Retrieval for Open-Domain Question Answering',
   'Vladimir Karpukhin, Barlas Oğuz, Sewon Min, Patrick Lewis, Ledell Wu, Sergey Edunov, Danqi Chen, Wen-tau Yih',
   'arXiv:2004.04906', 2020, 'en-US', 1100000000000000002, 1000000000000000001,
   'PUBLIC', 'PUBLISHED',
   'Open-domain question answering relies on efficient passage retrieval to select candidate contexts, where sparse models such as TF-IDF or BM25 are widely used. This work studies dense representations learned from questions and passages with a dual-encoder framework. On several open-domain QA datasets, the dense retriever improves top-20 passage retrieval accuracy over a strong BM25 baseline.',
   'Dense Retrieval, BM25, Dual Encoder, Open-Domain Question Answering',
   0, 0, 1000000000000000001, NOW(), 0),
  (1200000000000000061,
   'Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks',
   'Patrick Lewis, Ethan Perez, Aleksandra Piktus, Fabio Petroni, Vladimir Karpukhin, Naman Goyal, Heinrich Küttler, Mike Lewis, Wen-tau Yih, Tim Rocktäschel, Sebastian Riedel, Douwe Kiela',
   'arXiv:2005.11401', 2020, 'en-US', 1100000000000000003, 1000000000000000001,
   'PUBLIC', 'PUBLISHED',
   'This work studies retrieval-augmented generation for knowledge-intensive NLP tasks. The proposed models combine a pre-trained sequence-to-sequence generator with non-parametric memory represented by a dense vector index, accessed through a neural retriever. Experiments cover several knowledge-intensive tasks and report gains over parametric-only baselines on open-domain question answering.',
   'Retrieval-Augmented Generation, Neural Retrieval, Question Answering, Knowledge Intensive NLP',
   0, 0, 1000000000000000001, NOW(), 0),
  (1200000000000000062,
   'BEIR: A Heterogeneous Benchmark for Zero-shot Evaluation of Information Retrieval Models',
   'Nandan Thakur, Nils Reimers, Andreas Rücklé, Abhishek Srivastava, Iryna Gurevych',
   'arXiv:2104.08663', 2021, 'en-US', 1100000000000000002, 1000000000000000001,
   'PUBLIC', 'PUBLISHED',
   'BEIR presents a heterogeneous benchmark for evaluating information retrieval models in zero-shot settings. It collects datasets from diverse retrieval domains and tasks, enabling comparison of lexical, dense, and re-ranking methods beyond a single training distribution. The benchmark highlights that retrieval effectiveness varies across domains and that no single approach dominates every setting.',
   'Information Retrieval, Zero-shot Evaluation, Benchmark, Dense Retrieval, BM25',
   0, 0, 1000000000000000001, NOW(), 0)
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  author = VALUES(author),
  source = VALUES(source),
  publish_year = VALUES(publish_year),
  language = VALUES(language),
  category_id = VALUES(category_id),
  uploader_id = VALUES(uploader_id),
  visibility = VALUES(visibility),
  status = VALUES(status),
  abstract_text = VALUES(abstract_text),
  keywords = VALUES(keywords),
  reviewed_by = VALUES(reviewed_by),
  reviewed_at = VALUES(reviewed_at),
  deleted = 0;

INSERT INTO literature_chunk (
  id, literature_id, chunk_index, chunk_text, token_count,
  embedding_provider, vector_store_status, enabled, deleted
) VALUES
  (8500000000000000200, 1200000000000000060, 0,
   '摘要译文：开放域问答需要从语料中高效检索候选段落。传统方法常采用 TF-IDF 或 BM25 等稀疏检索模型。论文研究仅依赖稠密向量表示的段落检索方法。',
   0, 'seed:arxiv-abstract-translation', 'DB_ONLY', 1, 0),
  (8500000000000000201, 1200000000000000060, 1,
   '摘要译文：研究采用双编码器分别表示问题与段落，并在多个开放域问答数据集上评估。作者报告该方法相较强 BM25 基线提升了前 20 个候选段落的检索准确率。',
   0, 'seed:arxiv-abstract-translation', 'DB_ONLY', 1, 0),
  (8500000000000000202, 1200000000000000061, 0,
   '摘要译文：论文研究面向知识密集型自然语言处理任务的检索增强生成，将预训练序列到序列生成模型与非参数化知识记忆结合。',
   0, 'seed:arxiv-abstract-translation', 'DB_ONLY', 1, 0),
  (8500000000000000203, 1200000000000000061, 1,
   '摘要译文：知识记忆由稠密向量索引表示，并通过神经检索器访问。论文在多类知识密集型任务上进行实验，并报告开放域问答任务相较纯参数化模型基线的效果。',
   0, 'seed:arxiv-abstract-translation', 'DB_ONLY', 1, 0),
  (8500000000000000204, 1200000000000000062, 0,
   '摘要译文：BEIR 提出用于零样本信息检索评估的异质基准，汇集来自不同领域与任务的数据集，以便在单一训练分布之外比较词法检索、稠密检索和重排序方法。',
   0, 'seed:arxiv-abstract-translation', 'DB_ONLY', 1, 0),
  (8500000000000000205, 1200000000000000062, 1,
   '摘要译文：该基准显示，检索方法的效果会随领域变化，没有一种方法能在所有设置中占优。使用时应结合具体语料评估，而不能把单一基准上的结果当作普遍结论。',
   0, 'seed:arxiv-abstract-translation', 'DB_ONLY', 1, 0)
ON DUPLICATE KEY UPDATE
  literature_id = VALUES(literature_id),
  chunk_index = VALUES(chunk_index),
  chunk_text = VALUES(chunk_text),
  token_count = VALUES(token_count),
  embedding_provider = VALUES(embedding_provider),
  vector_store_status = VALUES(vector_store_status),
  enabled = 1,
  deleted = 0;
