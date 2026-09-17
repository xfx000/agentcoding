package dev.qiqi.dataagent.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.event.AgentEvent;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AgentEventMapper {
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    private final ObjectMapper mapper;

    public AgentEventMapper(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public StreamEvent map(AgentEvent event) {
        Map<String, Object> data = new LinkedHashMap<>(mapper.convertValue(event, MAP));
        data.remove("type");
        return new StreamEvent(event.getType().getValue(), data);
    }
}
