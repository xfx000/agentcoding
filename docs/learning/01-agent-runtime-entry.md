# 第一课：一次请求怎样进入 AgentScope

这一课只解决一个问题：用户在页面提交问题后，Qiqi DataAgent 怎样创建 `ReActAgent`，并把可信身份传给工具。

暂时不要研究 SQL AST、部门条件和 Harness。先把 Runtime 主链看懂。

## 先记住四个角色

| 角色 | 在项目中的实现 | 可以先怎样理解 |
| --- | --- | --- |
| Controller | `ChatController` | 接收用户问题，确认演示身份 |
| Agent 服务 | `DataAgentService` | 把业务请求转换成 AgentScope 调用 |
| Agent 工厂 | `DataAgentFactory` | 装配模型、工具、提示词和状态仓库 |
| SQL 工具适配器 | `ExecuteSqlAgentTool` | 把 AgentScope Tool Call 交给 Qiqi 查询服务 |

AgentScope 在中间负责推理循环和工具调度，Qiqi 负责身份、权限和数据库查询。

```text
HTTP 请求
  -> ChatController
  -> DataAgentService
  -> ReActAgent.streamEvents
  -> Model 判断是否调用工具
  -> Toolkit 分发 execute_sql
  -> ExecuteSqlAgentTool
  -> ReadOnlyQueryService
```

## 第一步：从 Controller 看可信输入

打开 `ChatController.stream`，只看三件事：

1. `X-Qiqi-User` 被转换成服务端查询到的 `UserIdentity`。
2. `conversationId` 被创建或复用。
3. 这两个值与问题一起传给 `DataAgentService`。

这里还没有 AgentScope。它的职责是先确定“谁在提问”和“属于哪个会话”。

请回答：为什么不能把请求体里的 `userId` 直接交给模型？

## 第二步：看 RuntimeContext

打开 `DataAgentService.stream`：

```java
RuntimeContext.builder()
        .userId(Long.toString(identity.id()))
        .sessionId(conversationId)
        .build()
```

`RuntimeContext` 是一次 Agent 调用的上下文，不是模型生成的内容。

- `userId` 表示当前服务端确认的用户。
- `sessionId` 表示当前对话。
- AgentScope 用二者选择会话状态。
- AgentScope 也会把同一个上下文传给后续工具。

`Flux.defer` 表示只有前端真正订阅 SSE 后，Agent 才会创建并开始运行。

请回答：`userId` 相同但 `sessionId` 不同，会发生什么？两个值都相同时又会发生什么？

## 第三步：拆开 ReActAgent Builder

打开 `DataAgentFactory.create`，把 Builder 分成四块看：

```java
.sysPrompt(...)     // 告诉模型角色和工作规则
.model(model)       // 调用哪个大模型
.toolkit(toolkit)   // 模型可以使用哪些工具
.stateStore(...)    // 多轮状态保存在哪里
```

再看：

```java
.maxIters(...)
```

它限制一次请求最多执行多少轮 Reasoning/Acting，防止模型反复调用工具无法结束。

项目共享 `Model` 和 `AgentStateStore`，但每次请求创建新的 `ReActAgent`。这样既能恢复同一会话的状态，又不会让两个并发请求共享一个正在变化的 Agent 执行现场。

请回答：如果每次请求都新建 `InMemoryAgentStateStore`，多轮对话还能够恢复吗？

## 第四步：比较两种工具注册

```java
toolkit.registerTool(catalogTools);
toolkit.registerTool(sqlTools);
toolkit.registerAgentTool(executeSql);
```

- `registerTool` 会扫描对象上的 `@Tool` 方法，适合简单工具。
- `registerAgentTool` 注册显式实现的 `AgentTool`，适合需要自定义参数 Schema、异步执行和读取 `RuntimeContext` 的工具。

`execute_sql` 的模型参数只有 `sql`，没有 `userId`。身份从 `RuntimeContext` 获取，因此模型无法通过修改 Tool Call 参数切换用户。

## 建议下四个断点

配置 `DASHSCOPE_API_KEY` 后，以 Debug 模式启动，在下面四处下断点：

1. `ChatController.stream`
2. `DataAgentService.stream`
3. `DataAgentFactory.create`
4. `ExecuteSqlAgentTool.callAsync`

在页面使用 `alice` 提问：

```text
统计每月已付款收入，并给出查询证据。
```

每停一次，只记录以下内容：

```text
当前 userId 是什么？
当前 sessionId 是什么？
当前对象属于 Qiqi 还是 AgentScope？
下一步会调用谁？
```

## 本课完成标准

能够不用看代码讲清下面这段话，就可以进入下一课：

> Controller 先确认用户和会话；DataAgentService 将它们放入 RuntimeContext；DataAgentFactory 装配并创建 ReActAgent；AgentScope 完成模型推理和工具分发；ExecuteSqlAgentTool 从 RuntimeContext 取回可信身份，再进入 Qiqi 自己的查询服务。

下一课再研究 `ReActAgent` 内部的 Reasoning、Acting 和 `AgentEvent` 流。
