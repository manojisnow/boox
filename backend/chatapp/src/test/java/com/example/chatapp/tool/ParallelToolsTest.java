package com.example.chatapp.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ParallelToolsTest {
  private final ParallelWebClient client = mock(ParallelWebClient.class);
  private final ParallelSearchTool search = new ParallelSearchTool(client);
  private final WebFetchTool fetch = new WebFetchTool(client);

  @Test
  void forwardsOnlyValidatedArgumentsAndTrustedRuntimeContext() {
    when(client.call(any(), any(), any(), any())).thenReturn("evidence");
    assertEquals(
        "evidence",
        search.execute(
            Map.of("query", "Spring Boot stable", "session_id", "forged"), "conv", "model"));
    verify(client)
        .call(
            "web_search",
            Map.of(
                "objective", "Spring Boot stable", "search_queries", List.of("Spring Boot stable")),
            "conv",
            "model");
    assertEquals(
        "evidence",
        fetch.execute(
            Map.of(
                "urls", List.of("https://docs.example.org"), "objective", "Current requirements"),
            "conv",
            "model"));
    verify(client)
        .call(
            "web_fetch",
            Map.of(
                "urls",
                List.of("https://docs.example.org"),
                "objective",
                "Current requirements",
                "full_content",
                false,
                "allow_live_fetch",
                true),
            "conv",
            "model");
    assertEquals("web_search", search.getName());
    assertEquals("web_fetch", fetch.getName());
    assertFalse(search.getDescription().isBlank());
    assertFalse(fetch.getDescription().isBlank());
    assertEquals(List.of("query"), search.getParameterSchema().get("required"));
    assertEquals(List.of("urls"), fetch.getParameterSchema().get("required"));
  }

  @Test
  void rejectsMalformedInputsAndAbsentContextBeforeNetwork() {
    for (Map<String, Object> args :
        List.of(
            Map.<String, Object>of(),
            Map.<String, Object>of("query", ""),
            Map.<String, Object>of("query", 4),
            Map.<String, Object>of("query", "x".repeat(1_001))))
      assertTrue(search.execute(args, "conv", "model").startsWith("Error:"));
    for (Map<String, Object> args :
        List.of(
            Map.<String, Object>of(),
            Map.<String, Object>of("urls", List.of()),
            Map.<String, Object>of("urls", List.of("http://localhost")),
            Map.<String, Object>of("urls", List.of(4)),
            Map.<String, Object>of(
                "urls", List.of("https://example.org"), "objective", "x".repeat(201)),
            Map.<String, Object>of("urls", List.of("https://example.org"), "objective", 4),
            Map.<String, Object>of("urls", List.of("a", "b", "c", "d"))))
      assertTrue(fetch.execute(args, "conv", "model").startsWith("Error:"));
    assertTrue(search.execute(Map.of("query", "facts")).startsWith("Error:"));
    assertTrue(fetch.execute(Map.of("urls", List.of("https://example.org"))).startsWith("Error:"));
    verifyNoInteractions(client);
  }
}
