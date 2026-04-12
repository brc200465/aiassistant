# AI学习助手后端

基于 **Spring Boot + MyBatis + MySQL + Redis + DeepSeek API** 实现的 AI 学习助手后端。  
当前版本已完成用户注册登录、会话管理、消息存储、真实 AI 问答、多轮上下文对话、会话列表缓存等核心能力。

---

## 1. 项目简介

本项目是一个面向学习场景的后端系统，目标不是简单调用一次大模型接口，而是将 **用户系统、登录鉴权、会话管理、消息存储、上下文问答、缓存优化** 串成一条完整的后端业务链。

和传统后台 CRUD 项目相比，本项目的核心业务不再是单表增删改查，而是围绕以下场景展开：

- 用户登录后进入自己的学习会话空间
- 用户可以创建多个学习会话
- 在会话中发送消息并获得 AI 回复
- 用户消息和 AI 消息分别落库
- 基于最近若干条历史消息构建上下文，实现多轮对话
- 使用 Redis 缓存当前用户的会话列表

---

## 2. 项目功能

### 2.1 用户模块
- 用户注册
- 用户登录
- 获取当前登录用户
- 退出登录

### 2.2 登录鉴权
- 基于 **Session** 保存登录状态
- 基于 **登录拦截器** 统一保护需要登录的接口
- 未登录统一返回 JSON 错误结果

### 2.3 会话模块
- 创建学习会话
- 查询当前用户的会话列表
- 查询某个会话详情
- 会话归属校验，防止越权访问

### 2.4 消息模块
- 发送消息
- 保存 `role=user` 的用户消息
- 保存 `role=assistant` 的 AI 回复消息
- 查询会话历史消息

### 2.5 AI 问答
- 接入 **DeepSeek API**
- 支持真实问答能力
- 支持多轮上下文对话
- 当前采用“最近若干条消息”作为上下文

### 2.6 Redis 缓存
- 缓存当前用户的会话列表
- 创建会话后删除缓存
- 发送消息后删除缓存
- 缓存失败时降级走数据库，不影响主流程

### 2.7 工程化能力
- DTO 参数校验
- 统一返回结果
- 统一错误码
- 全局异常处理

---

## 3. 技术栈

- **Java**
- **Spring Boot**
- **MyBatis**
- **MySQL**
- **Redis**
- **DeepSeek API**
- **Maven**
- **Git**

---

## 4. 项目亮点

### 4.1 AI 对话型后端，而不是普通 CRUD
本项目围绕 **conversation + message** 进行建模，将 AI 问答场景真正落成后端系统。

### 4.2 一轮问答拆成两条消息
一次问答拆成：
- `role=user`
- `role=assistant`

这样更适合：
- 历史消息展示
- 多轮上下文拼接
- 后续扩展更多角色消息

### 4.3 支持多轮上下文对话
发送消息时，不是只把当前一句发给模型，而是会查询当前会话最近若干条历史消息，一起发给 DeepSeek，提升回复连贯性。

### 4.4 主业务链和 AI 调用解耦
通过 `AiService` 抽象 AI 调用层，便于：
- mock 到真实 AI 的平滑切换
- 更换模型
- 后续扩展 system prompt / 学习模式

### 4.5 具备基础工程化能力
项目已补充：
- DTO 参数校验
- 统一错误码
- 全局异常处理
- Redis 缓存
- 登录拦截器

---

## 5. 核心业务流程

### 5.1 注册登录流程
1. 用户提交注册信息
2. 后端校验用户名是否已存在
3. 使用 BCrypt 对密码加密
4. 用户信息写入数据库
5. 登录时根据用户名查询用户
6. 使用 BCrypt 校验密码
7. 登录成功后将 `loginUserId` 写入 Session

### 5.2 发送消息流程
1. 获取当前登录用户 id
2. 根据 `conversationId` 查询会话
3. 校验会话是否存在
4. 校验会话是否属于当前登录用户
5. 保存当前用户消息（`role=user`）
6. 查询该会话最近若干条历史消息
7. 组装成 `AiChatMessage` 列表
8. 调用 DeepSeek API 生成回复
9. 保存 AI 回复消息（`role=assistant`）
10. 更新会话 `lastMessageTime`
11. 删除当前用户会话列表缓存
12. 返回本轮问答结果

---

## 6. 数据模型设计

### 6.1 user
表示系统用户。

核心字段：
- `id`
- `username`
- `password`
- `nickname`
- `create_time`
- `update_time`

### 6.2 conversation
表示一个学习会话。

核心字段：
- `id`
- `user_id`
- `title`
- `last_message_time`
- `create_time`
- `update_time`

### 6.3 message
表示会话中的一条消息。

核心字段：
- `id`
- `conversation_id`
- `role`
- `content`
- `token_count`
- `create_time`

### 6.4 表关系
- 一个用户可以拥有多个会话
- 一个会话可以拥有多条消息

---

## 7. 数据库建表 SQL

> 下面是一个可直接参考的建表版本，你可以按自己的数据库名执行。

```sql
CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(20) NOT NULL COMMENT '用户名',
  `password` VARCHAR(100) NOT NULL COMMENT '加密密码',
  `nickname` VARCHAR(20) DEFAULT NULL COMMENT '昵称',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';


CREATE TABLE `conversation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
  `title` VARCHAR(100) NOT NULL COMMENT '会话标题',
  `last_message_time` DATETIME NOT NULL COMMENT '最后消息时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_last_message_time` (`last_message_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';


CREATE TABLE `message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `conversation_id` BIGINT NOT NULL COMMENT '所属会话ID',
  `role` VARCHAR(20) NOT NULL COMMENT '消息角色：user/assistant',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `token_count` INT DEFAULT NULL COMMENT 'token数量（当前版本可为空）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';
```

---

## 8. 项目结构

```text
src/main/java/com/brc/aiassistant
├── AiAssistantApplication.java
├── common
│   ├── ErrorCode.java
│   └── Result.java
├── config
│   ├── AiConfig.java
│   ├── AiProperties.java
│   ├── PasswordConfig.java
│   └── WebMvcConfig.java
├── constant
│   └── RedisKeyConstants.java
├── controller
│   ├── ChatController.java
│   ├── ConversationController.java
│   └── UserController.java
├── dto
│   ├── AiChatMessage.java
│   ├── ChatSendDTO.java
│   ├── ConversationCreateDTO.java
│   ├── LoginDTO.java
│   └── RegisterDTO.java
├── entity
│   ├── Conversation.java
│   ├── Message.java
│   └── User.java
├── exception
│   ├── BusinessException.java
│   └── GlobalExceptionHandler.java
├── interceptor
│   └── LoginInterceptor.java
├── mapper
│   ├── ConversationMapper.java
│   ├── MessageMapper.java
│   └── UserMapper.java
├── service
│   ├── AiService.java
│   ├── ChatService.java
│   ├── ConversationService.java
│   ├── UserService.java
│   └── impl
│       ├── AiServiceImpl.java
│       ├── ChatServiceImpl.java
│       ├── ConversationServiceImpl.java
│       └── UserServiceImpl.java
└── vo
    ├── ChatResponseVO.java
    ├── ConversationVO.java
    ├── MessageVO.java
    └── UserVO.java
```

---

## 9. 环境要求

- JDK 17 或以上
- Maven 3.9+
- MySQL 8.x
- Redis 6.x / 7.x

---

## 10. 配置说明

### 10.1 application.properties 示例

```properties
spring.application.name=ai-assistant
server.port=8080

# MySQL
spring.datasource.url=jdbc:mysql://localhost:3306/ai_assistant?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8
spring.datasource.username=root
spring.datasource.password=你的数据库密码
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# MyBatis
mybatis.configuration.map-underscore-to-camel-case=true
mybatis.type-aliases-package=com.brc.aiassistant.entity
logging.level.com.brc.aiassistant.mapper=debug

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.database=0

# AI（当前接入 DeepSeek）
ai.openai.enabled=true
ai.openai.base-url=https://api.deepseek.com
ai.openai.api-key=${DEEPSEEK_API_KEY:}
ai.openai.model=deepseek-chat
ai.openai.timeout-millis=30000
```

> 说明：
> - 当前配置类前缀仍然使用 `ai.openai.*`，但实际已经接入 **DeepSeek API**
> - `ai.openai.api-key=${DEEPSEEK_API_KEY:}` 表示优先从环境变量读取 API Key
> - 如果暂时不想走真实 AI，可将 `ai.openai.enabled=false`

---

## 11. 启动步骤

### 11.1 克隆项目
```bash
git clone 你的仓库地址
cd 项目目录
```

### 11.2 创建数据库并执行建表 SQL
在 MySQL 中创建数据库：

```sql
CREATE DATABASE ai_assistant DEFAULT CHARACTER SET utf8mb4;
```

然后执行 README 中的建表 SQL。

### 11.3 启动 Redis
确保本地 Redis 服务已启动。

### 11.4 配置 DeepSeek API Key
在系统环境变量中配置：

```bash
DEEPSEEK_API_KEY=你的DeepSeekAPIKey
```

### 11.5 修改 application.properties
把数据库用户名、密码改成你本机的配置。

### 11.6 启动项目
```bash
mvn spring-boot:run
```

或在 IDE 中直接运行启动类：

```java
AiAssistantApplication
```

---

## 12. 接口说明

统一返回结构：

```json
{
  "code": 1,
  "message": "success",
  "data": {}
}
```

失败时示例：

```json
{
  "code": 4002,
  "message": "请先登录",
  "data": null
}
```

---

### 12.1 用户模块

#### 1）注册
**POST** `/users/register`

请求体：
```json
{
  "username": "ssh",
  "password": "123456",
  "nickname": "sunshine"
}
```

#### 2）登录
**POST** `/users/login`

请求体：
```json
{
  "username": "ssh",
  "password": "123456"
}
```

#### 3）获取当前登录用户
**GET** `/users/me`

#### 4）退出登录
**POST** `/users/logout`

---

### 12.2 会话模块

#### 1）创建会话
**POST** `/conversations`

请求体：
```json
{
  "title": "Java集合"
}
```

#### 2）查询当前用户会话列表
**GET** `/conversations`

#### 3）查询会话详情
**GET** `/conversations/{id}`

---

### 12.3 消息模块

#### 1）发送消息
**POST** `/chat`

请求体：
```json
{
  "conversationId": 1,
  "content": "请解释一下 ArrayList 和 LinkedList 的区别"
}
```

返回示例：
```json
{
  "code": 1,
  "message": "success",
  "data": {
    "conversationId": 1,
    "userMessage": "请解释一下 ArrayList 和 LinkedList 的区别",
    "assistantMessage": "......"
  }
}
```

#### 2）查询会话历史消息
**GET** `/conversations/{id}/messages`

---

## 13. 错误码说明

| 错误码 | 含义 |
|---|---|
| 1 | 成功 |
| 4001 | 参数错误 |
| 4002 | 未登录 |
| 4003 | 无权限 |
| 4004 | 资源不存在 |
| 4005 | 数据冲突 |
| 5001 | AI 调用失败 |
| 5000 | 系统异常 |

---

## 14. 当前已完成能力

- [x] 用户注册
- [x] 用户登录
- [x] 获取当前登录用户
- [x] 退出登录
- [x] 登录拦截器
- [x] 会话创建
- [x] 会话列表查询
- [x] 会话详情查询
- [x] 发送消息
- [x] 查询历史消息
- [x] user / assistant 消息双落库
- [x] 接入真实 DeepSeek API
- [x] 基于最近若干条历史消息实现上下文问答
- [x] Redis 缓存当前用户会话列表
- [x] DTO 参数校验
- [x] 统一错误码
- [x] 全局异常处理

---

## 15. 后续优化方向

- [ ] 更细的上下文裁剪策略（按 token / 字符长度）
- [ ] system prompt
- [ ] 学习模式（解释 / 面试 / 代码问答）
- [ ] 自动生成会话标题
- [ ] 更细的 AI 调用异常分类
- [ ] token 统计
- [ ] 流式输出
- [ ] 更多缓存场景

---

## 16. 面试中可重点讲的点

1. 为什么设计 `conversation` 和 `message` 两张核心表
2. 为什么一轮问答要拆成 `user / assistant` 两条消息
3. 发送消息这条链路怎么走
4. 为什么要做会话归属校验
5. 如何接入 DeepSeek API
6. 为什么上下文只取最近若干条消息
7. Redis 为什么先缓存会话列表
8. 为什么创建会话和发送消息后都要删缓存
9. 统一错误码和全局异常处理如何提升工程化程度

---

## 17. 总结

这个项目最核心的价值，不是“接上了 DeepSeek”，而是：

**把用户系统、登录鉴权、会话管理、消息存储、真实 AI 调用、上下文历史和 Redis 缓存，真正串成了一条完整的 Java 后端业务链。**

对于我来说，它相较于传统后台 CRUD 项目，更能体现我在 AI 场景下做后端系统设计与实现的能力。