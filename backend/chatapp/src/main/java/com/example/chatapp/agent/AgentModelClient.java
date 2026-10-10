package com.example.chatapp.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** Strict, time-bounded Ollama transport; errors are never converted into successful replies. */
@Component
public class AgentModelClient {
  private final RestTemplate client;
  private final String url;
  private final ObjectMapper mapper = new ObjectMapper();

  public AgentModelClient(@Value("${ollama.api.url:http://localhost:11434}") final String baseUrl) {
    final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(10_000);
    factory.setReadTimeout(180_000);
    client = new RestTemplate(factory);
    url = baseUrl + "/api/chat";
  }

  public JsonNode chat(
      final String model,
      final List<Map<String, Object>> messages,
      final Map<String, Object> options,
      final List<Map<String, Object>> tools) {
    final int contextSize = ((Number) options.getOrDefault("num_ctx", 16_384)).intValue();
    final int outputSize = ((Number) options.getOrDefault("num_predict", 2_048)).intValue();
    final int inputChars =
        messages.stream()
            .mapToInt(row -> String.valueOf(row.getOrDefault("content", "")).length())
            .sum();
    if (inputChars / 3 + outputSize > contextSize)
      throw new IllegalStateException(
          "The task evidence exceeds the selected context size. Increase context size or use smaller files and a narrower request.");
    final Map<String, Object> payload = new java.util.HashMap<>();
    payload.put("model", model);
    payload.put("messages", messages);
    payload.put("options", options);
    payload.put("stream", false);
    payload.put("think", false);
    if (!tools.isEmpty()) payload.put("tools", tools);
    final String body;
    try {
      body = client.postForObject(url, payload, String.class);
    } catch (org.springframework.web.client.HttpClientErrorException.NotFound exception) {
      throw new IllegalStateException(
          "The selected model is not available in Ollama. Choose an installed model.", exception);
    } catch (org.springframework.web.client.ResourceAccessException exception) {
      throw new IllegalStateException(
          "The model is unavailable or timed out. Check Ollama and retry.", exception);
    }
    try {
      final JsonNode response = mapper.readTree(body == null ? "{}" : body);
      if (response.has("error") || !response.path("message").isObject()) {
        throw new IllegalStateException("The model returned an invalid response.");
      }
      if ("length".equals(response.path("done_reason").asText())) {
        throw new IllegalStateException(
            "The model reached its output limit. Try a narrower request.");
      }
      return response.get("message");
    } catch (java.io.IOException exception) {
      throw new IllegalStateException("The model returned malformed JSON.", exception);
    }
  }
}
