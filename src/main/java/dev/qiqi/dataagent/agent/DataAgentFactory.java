package dev.qiqi.dataagent.agent;

import dev.qiqi.dataagent.config.QiqiProperties;
import dev.qiqi.dataagent.identity.UserIdentity;
import dev.qiqi.dataagent.tool.CatalogTools;
import dev.qiqi.dataagent.tool.ExecuteSqlAgentTool;
import dev.qiqi.dataagent.tool.SqlTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.Model;
import io.agentscope.core.state.AgentStateStore;
import io.agentscope.core.state.InMemoryAgentStateStore;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter;
import org.springframework.stereotype.Component;

/**
 * Qiqi 业务代码与 AgentScope Runtime 的装配边界。
 *
 * <p>这个类只负责把模型、工具、提示词和状态存储组装成 ReActAgent。SQL 校验、数据权限和
 * 查询执行不放在这里，避免业务规则反向依赖 AgentScope。</p>
 */
@Component
public class DataAgentFactory {
    // 系统提示词负责告诉模型“怎样工作”，但不能代替服务端权限校验。
    private static final String BASE_PROMPT = """
            You are Qiqi DataAgent, an evidence-first business data analyst.

            Rules:
            1. Inspect actual tables and columns before writing SQL. Never invent a table, column, row, or number.
            2. For relative dates, call current_time and inspect the available data range. State the exact interval used.
            3. Call validate_sql before execute_sql. If validation or execution fails, repair the query; do not describe rejected SQL as executed.
            4. Treat execute_sql output as evidence. Calculations must use complete aggregate query results, never a truncated preview.
            5. The server enforces the authenticated user's data scope. Never request, guess, or pass a user id in tool arguments.
            6. The final answer must state definitions, time interval, findings and queryId evidence. Distinguish observed changes from causal hypotheses.
            7. Reply in the user's language.
            """;

    private final QiqiProperties properties;
    private final CatalogTools catalogTools;
    private final SqlTools sqlTools;
    private final ExecuteSqlAgentTool executeSql;

    // Agent 每次请求重新创建，但状态仓库必须共享，才能用 userId + sessionId 恢复多轮上下文。
    private final AgentStateStore stateStore = new InMemoryAgentStateStore();

    // Model 实现由 AgentScope 提供，可以被多个按请求创建的 Agent 复用。
    private final Model model;

    public DataAgentFactory(QiqiProperties properties, CatalogTools catalogTools,
                            SqlTools sqlTools, ExecuteSqlAgentTool executeSql) {
        this.properties = properties;
        this.catalogTools = catalogTools;
        this.sqlTools = sqlTools;
        this.executeSql = executeSql;

        // 没有密钥时不构造模型，让应用和安全测试仍然能够启动；真正聊天时再返回明确错误。
        this.model = properties.model().apiKey().isBlank() ? null
                : properties.model().provider().equals("openai") ? OpenAIChatModel.builder()
                .apiKey(properties.model().apiKey())
                .baseUrl(properties.model().baseUrl())
                .modelName(properties.model().name())
                .stream(true)
                .formatter(new OpenAIChatFormatter())
                .build()
                : DashScopeChatModel.builder()
                .apiKey(properties.model().apiKey())
                .modelName(properties.model().name())
                .stream(true)
                .formatter(new DashScopeChatFormatter())
                .build();
    }

    public boolean modelConfigured() {
        return model != null;
    }

    public ReActAgent create(UserIdentity identity) {
        if (model == null) throw new IllegalStateException("Model API key is not configured (QIQI_MODEL_API_KEY or DASHSCOPE_API_KEY)");

        // Toolkit 是 AgentScope 的工具注册表。前两个对象通过 @Tool 反射注册；
        // execute_sql 需要精确控制参数和 RuntimeContext，因此显式实现 AgentTool。
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(catalogTools);
        toolkit.registerTool(sqlTools);
        toolkit.registerAgentTool(executeSql);

        // 身份说明可以帮助模型解释结果，但真正的授权仍在 ExecuteSqlAgentTool 中执行。
        // 不能因为身份已经写进提示词，就信任模型生成的用户或部门信息。
        String identityContext = """

                Authenticated context supplied by the server:
                - display name: %s
                - data scope: %s
                - department id: %s
                """.formatted(identity.displayName(), identity.dataScope(), identity.departmentId());

        // ReActAgent 是核心运行时：model 负责推理，toolkit 负责行动，maxIters 防止无限循环，
        // stateStore 保存会话状态。按请求创建 Agent 可避免并发请求共享可变执行现场。
        return ReActAgent.builder()
                .name("qiqi-data-agent")
                .sysPrompt(BASE_PROMPT + identityContext)
                .model(model)
                .toolkit(toolkit)
                .maxIters(properties.model().maxIterations())
                .stateStore(stateStore)
                .build();
    }
}
