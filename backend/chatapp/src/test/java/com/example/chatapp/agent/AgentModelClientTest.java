package com.example.chatapp.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class AgentModelClientTest {
  @Test
  void sendsBoundedNonStreamingRequestsAndRejectsInvalidOrTruncatedResponses() {
    final AgentModelClient client = new AgentModelClient("http://localhost:11434");
    final MockRestServiceServer server =
        MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(client, "client"))
            .build();
    server
        .expect(requestTo("http://localhost:11434/api/chat"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("\"stream\":false")))
        .andRespond(
            withSuccess(
                "{\"message\":{\"content\":\"answer\"},\"done_reason\":\"stop\"}",
                MediaType.APPLICATION_JSON));
    assertEquals(
        "answer",
        client
            .chat(
                "model",
                List.of(),
                Map.of(),
                List.of(Map.of("function", Map.of("name", "web_search"))))
            .path("content")
            .asText());
    server.verify();
    for (final String body :
        List.of(
            "{}",
            "{\"error\":\"offline\"}",
            "not json",
            "{\"message\":{},\"done_reason\":\"length\"}")) {
      server.reset();
      server
          .expect(requestTo("http://localhost:11434/api/chat"))
          .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
      assertThrows(
          IllegalStateException.class, () -> client.chat("model", List.of(), Map.of(), List.of()));
      server.verify();
    }
  }

  @Test
  void missingModelsAndOversizeContextGiveActionableErrors() {
    final AgentModelClient client = new AgentModelClient("http://localhost:11434");
    final MockRestServiceServer server =
        MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(client, "client"))
            .build();
    server
        .expect(requestTo("http://localhost:11434/api/chat"))
        .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
    final IllegalStateException unavailable =
        assertThrows(
            IllegalStateException.class,
            () -> client.chat("missing", List.of(), Map.of(), List.of()));
    assertTrue(unavailable.getMessage().contains("Choose an installed model"));
    server.verify();
    assertThrows(
        IllegalStateException.class,
        () ->
            client.chat(
                "model",
                List.of(Map.of("role", "user", "content", "x".repeat(10000))),
                Map.of("num_ctx", 1024),
                List.of()));
  }
}
