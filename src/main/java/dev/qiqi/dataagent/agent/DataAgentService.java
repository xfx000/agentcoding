package dev.qiqi.dataagent.agent;

import dev.qiqi.dataagent.identity.UserIdentity;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.UserMessage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 把一次业务请求转换为 AgentScope 调用，并把 AgentEvent 流交给 Web 层。
 */
@Service
public class DataAgentService {
    private final DataAgentFactory factory;

    public DataAgentService(DataAgentFactory factory) {
        this.factory = factory;
    }

    public Flux<AgentEvent> stream(String query, String conversationId, UserIdentity identity) {
        // defer 表示订阅 SSE 时才创建 Agent 和开始模型调用，避免 Controller 组装响应时提前执行。
        return Flux.defer(() -> factory.create(identity).streamEvents(
                new UserMessage(query),
                // RuntimeContext 是单次调用的可信上下文。userId + sessionId 同时决定状态槽，
                // 也会随工具调用传给 ExecuteSqlAgentTool；它们不是由模型生成的参数。
                RuntimeContext.builder()
                        .userId(Long.toString(identity.id()))
                        .sessionId(conversationId)
                        .build()));
    }

    public boolean modelConfigured() {
        return factory.modelConfigured();
    }
}
