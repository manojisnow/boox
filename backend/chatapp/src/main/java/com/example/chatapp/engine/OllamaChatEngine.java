package com.example.chatapp.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Profile("!contract-test")
@Component("ollama")
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP2"},
    justification = "Spring-managed beans are not exposed outside this class; safe for DI usage.")
public class OllamaChatEngine implements ChatEngine {
  private final RestTemplate restTemplate;
  private final ChatContextService chatContextService;
  private static final Logger LOGGER = LoggerFactory.getLogger(OllamaChatEngine.class);

  @Value("${ollama.api.url}")
  private String ollamaApiUrl;

  @Value("${ollama.api.tags-path}")
  private String tagsPath;

  @Value("${ollama.api.chat-path}")
  private String chatPath;

  @Value("${ollama.api.temperature}")
  private double temperature;

  @Value("${ollama.model.description}")
  private String modelDescription;

  @SuppressWarnings({"PMD.LawOfDemeter", "PMD.LongVariable"})
  public OllamaChatEngine(
      final RestTemplate restTemplate, final ChatContextService chatContextService) {
    // These are Spring-managed beans, not exposed outside this class, so SpotBugs EI_EXPOSE_REP2 is
    // a false positive.
    // If you want to be extra strict, you could wrap with a proxy or unmodifiable, but for
    // stateless beans this is safe.
    this.restTemplate = restTemplate;
    this.chatContextService = chatContextService;
    // Suppress SpotBugs EI_EXPOSE_REP2: Beans are not exposed via getters/setters.
  }

  public static class Constants {
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_USER = "user";
    public static final String CONTENT = "content";
    public static final String MESSAGE = "message";
    public static final String NO_CONTENT = "No content";
    public static final String NO_RESPONSE = "No response";
    public static final String NO_VALID_RESPONSE = "No valid response";
    public static final String SERVER_NOT_RUNNING = "Server not running";
    public static final String ERROR_FETCHING_MODELS = "Error fetching models";
    public static final String ERROR_COMMUNICATING = "Error communicating with Ollama API";
  }

  private boolean isOllamaRunning() {
    try {
      final String url = ollamaApiUrl + tagsPath;
      restTemplate.getForEntity(url, Map.class);
      return true;
    } catch (final Exception e) {
      LOGGER.warn("Ollama server is not running: {}", e.getMessage());
      return false;
    }
  }

  private List<ModelInfo> fetchModelsFromOllama()
      throws java.io.IOException, com.fasterxml.jackson.core.JsonProcessingException {
    final String url = ollamaApiUrl + tagsPath;
    final ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
    final Map body = response.getBody();
    if (body != null && body.containsKey("models")) {
      final List<Map<String, Object>> models = (List<Map<String, Object>>) body.get("models");
      return models.stream()
          .map(m -> new ModelInfo((String) m.get("name"), modelDescription))
          .collect(Collectors.toList());
    }
    return Collections.emptyList();
  }

  private Map<String, String> parseOllamaResponse(final Map body) {
    if (body != null && body.containsKey(Constants.MESSAGE)) {
      final Map messageObj = (Map) body.get(Constants.MESSAGE);
      final String role = messageObj.getOrDefault("role", Constants.ROLE_ASSISTANT).toString();
      final String content =
          messageObj.getOrDefault(Constants.CONTENT, Constants.NO_CONTENT).toString();
      return Map.of("role", role, Constants.CONTENT, content);
    }
    return Map.of("role", Constants.ROLE_ASSISTANT, Constants.CONTENT, Constants.NO_RESPONSE);
  }

  private Map<String, String> parseStreamedOllamaResponse(final String body) {
    final ObjectMapper mapper = new ObjectMapper();
    final String[] lines = body.split("\r?\n");
    final StringBuilder fullContent = new StringBuilder();
    String role = Constants.ROLE_ASSISTANT;
    for (final String lineRaw : lines) {
      String line = lineRaw.trim();
      if (line.isEmpty()) continue;
      try {
        final Map json = mapper.readValue(line, Map.class);
        if (json.get("done") != null && Boolean.TRUE.equals(json.get("done"))) {
          break;
        }
        final Map msgObj = (Map) json.get(Constants.MESSAGE);
        if (msgObj != null && msgObj.get(Constants.CONTENT) != null) {
          final String chunk = msgObj.get(Constants.CONTENT).toString();
          if (fullContent.length() > 0
              && !fullContent.toString().endsWith("\n")
              && !chunk.startsWith("\n")) {
            fullContent.append("\n");
          }
          fullContent.append(chunk);
          if (msgObj.get("role") != null) {
            role = msgObj.get("role").toString();
          }
        }
      } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        LOGGER.debug("Malformed line in streamed response: {}", line);
      }
    }
    if (fullContent.length() == 0) {
      return Map.of(
          "role", Constants.ROLE_ASSISTANT, Constants.CONTENT, Constants.NO_VALID_RESPONSE);
    }
    return Map.of("role", role, Constants.CONTENT, fullContent.toString());
  }

  @Override
  public List<ModelInfo> getModels() {
    if (!isOllamaRunning()) {
      LOGGER.warn("Ollama server is not running when fetching models.");
      return List.of(new ModelInfo(Constants.SERVER_NOT_RUNNING, ""));
    }
    try {
      return fetchModelsFromOllama();
    } catch (final Exception e) {
      LOGGER.error("Error fetching models from Ollama: {}", e.getMessage(), e);
      return List.of(new ModelInfo(Constants.ERROR_FETCHING_MODELS, ""));
    }
  }

  @SuppressFBWarnings(
      value = {"REC_CATCH_EXCEPTION"},
      justification =
          "Broad exception handling is intentional for robust error reporting in a service/controller boundary.")
  @Override
  public Map<String, String> sendMessage(
      final String message, final String model, final String sessionId, final boolean stream) {
    try {
      if (!isOllamaRunning()) {
        LOGGER.warn("Ollama server is not running when sending message.");
        return Map.of(
            "role", Constants.ROLE_ASSISTANT, Constants.CONTENT, Constants.SERVER_NOT_RUNNING);
      }
      final String url = ollamaApiUrl + chatPath;
      chatContextService.addMessage(sessionId, Constants.ROLE_USER, message);
      final List<Map<String, String>> context = chatContextService.getContext(sessionId);
      final Map<String, Object> payload = new HashMap<>();
      payload.put("model", model);
      payload.put("messages", context);
      payload.put("temperature", temperature);
      payload.put("stream", stream);

      final HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      final HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

      final ResponseEntity<String> response =
          restTemplate.exchange(url, HttpMethod.POST, request, String.class);
      final String body = response.getBody();
      if (body == null || body.isEmpty()) {
        LOGGER.warn("No response from Ollama for chat request.");
        return Map.of("role", Constants.ROLE_ASSISTANT, Constants.CONTENT, Constants.NO_RESPONSE);
      }
      if (!stream) {
        final ObjectMapper mapper = new ObjectMapper();
        final Map json = mapper.readValue(body, Map.class);
        final Map<String, String> reply = parseOllamaResponse(json);
        chatContextService.addMessage(sessionId, reply.get("role"), reply.get(Constants.CONTENT));
        return reply;
      } else {
        final Map<String, String> reply = parseStreamedOllamaResponse(body);
        chatContextService.addMessage(sessionId, reply.get("role"), reply.get(Constants.CONTENT));
        return reply;
      }
    } catch (Exception e) {
      LOGGER.error("Error communicating with Ollama: {}", e.getMessage(), e);
      return Map.of(
          "role", Constants.ROLE_ASSISTANT, Constants.CONTENT, Constants.ERROR_COMMUNICATING);
    }
  }

  @Override
  public void resetContext(String sessionId) {
    chatContextService.resetContext(sessionId);
  }
}
