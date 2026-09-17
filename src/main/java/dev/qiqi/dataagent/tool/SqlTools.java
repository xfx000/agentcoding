package dev.qiqi.dataagent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qiqi.dataagent.query.SqlPolicy;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

@Component
public class SqlTools {
    private final SqlPolicy policy;
    private final ObjectMapper mapper;

    public SqlTools(SqlPolicy policy, ObjectMapper mapper) {
        this.policy = policy;
        this.mapper = mapper;
    }

    @Tool(name = "validate_sql",
            description = "Validate one read-only SQL statement, enforce the table allowlist and cap its LIMIT. Call before execute_sql.",
            readOnly = true)
    public String validate(@ToolParam(name = "sql", description = "A single SELECT or WITH query") String sql) {
        return json(policy.validate(sql));
    }

    @Tool(name = "current_time",
            description = "Return current time in the configured server timezone for relative-date questions.", readOnly = true)
    public String currentTime() {
        return json(Map.of("time", OffsetDateTime.now(ZoneId.systemDefault()).toString(),
                "zone", ZoneId.systemDefault().getId()));
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize tool result", e);
        }
    }
}
