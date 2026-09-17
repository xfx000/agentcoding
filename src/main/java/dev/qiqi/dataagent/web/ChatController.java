package dev.qiqi.dataagent.web;

import dev.qiqi.dataagent.agent.DataAgentService;
import dev.qiqi.dataagent.identity.IdentityService;
import dev.qiqi.dataagent.identity.UserIdentity;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ChatController {
    private final DataAgentService agent;
    private final IdentityService identities;
    private final AgentEventMapper eventMapper;

    public ChatController(DataAgentService agent, IdentityService identities, AgentEventMapper eventMapper) {
        this.agent = agent;
        this.identities = identities;
        this.eventMapper = eventMapper;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<StreamEvent>> stream(
            @RequestHeader("X-Qiqi-User") String username,
            @Valid @RequestBody ChatRequest request) {
        UserIdentity identity = identities.findActiveByUsername(username)
                .orElseThrow(() -> new SecurityException("Unknown or inactive demo user"));
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? "qiqi-" + UUID.randomUUID() : request.conversationId().trim();
        return agent.stream(request.query().trim(), conversationId, identity)
                .map(eventMapper::map)
                .onErrorResume(error -> Flux.just(StreamEvent.error(safeMessage(error))))
                .map(event -> ServerSentEvent.<StreamEvent>builder(event)
                        .event(event.type()).build());
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        List<Map<String, Object>> users = identities.listDemoUsers().stream().map(user -> Map.<String, Object>of(
                "username", user.username(), "displayName", user.displayName(),
                "dataScope", user.dataScope(), "departmentId", user.departmentId() == null ? "" : user.departmentId())).toList();
        return Map.of("name", "Qiqi DataAgent", "modelConfigured", agent.modelConfigured(), "demoUsers", users);
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return "Agent execution failed";
        return message.length() <= 300 ? message : message.substring(0, 300);
    }
}
