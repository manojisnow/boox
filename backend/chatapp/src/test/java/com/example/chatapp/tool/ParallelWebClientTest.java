package com.example.chatapp.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

class ParallelWebClientTest {
  private static final String ENDPOINT = "https://search.parallel.ai/mcp";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final RestTemplate http = new RestTemplate();
  private final WebSessionService sessions = mock(WebSessionService.class);
  private MockRestServiceServer server;
  private ParallelWebClient client;

  @BeforeEach
  void setup() {
    server = MockRestServiceServer.createServer(http);
    client = new ParallelWebClient(http, sessions);
    when(sessions.sessionId("conv")).thenReturn("ea6fa0d5-a93f-4d82-84a5-f390ba9cb622");
  }

  private void initialize(final boolean transportSession) {
    server
        .expect(requestTo(ENDPOINT))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("initialize")))
        .andRespond(
            withSuccess(
                    "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-03-26\"}}",
                    MediaType.APPLICATION_JSON)
                .header("Mcp-Session-Id", transportSession ? "transport" : ""));
    server
        .expect(requestTo(ENDPOINT))
        .andExpect(header("MCP-Protocol-Version", "2025-03-26"))
        .andRespond(withStatus(HttpStatus.ACCEPTED));
  }

  private String result(final Map<String, Object> payload) {
    return MAPPER.writeValueAsString(
        Map.of(
            "jsonrpc",
            "2.0",
            "id",
            2,
            "result",
            Map.of(
                "content",
                List.of(Map.of("type", "text", "text", MAPPER.writeValueAsString(payload))))));
  }

  private Map<String, Object> source(final String url, final String text) {
    return Map.of("url", url, "title", "Official docs", "excerpts", List.of(text));
  }

  @Test
  void speaksMcpWithTrustedMetadataAndClosesTransport() {
    ReflectionTestUtils.setField(client, "apiKey", "test-secret");
    initialize(true);
    server
        .expect(requestTo(ENDPOINT))
        .andExpect(header("Authorization", "Bearer test-secret"))
        .andExpect(header("Mcp-Session-Id", "transport"))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.containsString("ea6fa0d5-a93f-4d82-84a5-f390ba9cb622")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("exact-model")))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("forged"))))
        .andRespond(
            withSuccess(
                result(
                    Map.of(
                        "results",
                        List.of(
                            source(
                                "https://docs.example.org/current", "Latest stable is 4.1.1.")))),
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(ENDPOINT))
        .andExpect(method(HttpMethod.DELETE))
        .andRespond(withSuccess());
    String output =
        client.call("web_search", Map.of("session_id", "forged"), "conv", "exact-model");
    assertTrue(output.contains("Latest stable is 4.1.1"));
    assertTrue(output.contains("Source: https://docs.example.org/current"));
    server.verify();
  }

  @Test
  void supportsEventStreamAndStructuredContent() {
    initialize(false);
    server
        .expect(requestTo(ENDPOINT))
        .andRespond(
            withSuccess(
                "event: message\ndata: "
                    + MAPPER.writeValueAsString(
                        Map.of(
                            "jsonrpc",
                            "2.0",
                            "id",
                            2,
                            "result",
                            Map.of(
                                "structuredContent",
                                Map.of(
                                    "results",
                                    List.of(source("https://example.org/page", "Page evidence"))))))
                    + "\n\n",
                MediaType.TEXT_EVENT_STREAM));
    assertTrue(client.call("web_fetch", Map.of(), "conv", null).contains("Page evidence"));
    server.verify();
  }

  @Test
  void preservesLaterSourceUrlsWithinEvidenceLimit() {
    final List<Map<String, Object>> rows =
        java.util.stream.IntStream.range(0, 5)
            .mapToObj(
                i ->
                    source(
                        "https://example.org/source" + i,
                        "Important fact " + i + " " + "x".repeat(20_000)))
            .toList();
    String output =
        ParallelWebClient.normalize(MAPPER.valueToTree(Map.of("results", rows)), "web_search");
    assertTrue(output.length() <= 4_000);
    for (int i = 0; i < 5; i++) {
      assertTrue(output.contains("Source: https://example.org/source" + i));
      assertTrue(output.contains("Important fact " + i));
    }
    assertTrue(output.contains("Excerpt shortened"));
  }

  @Test
  void filtersUnusableSourcesAndExplainsPartialFetch() {
    assertTrue(
        ParallelWebClient.normalize(MAPPER.valueToTree(Map.of("results", List.of())), "web_search")
            .startsWith("No results"));
    String output =
        ParallelWebClient.normalize(
            MAPPER.valueToTree(
                Map.of(
                    "results",
                    List.of(
                        source("http://127.0.0.1/secret", "private"),
                        source("https://example.org/page", "public")),
                    "errors",
                    List.of(Map.of("url", "https://failed.example.org", "content", "credential")))),
            "web_fetch");
    assertFalse(output.contains("private"));
    assertFalse(output.contains("credential"));
    assertTrue(output.contains("Some pages could not be read"));
  }

  @Test
  void rejectsPrivateAndMalformedUrls() {
    for (String url :
        List.of(
            "file:///tmp/private",
            "http://localhost:8080",
            "http://192.168.1.1",
            "http://[::1]",
            "https://user:pass@example.org",
            "http://machine.local",
            "https://foo.internal",
            "https://foo.test",
            "https://example.org:8080",
            "garbage")) assertFalse(ParallelWebClient.publicUrl(url), url);
    assertTrue(
        ParallelWebClient.publicUrl("https://docs.spring.io/spring-boot/system-requirements.html"));
  }

  @Test
  void returnsSafeErrorsForRateLimitsAuthenticationAndProviderFailure() {
    for (HttpStatus status :
        List.of(
            HttpStatus.TOO_MANY_REQUESTS,
            HttpStatus.UNAUTHORIZED,
            HttpStatus.FORBIDDEN,
            HttpStatus.BAD_GATEWAY)) {
      server.reset();
      server.expect(requestTo(ENDPOINT)).andRespond(withStatus(status).body("secret-key"));
      String output = client.call("web_search", Map.of(), "conv", "model");
      assertTrue(output.startsWith("Error:"));
      assertFalse(output.contains("secret-key"));
      if (status == HttpStatus.TOO_MANY_REQUESTS) assertTrue(output.contains("rate limit"));
      server.verify();
    }
  }

  @Test
  void failsSafelyForInvalidProtocolEmptyAndMalformedResponses() {
    for (String body :
        List.of(
            "",
            "not json",
            "{\"id\":1,\"error\":{\"message\":\"secret\"}}",
            "{\"id\":1,\"result\":{\"protocolVersion\":\"unknown\"}}",
            "{\"id\":99}")) {
      server.reset();
      server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
      assertTrue(client.call("web_search", Map.of(), "conv", "model").startsWith("Error:"));
      server.verify();
    }
    assertTrue(client.call("shell", Map.of(), "conv", "model").startsWith("Error:"));
  }

  @Test
  void treatsMcpToolErrorsAndMissingEvidenceAsFailure() {
    for (String body :
        List.of("{\"id\":2,\"result\":{\"isError\":true}}", "{\"id\":2,\"result\":{}}")) {
      server.reset();
      initialize(false);
      server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
      String output = client.call("web_fetch", Map.of(), "conv", "model");
      assertTrue(output.startsWith("Error:") || output.startsWith("No results"));
      server.verify();
    }
  }
}
