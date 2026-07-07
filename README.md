# 12345 智能铁路票务助手

Spring Boot 3.4.0 + MyBatis-Plus + MySQL + LangChain4j 依赖预留 + RAG 规则 + 工具调用 + 安全增强 + 现代国铁蓝前端工作台。

## Docker 启动

```bash
.\.tools\apache-maven-3.9.9\bin\mvn.cmd -s .mvn\settings.xml -DskipTests package
docker compose up --build
```

启动后访问：

```text
http://127.0.0.1:19999/index.html
```

MySQL：

```text
127.0.0.1:3306
database: ticket_assistant
username: root
password: root
```

## 验收演示建议

1. 新建会话 A，输入“我要购票”。
2. 按提示补充：`姓名张三 身份证110101199001011234 G1 2026-07-20 硬座 北京到上海 1张`。
3. 观察 AI 流式回复、订单面板、MySQL 的 `ticket_order` 记录。
4. 新建会话 B，询问“刚才我的票是什么”，演示记忆隔离。
5. 回到会话 A，输入“我要退票”，再提供订单号，演示退票和手续费。
6. 输入“北京天气怎么样”，演示天气工具调用。
7. 点击“抽取目的地”或输入“帮我随机抽一个目的地”，演示创新动效。

## 安全增强

- CORS 白名单
- 安全响应头
- API 限流
- API Key 预留：设置 `TICKET_API_KEY`
- 参数校验
- SQL 注入防护：MyBatis-Plus 参数绑定
- 身份证展示脱敏
- 操作审计日志
- 全局异常处理

## AI 能力

- 流式聊天输出
- 会话记忆隔离
- 聊天记忆持久化
- RAG 票务规则文件：`src/main/resources/rag/rag-service.txt`
- 购票工具调用
- 退票工具调用
- 天气工具调用
- 随机目的地推荐
- 浏览器语音输入
