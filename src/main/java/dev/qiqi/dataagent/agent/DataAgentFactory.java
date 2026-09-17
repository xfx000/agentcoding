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
import org.springframework.stereotype.Component;

@Component
public class DataAgentFactory {
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
    private final AgentStateStore stateStore = new InMemoryAgentStateStore();
    private final Model model;

    public DataAgentFactory(QiqiProperties properties, CatalogTools catalogTools,
                            SqlTools sqlTools, ExecuteSqlAgentTool executeSql) {
        this.properties = properties;
        this.catalogTools = catalogTools;
        this.sqlTools = sqlTools;
        this.executeSql = executeSql;
        this.model = properties.model().apiKey().isBlank() ? null : DashScopeChatModel.builder()
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
        if (model == null) throw new IllegalStateException("DASHSCOPE_API_KEY is not configured");
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(catalogTools);
        toolkit.registerTool(sqlTools);
        toolkit.registerAgentTool(executeSql);
        String identityContext = """

                Authenticated context supplied by the server:
                - display name: %s
                - data scope: %s
                - department id: %s
                """.formatted(identity.displayName(), identity.dataScope(), identity.departmentId());
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
