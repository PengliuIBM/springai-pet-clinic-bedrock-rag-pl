# Spring AI + Bedrock Embedding + Chroma 集成踩坑记录

## 目标

在 Spring AI 项目中使用 AWS Bedrock 的 Embedding 模型将文档向量化，存入本地 Chroma VectorStore，实现 RAG 问答。

## 问题排查过程

### 第 1 步：两个 EmbeddingModel bean 冲突

**错误：**
```
Parameter 0 of method vectorStore required a single bean, but 2 were found:
- cohereEmbeddingModel
- titanEmbeddingModel
```

**原因：** `spring-ai-bedrock` 包含两个 EmbeddingModel 的 AutoConfiguration：
- `BedrockTitanEmbeddingAutoConfiguration` → 注册 `titanEmbeddingModel`
- `BedrockCohereEmbeddingAutoConfiguration` → 注册 `cohereEmbeddingModel`

两个都被自动发现，Spring 不知道用哪个。

**解决：** 在 `@SpringBootApplication` 中排除不需要的那个：
```java
@SpringBootApplication(exclude = BedrockTitanEmbeddingAutoConfiguration.class)
```

---

### 第 2 步：Chroma URL 格式错误

**错误：**
```
I/O error on GET request for "localhost:/api/v2/tenants/...": Target host is not specified
```

**原因：** `application.yml` 中配置 `host: localhost`，缺少 scheme（`http://`）。

**解决：**
```yaml
spring.ai.vectorstore.chroma.client.host: http://localhost
```

---

### 第 3 步：Chroma 版本不兼容 (v1 vs v2 API)

**错误：**
```
404 Not Found: "{"detail":"Not Found"}"
# 在 createTenant 调用时
```

**原因：** Spring AI 1.1.7 的 Chroma 客户端使用 v2 API（多租户），但安装的 Chroma 0.4.15 只支持 v1 API。

**解决：** 升级 Chroma 到 1.5+：
```bash
pip install chromadb --upgrade  # 升级到 1.5.9
chroma run --path ./chroma-data --port 8000
```

验证 v2 API 可用：
```bash
curl http://localhost:8000/api/v2
# 应返回 {"nanosecond heartbeat":...}
```

---

### 第 4 步：Titan Embed v2 需要 on-demand 权限

**错误：**
```
ValidationException: Invocation of model ID amazon.titan-embed-text-v2:0 with on-demand throughput isn't supported.
```

**原因：** `amazon.titan-embed-text-v2:0` 在该 AWS 账户中没有启用 on-demand 访问。

**尝试：** 切换到 v1 模型 `amazon.titan-embed-text-v1`。

---

### 第 5 步：Titan Embed v1 请求格式不兼容

**错误：**
```
ValidationException: Malformed input request: 2 schema violations found
```

**原因：** Spring AI 1.1.7 的 `BedrockTitanEmbeddingModel` 默认发送 v2 格式的请求体（包含 `dimensions` 和 `normalize` 字段），但 Titan v1 不接受这些字段。

**尝试：** 切换到 `amazon.titan-embed-g1-text-02` — 同样失败，相同原因。

**结论：** Spring AI 1.1.7 的 Titan embedding 实现与 Titan v1 模型不兼容，它总是发送 v2 格式请求。

---

### 第 6 步（最终解决）：改用 Cohere Embedding

**方案：** 放弃 Titan，改用 Bedrock 上的 Cohere Embed English v3。

**配置：**
```yaml
spring:
  ai:
    bedrock:
      titan:
        embedding:
          enabled: false
      cohere:
        embedding:
          enabled: true
          model: cohere.embed-english-v3
          input-type: SEARCH_DOCUMENT
```

**Java：**
```java
@SpringBootApplication(exclude = BedrockTitanEmbeddingAutoConfiguration.class)
```

**结果：** 成功！Cohere embed 与 Spring AI 1.1.7 完全兼容。

---

## 最终可工作的配置

### application.yml
```yaml
spring:
  ai:
    bedrock:
      aws:
        region: us-east-1
      converse:
        chat:
          enabled: true
          options:
            model: us.anthropic.claude-sonnet-4-6
            max-tokens: 1024
            temperature: 0.7
      titan:
        embedding:
          enabled: false
      cohere:
        embedding:
          enabled: true
          model: cohere.embed-english-v3
          input-type: SEARCH_DOCUMENT
    vectorstore:
      chroma:
        client:
          host: http://localhost
          port: 8000
        collection-name: pet-knowledge
        initialize-schema: true
```

### 依赖 (pom.xml)
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-bedrock-converse</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bedrock</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-vector-store-chroma</artifactId>
</dependency>
```

### 前置要求
- Chroma 1.5+（支持 v2 API）
- AWS 账户启用 `cohere.embed-english-v3` 模型访问
- AWS 账户启用 Claude 模型访问

---

## 经验总结

| 问题 | 根因 | 教训 |
| ---- | ---- | ---- |
| 多个 EmbeddingModel bean | Bedrock autoconfigure 自动注册所有可用模型 | 用 `exclude` 或 `enabled: false` 精确控制 |
| Chroma host 缺 scheme | Spring AI 不自动补 `http://` | 配置中写全 URL scheme |
| Chroma API 版本 | Spring AI 1.1.7 要求 v2 API | 升级 Chroma ≥ 0.5 |
| Titan embed 格式 | Spring AI 库总是发 v2 请求格式 | Titan v1 与 Spring AI 1.1.7 不兼容 |
| Titan v2 无权限 | on-demand 未启用 | 需要在 Model Access 中开通 |
| **最终选择 Cohere** | Cohere embed 完全兼容 | Bedrock embedding 首选 Cohere |
