# VectorDB 与 Embedding Model 原理说明

## 什么是 Embedding？

Embedding 是将文本转换为**高维数值向量**的过程。每段文本会被映射为一个固定长度的浮点数数组（如 1024 维），语义相近的文本在向量空间中距离更近。

```text
"friendly dog loves swimming"  →  [0.12, -0.34, 0.78, ..., 0.56]  (1024 维)
"energetic puppy enjoys water" →  [0.11, -0.32, 0.76, ..., 0.54]  (1024 维)  ← 很近！
"quantum physics equation"     →  [0.89, 0.23, -0.45, ..., -0.71]  (1024 维)  ← 很远
```

## 什么是 VectorDB？

VectorDB（向量数据库）专门存储和检索 embedding 向量。核心操作是**相似度搜索**：给一个查询向量，找出最接近的 N 条记录。

```text
用户问题 → embedding → 查询向量 → VectorDB 相似度搜索 → 返回最相关的文档
```

本项目使用的是 **Chroma** — 一个轻量级开源向量数据库，支持本地运行。

## 本项目的数据流

```text
写入流程（添加宠物时）:
Pet 描述文本 → Cohere Embed (Bedrock) → 向量 → 存入 Chroma

查询流程（RAG 问答时）:
用户问题 → Cohere Embed (Bedrock) → 查询向量 → Chroma 相似度搜索
  → 返回相关文档 → 拼入 prompt → Claude 回答
```

## AWS Bedrock 上的 Embedding 模型

### 可用模型

| 模型 | Model ID | 维度 | 说明 |
| ---- | -------- | ---- | ---- |
| Titan Embed Text v1 | `amazon.titan-embed-text-v1` | 1536 | AWS 自研，较旧 |
| Titan Embed Text v2 | `amazon.titan-embed-text-v2:0` | 可配置 | AWS 自研，需要 on-demand 权限 |
| Titan Embed G1 | `amazon.titan-embed-g1-text-02` | 1536 | AWS 自研，基础版 |
| Cohere Embed English v3 | `cohere.embed-english-v3` | 1024 | 英文优化 |
| Cohere Embed Multilingual v3 | `cohere.embed-multilingual-v3` | 1024 | 多语言支持 |
| Cohere Embed v4 | `cohere.embed-v4:0` | 1024 | 最新版 |

### Titan vs Cohere 在 Spring AI 中的兼容性

| 模型 | Spring AI 1.1.7 兼容性 | 问题 |
| ---- | ---- | ---- |
| Titan Embed v1 | 不兼容 | Spring AI 发送 v2 格式请求（含 dimensions/normalize），v1 不接受 |
| Titan Embed v2 | 需要 on-demand | 很多账户未开通，调用报 "not supported" |
| Titan Embed G1 | 不兼容 | 同 v1 问题 |
| **Cohere Embed v3** | **完全兼容** | 推荐使用 |

### 正确使用 Titan Embedding 的方法

如果你的 AWS 账户已开通 Titan Embed v2 的 on-demand 访问，理论上可以使用。配置如下：

```yaml
spring:
  ai:
    bedrock:
      titan:
        embedding:
          enabled: true
          model: amazon.titan-embed-text-v2:0
```

**但需注意：**
1. 必须在 AWS Console → Bedrock → Model access 中开通 `amazon.titan-embed-text-v2:0`
2. Spring AI 1.1.7 的 `BedrockTitanEmbeddingModel` 只兼容 v2 格式
3. Titan v1 / G1 模型与 Spring AI 1.1.7 不兼容（请求格式冲突）

### 推荐：使用 Cohere Embed

对于 Spring AI 1.1.7，**Cohere Embed 是最稳定的选择**：

```yaml
spring:
  ai:
    bedrock:
      titan:
        embedding:
          enabled: false       # 禁用 Titan
      cohere:
        embedding:
          enabled: true
          model: cohere.embed-english-v3
          input-type: SEARCH_DOCUMENT
```

Java 启动类中排除 Titan 自动配置：
```java
@SpringBootApplication(exclude = BedrockTitanEmbeddingAutoConfiguration.class)
```

## Chroma VectorStore

### 安装与启动

```bash
# 安装
pip install chromadb

# 启动 server（数据持久化到 ./chroma-data）
chroma run --path ./chroma-data --port 8000
```

### 版本要求

Spring AI 1.1.7 的 Chroma 客户端使用 **v2 API**（多租户），需要 Chroma ≥ 0.5（推荐 1.5+）。

验证：
```bash
# v2 API 应返回 heartbeat
curl http://localhost:8000/api/v2

# 如果返回 404，说明版本太旧，需升级
pip install chromadb --upgrade
```

### Spring AI 配置

```yaml
spring:
  ai:
    vectorstore:
      chroma:
        client:
          host: http://localhost    # 必须包含 http:// 前缀
          port: 8000
        collection-name: pet-knowledge
        initialize-schema: true    # 自动创建集合
```

**注意：** `host` 必须写 `http://localhost` 而不是 `localhost`，否则报 "Target host is not specified"。

### 相似度阈值

搜索时可以设置相似度阈值，过滤掉不相关的结果：

```java
SearchRequest request = SearchRequest.builder()
        .query(query)
        .topK(topK)
        .similarityThreshold(0.3)  // 只返回相似度 > 30% 的结果
        .build();
```

阈值选择建议：
- **0.7+**：过于严格，Cohere embedding 在短文本场景下很多有效结果会被过滤
- **0.3~0.5**：推荐范围，平衡精准度和召回率
- **不设阈值**：总会返回 topK 条结果，即使完全不相关

## 完整流程示意

```text
1. 用户通过 Web GUI 添加宠物：
   POST /pets {"name":"Buddy", "description":"Friendly dog, loves fetch"}

2. 后端处理：
   a) 保存到 H2 数据库（CRUD）
   b) 拼接文本："Buddy is a 3-year-old Golden Retriever dog. Friendly dog, loves fetch"
   c) 调用 Cohere Embed (Bedrock) 生成 1024 维向量
   d) 存入 Chroma collection "pet-knowledge"

3. 用户在 Chat 窗口（RAG 模式）提问：
   GET /rag/ask?question=Which pet likes playing?

4. 后端处理：
   a) 将问题 "Which pet likes playing?" embedding 为向量
   b) 在 Chroma 中做余弦相似度搜索，返回 topK=3 且相似度 > 0.3 的文档
   c) 如果找到相关文档 → 拼入 prompt 给 Claude 回答
   d) 如果没找到 → 返回"知识库中没有相关信息"
```
