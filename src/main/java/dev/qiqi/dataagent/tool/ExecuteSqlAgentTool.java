package dev.qiqi.dataagent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qiqi.dataagent.identity.IdentityService;
import dev.qiqi.dataagent.identity.UserIdentity;
import dev.qiqi.dataagent.query.ReadOnlyQueryService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@Component
public class ExecuteSqlAgentTool implements AgentTool {
    private static final Map<String, Object> PARAMETERS = Map.of(
            "type", "object",
            "additionalProperties", false,
            "properties", Map.of("sql", Map.of("type", "string", "description", "Validated SELECT or WITH query")),
            "required", List.of("sql"));

    private final IdentityService identities;
    private final ReadOnlyQueryService queries;
    private final ObjectMapper mapper;

    public ExecuteSqlAgentTool(IdentityService identities, ReadOnlyQueryService queries, ObjectMapper mapper) {
        this.identities = identities;
        this.queries = queries;
        this.mapper = mapper;
    }

    @Override public String getName() { return "execute_sql"; }
    @Override public String getDescription() {
        return "Execute one validated read-only SQL query. User identity and data scope are supplied by the server, never by model arguments.";
    }
    @Override public Map<String, Object> getParameters() { return PARAMETERS; }
    @Override public boolean isReadOnly() { return true; }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return Mono.fromCallable(() -> execute(param)).subscribeOn(Schedulers.boundedElastic());
    }

    private ToolResultBlock execute(ToolCallParam param) throws JsonProcessingException {
        RuntimeContext context = param.getRuntimeContext();
        if (context == null || context.getUserId() == null || context.getSessionId() == null) {
            throw new SecurityException("Authenticated user and conversation are required");
        }
        UserIdentity identity = identities.findActiveById(context.getUserId())
                .filter(UserIdentity::canQuery)
                .orElseThrow(() -> new SecurityException("User cannot query data"));
        Object rawSql = param.getInput().get("sql");
        if (!(rawSql instanceof String sql) || sql.isBlank()) throw new IllegalArgumentException("sql is required");
        var result = queries.execute(sql, identity, context.getSessionId());
        return ToolResultBlock.text(mapper.writeValueAsString(result));
    }
}
