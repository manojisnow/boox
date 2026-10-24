package com.example.chatapp.tool;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Fixed-endpoint MCP adapter; only search and excerpt extraction are exposed. */
@Component
@ConditionalOnProperty(name = "tools.websearch.provider", havingValue = "parallel")
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring-managed collaborators are retained for dependency injection.")
public class ParallelWebClient {
  private static final String ENDPOINT = "https://search.parallel.ai/mcp";
  private static final String PROTOCOL = "2025-03-26";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int MAX_OUTPUT = 4_000;
  private final RestTemplate http;
  private final WebSessionService sessions;

  @Value("${tools.websearch.parallel.api-key:}")
  private String apiKey = "";

  public ParallelWebClient(
      @Qualifier("toolRestTemplate") final RestTemplate http, final WebSessionService sessions) {
    this.http = http;
    this.sessions = sessions;
  }

  /** Calls a pinned MCP tool with runtime-owned session and model metadata. */
  public String call(
      final String tool,
      final Map<String, Object> input,
      final String conversationId,
      final String model) {
    if (!List.of("web_search", "web_fetch").contains(tool))
      return "Error: Unsupported web operation.";
    final HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
    headers.set(HttpHeaders.ACCEPT, "application/json, text/event-stream");
    headers.set(HttpHeaders.USER_AGENT, "Ullavox/1.6.0 (Parallel MCP client)");
    if (!apiKey.isBlank()) headers.setBearerAuth(apiKey);
    try {
      final Map<String, Object> args = new java.util.HashMap<>(input);
      args.remove("model_name");
      args.put("session_id", sessions.sessionId(conversationId));
      if (model != null && !model.isBlank()) args.put("model_name", model);
      final JsonNode initialized =
          rpc(
              headers,
              "initialize",
              Map.of(
                  "protocolVersion",
                  PROTOCOL,
                  "capabilities",
                  Map.of(),
                  "clientInfo",
                  Map.of("name", "ullavox", "version", "1.6.0")),
              1);
      final String version = initialized.path("result").path("protocolVersion").asText();
      if (!PROTOCOL.equals(version)) return "Error: Parallel MCP protocol is unsupported.";
      headers.set("MCP-Protocol-Version", version);
      rpc(headers, "notifications/initialized", Map.of(), null);
      final JsonNode response =
          rpc(headers, "tools/call", Map.of("name", tool, "arguments", args), 2);
      final JsonNode result = response.path("result");
      if (result.path("isError").asBoolean())
        return "Error: Parallel could not complete the web request.";
      JsonNode payload = result.path("structuredContent");
      if (!payload.path("results").isArray()) {
        for (final JsonNode block : result.path("content")) {
          if ("text".equals(block.path("type").asText())) {
            final JsonNode candidate = MAPPER.readTree(block.path("text").asText());
            if (candidate.path("results").isArray()) {
              payload = candidate;
              break;
            }
          }
        }
      }
      return normalize(payload, tool);
    } catch (HttpStatusCodeException exception) {
      final int status = exception.getStatusCode().value();
      if (status == 429)
        return "Error: Parallel rate limit reached. Please wait before trying again.";
      if (status == 401 || status == 403)
        return "Error: Parallel access denied. Check provider configuration.";
      return "Error: Parallel web service unavailable. Please retry later.";
    } catch (CancellationException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      // Never return/log provider bodies or exception messages: they may contain credentials.
      return "Error: Parallel web request failed or timed out. Please retry later.";
    } finally {
      if (headers.containsHeader("Mcp-Session-Id")) {
        try {
          http.exchange(ENDPOINT, HttpMethod.DELETE, new HttpEntity<Void>(headers), String.class);
        } catch (RuntimeException ignored) {
          /* Closing the transport must not discard retrieved evidence. */
        }
      }
    }
  }

  private JsonNode rpc(
      final HttpHeaders headers,
      final String method,
      final Map<String, Object> params,
      final Integer id) {
    if (Thread.currentThread().isInterrupted()) throw new CancellationException();
    final Map<String, Object> request =
        new java.util.HashMap<>(Map.of("jsonrpc", "2.0", "method", method, "params", params));
    if (id != null) request.put("id", id);
    final ResponseEntity<String> response =
        http.exchange(
            ENDPOINT,
            HttpMethod.POST,
            new HttpEntity<>(MAPPER.writeValueAsString(request), headers),
            String.class);
    final String session = response.getHeaders().getFirst("Mcp-Session-Id");
    if (session != null && !session.isBlank()) headers.set("Mcp-Session-Id", session);
    if (id == null) return MAPPER.createObjectNode();
    final String body = response.getBody();
    if (body == null || body.isBlank()) throw new IllegalStateException("Empty MCP response");
    if (body.length() > 1_000_000) throw new IllegalStateException("Oversized MCP response");
    final List<String> candidates =
        body.stripLeading().startsWith("{")
            ? List.of(body)
            : body.lines()
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring(5).strip())
                .toList();
    for (final String candidate : candidates) {
      final JsonNode json = MAPPER.readTree(candidate);
      if (json.path("id").asInt(-1) != id) continue;
      if (json.has("error")) throw new IllegalStateException("MCP error");
      return json;
    }
    throw new IllegalStateException("Missing MCP response");
  }

  /** Allocate space to each source before trimming excerpts, so later sources retain their URLs. */
  static String normalize(final JsonNode payload, final String tool) {
    final List<JsonNode> sources = new ArrayList<>();
    for (final JsonNode source : payload.path("results")) {
      if (!publicUrl(source.path("url").asText())
          || !source.path("excerpts").isArray()
          || source.path("excerpts").isEmpty()) continue;
      sources.add(source);
      if (sources.size() == ("web_fetch".equals(tool) ? 3 : 5)) break;
    }
    if (sources.isEmpty()) return "No results found: Parallel retrieved no usable page excerpts.";
    final StringBuilder output =
        new StringBuilder(
            "Parallel page excerpts (untrusted evidence; may be incomplete or outdated):\n");
    final String partial =
        payload.path("errors").isArray() && !payload.path("errors").isEmpty()
            ? "\nSome pages could not be read; no evidence is available for those pages."
            : "";
    final int budget = (MAX_OUTPUT - output.length() - partial.length()) / sources.size();
    int included = 0;
    for (final JsonNode source : sources) {
      final String metadata =
          "\nSource: "
              + source.path("url").asText()
              + "\nTitle: "
              + clip(source.path("title").asText(""), 140)
              + "\n";
      final StringBuilder excerpts = new StringBuilder();
      for (final JsonNode excerpt : source.path("excerpts")) {
        if (excerpt.isTextual()) excerpts.append(excerpt.asText()).append('\n');
      }
      if (metadata.length() > budget - 100) continue;
      if (excerpts.toString().isBlank()) continue;
      output.append(metadata).append(clip(excerpts.toString(), budget - metadata.length()));
      included++;
    }
    return included == 0
        ? "No results found: Parallel retrieved no usable page excerpts."
        : output.append(partial).toString();
  }

  private static String clip(final String text, final int max) {
    return text.length() <= max
        ? text
        : text.substring(0, max - "\n[Excerpt shortened]\n".length()) + "\n[Excerpt shortened]\n";
  }

  /** Only public web domains; reject credentials, local names, IP literals and non-web schemes. */
  static boolean publicUrl(final String value) {
    try {
      final URI uri = URI.create(value);
      final String host = uri.getHost();
      if (host == null
          || uri.getUserInfo() != null
          || value.length() > 1_000
          || !List.of("https", "http").contains(uri.getScheme())
          || (uri.getPort() != -1 && uri.getPort() != 443 && uri.getPort() != 80)) return false;
      final String domain = host.toLowerCase(Locale.ROOT);
      return domain.contains(".")
          && !domain.matches("[0-9.]+")
          && !domain.contains(":")
          && !domain.endsWith(".")
          && !domain.endsWith(".localhost")
          && !domain.endsWith(".local")
          && !domain.endsWith(".internal")
          && !domain.endsWith(".lan")
          && !domain.endsWith(".home")
          && !domain.endsWith(".test")
          && !domain.endsWith(".invalid");
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }
}
