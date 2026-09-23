# AI 学习助手后端

基于 Java 21、Spring Boot、MyBatis、MySQL、Redis 和 DeepSeek API 实现的学习问答后端。

支持用户注册登录、会话管理、多轮 AI 问答、历史消息查询，以及请求幂等、失败重试、会话并发控制和过期任务恢复。

## 1. 技术栈

| 技术 | 用途 |
|---|---|
| Java 21 | 开发语言 |
| Spring Boot 4.0.5 | Web 应用与依赖管理 |
| MyBatis Starter 4.0.0 | 数据库访问 |
| MySQL 8.x / InnoDB | 消息、会话与请求状态持久化 |
| Redis | 用户会话列表缓存 |
| BCrypt | 密码哈希 |
| Java HttpClient | 调用 DeepSeek API |
| Maven | 项目构建 |

## 2. 已实现功能

### 用户与鉴权

- 用户注册、登录、退出及当前用户查询。
- BCrypt 密码哈希与校验。
- HttpSession 保存登录状态。
- 登录拦截器保护业务接口。
- 校验会话归属，防止访问其他用户的会话。

### 会话与消息

- 创建会话、查询会话列表和详情。
- 分别保存用户问题与 AI 回复。
- 查询消息时，通过单条关联 SQL 同时读取消息与请求状态。
- 按首次提问顺序展示，每条回复紧跟对应问题。

### AI 问答

- 接入 DeepSeek 的 Chat Completions 接口。
- 可通过配置切换真实 AI 和模拟回复。
- 上下文使用当前问题之前最近 4 轮完整成功问答，再追加当前问题，最多 9 条消息。
- 根据请求关联的消息 ID 配对问题与回复，避免按消息条数截取时拆散问答。

### 请求状态与重试

- 使用 requestId 标识一次逻辑提问。
- 同一次提问重试复用原 ID，不重复保存用户问题。
- 成功请求重复提交，直接返回已保存的回复。
- AI 失败后保留问题，并记录失败状态和提示。
- attempt 区分生成尝试，防止旧调用覆盖新一次重试。
- leaseUntil 表示本次尝试的处理期限，支持过期接管。

### 同一会话并发控制

- 同一会话最多允许一个有效生成任务。
- 存在其他未过期任务时，新提问返回会话忙碌错误，不保存新问题。
- 其他任务已过期时，先将其标记为失败，再允许新任务进入。
- 数据库锁仅覆盖短事务，不在等待 AI 时持续持有。

### 缓存与接口基础

- Redis 缓存用户会话列表，TTL 为 10 分钟。
- 会话列表缓存读取、写入失败时，仍返回数据库查询结果。
- 聊天成功后删除会话列表缓存；删除失败只记录日志。
- DTO 参数校验、统一响应和全局异常处理。

注意：创建会话后的缓存删除目前仍未捕获 Redis 异常，详见“已知限制”。

## 3. 核心流程

### 注册登录

1. 校验注册参数与用户名。
2. 使用 BCrypt 处理密码后写入数据库。
3. 登录时校验用户名和密码。
4. 将 loginUserId 保存到 Session。
5. 后续请求携带 Session Cookie 访问业务接口。

### 发送消息

```text
POST /chat
    ↓
begin()：短事务
    ├─ 校验参数和会话归属
    ├─ 检查相同请求的状态
    ├─ 检查会话是否被其他有效任务占用
    └─ 保存问题和PROCESSING记录，或取得重试资格
    ↓ 提交事务
组装完整问答上下文
    ↓
调用AI，不持有数据库事务
    ├─ 成功 → complete()短事务
    │          保存回复、更新SUCCESS、更新会话时间
    └─ 失败 → fail()短事务
               保留问题、记录FAILED和失败原因
```

如果保存回复失败，成功事务回滚后，再尝试记录失败状态。若数据库不可用，失败状态也可能无法立即写入，此时返回结果暂无法确认的提示，后续使用原请求 ID 恢复。

### 请求状态规则

| 情况 | 处理方式 |
|---|---|
| 新请求 | 保存用户问题，状态设为 PROCESSING，attempt=1 |
| 同请求正在处理且未过期 | 返回 PROCESSING，不再次调用 AI |
| 同请求已成功 | 返回原回复 |
| 同请求失败后重试 | 复用问题，attempt 加一，重新生成 |
| 同请求处理过期后重试 | 更新 attempt 和处理期限，接管任务 |
| 其他请求正在处理且未过期 | 返回 4006，不创建新问题 |
| 其他请求已过期 | 将旧请求标记为 FAILED，再接收新任务 |

处理期限按“AI 请求超时时间 + 60 秒余量”计算。当前默认 AI 超时为 30 秒，因此处理期限约为设置时刻之后 90 秒。

过期恢复由后续请求触发，没有定时扫描。期限过期不会自动取消远端 AI 调用；状态和 attempt 校验负责阻止已经失去资格的旧调用写入结果。

## 4. 数据模型

| 表 | 作用 |
|---|---|
| user | 用户账号及密码哈希 |
| conversation | 用户的学习会话 |
| message | 用户问题、AI 回复 |
| chat_request | 一次提问的幂等标识、消息关联及处理状态 |

chat_request 的主要字段：

| 字段 | 含义 |
|---|---|
| conversation_id | 所属会话 |
| request_id | 同一次提问重试时复用的标识 |
| user_message_id | 用户问题消息 ID |
| assistant_message_id | AI 回复消息 ID，未成功时为空 |
| status | PROCESSING、SUCCESS、FAILED |
| error_message | 可展示的失败原因 |
| attempt | 生成尝试次数，首次为 1 |
| lease_until | 本次处理期限 |
| create_time / update_time | 创建与更新时间 |

唯一约束 `(conversation_id, request_id)` 防止重复创建同一请求。

用户问题和请求记录在同一事务中创建；AI 回复、成功状态及会话时间在另一个事务中提交。

## 5. 数据库初始化

创建数据库：

```sql
CREATE DATABASE ai_learn_system DEFAULT CHARACTER SET utf8mb4;

USE ai_learn_system;
```

全新数据库先创建基础表：

```sql
CREATE TABLE `user` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(20) NOT NULL,
    password VARCHAR(100) NOT NULL,
    nickname VARCHAR(20) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE conversation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    last_message_time DATETIME NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_last_message_time (last_message_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    token_count INT DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_conversation_id (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

然后手动执行：

[src/main/resources/db/migration/V2__chat_request.sql](src/main/resources/db/migration/V2__chat_request.sql)

已有基础表的环境只需补建 chat_request；已经创建过的表不要重复执行建表语句。

当前未集成自动数据库迁移工具，文件放在 `db/migration` 下不代表启动时会自动执行。

旧消息不会自动生成请求关联记录：它们仍可展示，但不参与新版 AI 上下文，也不支持通过原请求 ID 重试。

## 6. 项目结构

Java 包路径：

```text
src/main/java/com/example/aiassistant
├── AiassistantApplication.java
├── common          # 统一响应、错误码
├── config          # AI配置、密码配置、MVC配置
├── constant        # Redis键前缀
├── controller      # 用户、会话、聊天接口
├── dto             # 请求参数、AI消息、完整问答、生成资格结果
├── entity          # User、Conversation、Message、ChatRequest
├── exception       # 业务异常、全局异常处理
├── interceptor     # 登录拦截器
├── mapper          # MyBatis注解SQL
├── service         # 业务接口
│   └── impl        # 业务实现
└── vo              # 返回给客户端的数据
```

关键类：

- `ChatServiceImpl`：协调发送流程、组装上下文、查询历史。
- `ChatRequestServiceImpl`：管理请求状态、事务、并发和过期接管。
- `AiServiceImpl`：构造 HTTP 请求、调用 AI 并解析回复。
- `MessageMapper`：历史关联查询和完整成功问答查询。
- `AiChatTurn`：一轮完整的问题与回复。

## 7. 环境与启动

需要 JDK 21、Maven、MySQL 8.x 和 Redis。

克隆项目并进入包含 pom.xml 的目录：

```bash
git clone https://github.com/brc200465/aiassistant.git
cd aiassistant
```

完成数据库初始化，并启动 Redis。

配置环境变量：

| 环境变量 | 用途 |
|---|---|
| DB_USERNAME | 数据库用户名 |
| DB_PASSWORD | 数据库密码 |
| DEEPSEEK_API_KEY | DeepSeek API Key |

环境变量需要对启动 Java 的终端或 IDE 运行配置可见。

当前主要配置：

```properties
spring.application.name=aiassistant
server.port=8080

spring.datasource.url=jdbc:mysql://localhost:3306/ai_learn_system?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

mybatis.configuration.map-underscore-to-camel-case=true
mybatis.type-aliases-package=com.example.aiassistant.entity

spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.database=0
spring.data.redis.timeout=5s

ai.openai.enabled=true
ai.openai.base-url=https://api.deepseek.com/v1
ai.openai.api-key=${DEEPSEEK_API_KEY:}
ai.openai.model=deepseek-chat
ai.openai.timeout-millis=30000
```

虽然配置前缀为 `ai.openai`，当前实际调用 DeepSeek。

设置 `ai.openai.enabled=false` 可使用模拟回复，但数据库等业务依赖仍需正常配置。

启动：

```bash
mvn spring-boot:run
```

或在 IDE 中运行：

```text
com.example.aiassistant.AiassistantApplication
```

## 8. 接口

除注册和登录外，业务接口需要携带登录后的 Session Cookie。

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | /users/register | 注册 |
| POST | /users/login | 登录 |
| GET | /users/me | 当前用户 |
| POST | /users/logout | 退出 |
| POST | /conversations | 创建会话 |
| GET | /conversations | 会话列表 |
| GET | /conversations/{id} | 会话详情 |
| POST | /chat | 提问或重试 |
| GET | /conversations/{conversationId}/messages | 历史消息 |

注册示例：

```json
{
  "username": "student",
  "password": "example123",
  "nickname": "学习者"
}
```

登录示例：

```json
{
  "username": "student",
  "password": "example123"
}
```

创建会话：

```json
{
  "title": "Java基础"
}
```

### 发送消息

```json
{
  "requestId": "test-001",
  "conversationId": 1,
  "content": "什么是多态？"
}
```

requestId 为不超过 64 个字符的非空字符串：

- 新提问生成新 ID，例如 UUID。
- 同一次提问的重试复用原 ID。
- 同一 ID 不能更换问题内容。
- 手工测试可使用 test-001、test-002 等标识。
- 创建会话接口不受这个幂等键保护。

成功响应示例，消息 ID 仅作示意：

```json
{
  "code": 1,
  "message": "success",
  "data": {
    "conversationId": 1,
    "userMessage": "什么是多态？",
    "assistantMessage": "多态是……",
    "requestId": "test-001",
    "status": "SUCCESS",
    "errorMessage": null,
    "userMessageId": 10,
    "assistantMessageId": 11
  }
}
```

前端必须检查 `data.status`：

| 状态 | 含义 |
|---|---|
| PROCESSING | 原请求仍在处理中，当前没有最终回复 |
| SUCCESS | 回复已保存 |
| FAILED | 处理失败，用户问题保留，可复用原 ID 重试 |

外层 `code=1` 表示接口正常返回业务结果，不一定表示 AI 已生成成功。记录失败状态成功时，也会返回 `code=1`、`data.status=FAILED`。

不同请求遇到会话忙碌时：

```json
{
  "code": 4006,
  "message": "当前会话正在生成回复，请稍后再发送",
  "data": null
}
```

### 历史消息

每条消息包含：

- id、role、content、createTime。
- requestId、status、errorMessage。

消息按首次提问顺序组织，回复紧跟问题。前端应保留接口顺序，不要再次按消息 ID 或创建时间排序。

没有请求关联的旧消息，其请求状态字段为 null。

## 9. 错误码

| 错误码 | 含义 |
|---|---|
| 1 | 正常返回 |
| 4001 | 参数错误 |
| 4002 | 未登录 |
| 4003 | 无权限 |
| 4004 | 资源不存在 |
| 4005 | 数据冲突 |
| 4006 | 会话忙碌 |
| 5001 | AI 调用错误 |
| 5002 | 系统异常 |

聊天流程中的 AI 异常通常会转为 FAILED 请求状态。若失败状态也无法保存，则返回系统异常。

当前多数错误通过响应体表达，HTTP 状态通常仍为 200，客户端不能只检查 HTTP 状态。

## 10. 验证进度

主要逻辑已实现并经过静态代码审查，但尚未完成系统性的数据库集成、并发与故障恢复测试。

测试目录目前只有 Spring 上下文加载测试，不代表业务流程已全部验证。

重点验证场景：

- 正常生成并保存完整问答。
- 成功请求重复提交，不新增消息或再次调用 AI。
- AI 失败后问题保留，重试复用问题并增加 attempt。
- 同一会话的不同请求同时提交，后者返回忙碌。
- 超时接管后，旧调用不能保存回复或覆盖状态。
- 回复保存失败时，回复与 SUCCESS 状态一起回滚。
- A失败、B成功、重试A成功后，历史仍按问答配对展示。
- 历史超过4轮时，只选最近4轮完整成功问答。
- Redis故障时聊天结果不被缓存删除异常影响。

## 11. 已知限制与待办

### 优先修复

- 创建会话后的 Redis 缓存删除尚未捕获异常，可能出现数据库已插入、接口却报错。
- 创建会话接口没有幂等保护，网络重试可能创建重复会话。
- AI回复未检查 finish_reason，截断内容可能被视为完整成功。
- 非法JSON、参数类型错误、并发注册用户名冲突等异常分类待完善。
- AI调用的线程中断处理待完善。

### 后续完善

- 历史消息和会话列表分页。
- 按token或字符预算裁剪上下文。
- 请求状态查询接口与超时任务自动恢复。
- 缓存失效失败后的补偿机制。
- AI调用限流、用量统计和更细的错误分类。
- 流式输出、学习模式、自动会话标题。
- 核心业务的自动化测试。

同一会话限制的是“有效生成任务”。过期后的旧远端调用可能仍在运行，但不能继续写入结果。

当前允许重试旧失败问题，并按原提问位置展示回复；不会重新生成它后面已经成功的问答，也未实现对话分支。