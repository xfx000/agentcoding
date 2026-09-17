package dev.qiqi.dataagent.agent;

import dev.qiqi.dataagent.identity.UserIdentity;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class DataAgentService {
    private final DataAgentFactory factory;

    public DataAgentService(DataAgentFactory factory) {
        this.factory = factory;
    }

    public Flux<AgentEvent> stream(String query, String conversationId, UserIdentity identity) {
        return Flux.defer(() -> factory.create(identity).streamEvents(
                new UserMessage(query), RuntimeContext.builder()
                        .userId(Long.toString(identity.id()))
                        .sessionId(conversationId)
                        .build()));
    }

    public boolean modelConfigured() {
        return factory.modelConfigured();
    }
}
