package dev.qiqi.dataagent.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationSmokeTest {
    @Autowired WebTestClient client;

    @Test
    void metadataAndStaticPageWorkWithoutModelKey() {
        client.get().uri("/api/meta").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.name").isEqualTo("Qiqi DataAgent")
                .jsonPath("$.modelConfigured").isEqualTo(false)
                .jsonPath("$.demoUsers.length()").isEqualTo(3);
        byte[] page = client.get().uri("/").exchange().expectStatus().isOk()
                .expectBody().returnResult().getResponseBody();
        assertThat(new String(page, java.nio.charset.StandardCharsets.UTF_8)).contains("Qiqi DataAgent");
    }

    @Test
    void unknownIdentityIsRejectedBeforeAgentExecution() {
        client.post().uri("/api/chat/stream").header("X-Qiqi-User", "unknown")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue("{\"query\":\"hello\"}").exchange().expectStatus().isUnauthorized();
    }
}
