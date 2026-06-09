# Spring AI Pet - CRUD + RAG Demo

Spring AI + AWS Bedrock (Claude) + Chroma VectorStore 的完整示例项目，包含 Pet CRUD、向量语义搜索和 RAG 问答。

## 版本信息

| 组件 | 版本 |
| ---- | ---- |
| JDK | Amazon Corretto 25.0.3 |
| Maven | 3.9.9 (通过 Maven Wrapper) |
| Spring Boot | 3.5.14 |
| Spring AI | 1.1.7 |
| LLM | Claude Sonnet 4.6 (us.anthropic.claude-sonnet-4-6) |
| Embedding (默认) | Titan Embed Text v2 (amazon.titan-embed-text-v2:0) |
| Embedding (备选) | Cohere Embed English v3 (cohere.embed-english-v3) |
| VectorStore | Chroma 1.5+ (本地 server 模式) |
| 数据库 | H2 (内存) |

## 架构

```text
┌─────────────────────────────────────────────────────┐
│                   Web GUI (index.html)               │
│         Pet CRUD 面板  |  Chat 窗口 (3 种模式)       │
└─────────────┬───────────────────────┬───────────────┘
              │                       │
     ┌────────▼────────┐    ┌────────▼────────┐
     │  /pets (CRUD)   │    │  /rag/ask       │
     │  PetInfoController  │  RagController   │
     └────────┬────────┘    └────────┬────────┘
              │                       │
     ┌────────▼────────┐    ┌────────▼────────┐
     │  H2 Database    │    │  Chroma Search  │
     │  (持久化存储)    │    │  (语义检索)      │
     └─────────────────┘    └────────┬────────┘
                                     │
                            ┌────────▼────────┐
                            │  Claude (LLM)   │
                            │  基于检索结果回答 │
                            └─────────────────┘
```

## 快速启动

### 前置条件

1. JDK 17+（`~/java`）
2. AWS Credentials（已配置 Bedrock 访问）
3. Python 3（用于运行 Chroma）

### 步骤 1：启动 Chroma VectorStore

```bash
# 安装 Chroma（如未安装）
pip install chromadb

# 启动 Chroma server（保持运行）
chroma run --path ./chroma-data --port 8000
```

验证 Chroma 运行正常：
```bash
curl http://localhost:8000/api/v2
# 应返回: {"nanosecond heartbeat":...}
```

### 步骤 2：启动 Spring AI App

**默认使用 Titan Embed v2（需要在 Bedrock Console 开通模型访问）：**

```bash
cd springai-pet
export JAVA_HOME=~/java
./mvnw spring-boot:run
```

**切换到 Cohere Embed（无需额外开通）：**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=cohere
```

### 步骤 3：打开 Web GUI

浏览器访问：**http://localhost:8080**

- 左侧面板：添加/删除宠物（描述会自动 embedding 存入 Chroma）
- 右侧面板：Chat 窗口，支持 3 种模式
  - **RAG** — 先从 Chroma 语义搜索，找到相关数据后喂给 Claude 回答
  - **Direct Chat** — 直接和 Claude 对话（不查 VectorStore）
  - **Stream Chat** — 流式输出（逐 token 显示）

## API 端点

| 端点 | 方法 | 说明 |
| ---- | ---- | ---- |
| `/pets` | GET | 列出所有宠物 |
| `/pets/{id}` | GET | 获取单个宠物 |
| `/pets` | POST | 创建宠物（同时 embedding 存入 Chroma） |
| `/pets/{id}` | PUT | 更新宠物 |
| `/pets/{id}` | DELETE | 删除宠物（同时从 Chroma 删除） |
| `/rag/ask?question=...` | GET | RAG 问答（检索 + LLM） |
| `/rag/search?query=...` | GET | 纯语义搜索（返回相关文档） |
| `/chat?message=...` | GET | 直接对话（同步） |
| `/chat/stream?message=...` | GET | 直接对话（SSE 流式） |

## 构建与测试

```bash
# 构建
./mvnw clean package -DskipTests

# 运行单元测试
./mvnw test
```

## 项目结构

```text
springai-pet/
├── mvnw                                    # Maven Wrapper
├── pom.xml                                 # 依赖配置
├── embedding-troubleshooting.md            # Embedding 集成踩坑记录
└── src/
    ├── main/java/com/example/pet/
    │   ├── PetApplication.java             # 启动类（排除 Titan AutoConfig）
    │   ├── ChatController.java             # /chat 同步端点
    │   ├── StreamChatController.java       # /chat/stream 流式端点
    │   ├── crud/
    │   │   ├── PetInfo.java                # 宠物实体
    │   │   ├── PetInfoRepository.java      # JPA Repository
    │   │   └── PetInfoController.java      # CRUD REST Controller
    │   └── rag/
    │       ├── PetRagService.java          # 向量化 + 搜索服务
    │       └── RagController.java          # RAG 问答端点
    ├── main/resources/
    │   ├── application.yml                 # 全部配置
    │   └── static/index.html               # Web GUI
    └── test/java/com/example/pet/
        ├── ChatControllerTest.java         # Chat 单元测试
        └── StreamChatControllerTest.java   # Stream 单元测试
```

## 配置说明

`application.yml` 关键配置：

| 配置项 | 说明 |
| ------ | ---- |
| `spring.ai.bedrock.aws.region` | AWS Region (us-east-1) |
| `spring.ai.bedrock.converse.chat.options.model` | LLM Inference Profile ID |
| `spring.ai.bedrock.cohere.embedding.model` | Embedding 模型 ID |
| `spring.ai.vectorstore.chroma.client.host` | Chroma 地址（需含 http://） |
| `spring.ai.vectorstore.chroma.client.port` | Chroma 端口 (8000) |
| `spring.ai.vectorstore.chroma.collection-name` | Chroma 集合名称 |

## 依赖说明

| 依赖 | 用途 |
| ---- | ---- |
| `spring-ai-starter-model-bedrock-converse` | Claude LLM (Bedrock Converse API) |
| `spring-ai-bedrock` | Bedrock Embedding Model (Cohere) |
| `spring-ai-starter-vector-store-chroma` | Chroma VectorStore 集成 |
| `spring-boot-starter-data-jpa` + `h2` | Pet CRUD 数据持久化 |
| `spring-boot-starter-web` | REST API |
| `spring-boot-starter-webflux` | SSE 流式响应 |
