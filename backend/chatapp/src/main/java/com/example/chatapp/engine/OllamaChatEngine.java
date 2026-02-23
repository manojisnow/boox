package com.example.chatapp.engine;

import com.example.chatapp.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Profile("!contract-test")
@Component("ollama")
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP2"},
    justification = "Spring-managed beans are not exposed outside this class; safe for DI usage.")
public class OllamaChatEngine implements ChatEngine {
  private static final Logger LOGGER = LoggerFactory.getLogger(OllamaChatEngine.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int MAX_TOOL_ITERATIONS = 5;

  private final RestTemplate restTemplate;
  private final ChatContextService chatContextService;
  private final ToolRegistry toolRegistry;

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
      final RestTemplate restTemplate,
      final ChatContextService chatContextService,
      final ToolRegistry toolRegistry) {
    this.restTemplate = restTemplate;
    this.chatContextService = chatContextService;
    this.toolRegistry = toolRegistry;
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

  /**
   * Tool-use hint prepended to every system message when tools are registered. Guides smaller
   * models (e.g. llama3.2 3B) that otherwise echo the parameter schema as argument values.
   */
  private static final String TOOL_USAGE_HINT =
      "You have access to tools, but you must only use them when truly necessary.\n"
          + "Do NOT call any tool for: greetings, small talk, simple facts, math, coding"
          + " questions, or anything you can answer confidently from your training data.\n"
          + "Only call a tool (e.g. web_search) when the user explicitly needs current,"
          + " real-time, or external information that you cannot reliably provide yourself.\n"
          + "When you do call a tool, always supply plain scalar values as arguments — never"
          + " the schema definition. Example: {\"query\": \"your actual search terms\"},"
          + " not {\"query\": {\"type\": \"string\", \"description\": \"...\"}}.\n";

  private List<Map<String, String>> buildMessagesWithSystemPrompt(final String sessionId) {
    final List<Map<String, String>> context = chatContextService.getContext(sessionId);
    final String userSystemPrompt = chatContextService.getSystemPrompt(sessionId);

    final StringBuilder systemContent = new StringBuilder();
    if (toolRegistry.hasTools()) {
      systemContent.append(TOOL_USAGE_HINT);
    }
    if (userSystemPrompt != null && !userSystemPrompt.isEmpty()) {
      systemContent.append(userSystemPrompt);
    }

    if (systemContent.length() > 0) {
      final List<Map<String, String>> messages = new ArrayList<>();
      messages.add(Map.of("role", "system", Constants.CONTENT, systemContent.toString()));
      messages.addAll(context);
      return messages;
    }
    return context;
  }

  private Optional<Map<String, Object>> callOllamaWithTools(
      final String model, final List<Map<String, ?>> messages, final boolean stream)
      throws com.fasterxml.jackson.core.JsonProcessingException, java.io.IOException {
    final String url = ollamaApiUrl + chatPath;
    final Map<String, Object> payload = new HashMap<>();
    payload.put("model", model);
    payload.put("messages", messages);
    payload.put("temperature", temperature);
    payload.put("stream", stream);
    if (toolRegistry.hasTools()) {
      payload.put("tools", toolRegistry.getToolDefinitions());
    }

    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    final HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

    final ResponseEntity<String> response =
        restTemplate.exchange(url, HttpMethod.POST, request, String.class);
    final String body = response.getBody();
    if (body == null || body.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(MAPPER.readValue(body, Map.class));
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> extractToolCalls(final Map<String, Object> responseJson) {
    if (responseJson == null) {
      return Collections.emptyList();
    }
    final Map<String, Object> msg = (Map<String, Object>) responseJson.get(Constants.MESSAGE);
    if (msg == null) {
      return Collections.emptyList();
    }
    final Object toolCalls = msg.get("tool_calls");
    if (toolCalls instanceof List) {
      return (List<Map<String, Object>>) toolCalls;
    }
    return Collections.emptyList();
  }

  /**
   * Resolves the {@code arguments} field of a tool call function object into a usable Map.
   *
   * <p>Ollama may return {@code arguments} either as a pre-parsed JSON object (Map) or as a
   * JSON-encoded string. Both cases are handled here so the rest of the tool-call pipeline always
   * receives a {@code Map<String, Object>}.
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> resolveArguments(final Map<String, Object> function) {
    final Object raw = function.getOrDefault("arguments", Map.of());
    if (raw instanceof Map) {
      return (Map<String, Object>) raw;
    }
    if (raw instanceof String) {
      final String json = ((String) raw).trim();
      if (!json.isEmpty()) {
        try {
          return MAPPER.readValue(json, Map.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
          LOGGER.warn("Could not parse tool arguments as JSON: {}", json);
        }
      }
    }
    return Map.of();
  }

  /**
   * Runs the tool-use loop until no tool calls remain or MAX_TOOL_ITERATIONS is reached.
   *
   * <p>When {@code emitter} is non-null, emits {@code tool_call} and {@code tool_result} named SSE
   * events for each tool call so the frontend can render live progress.
   *
   * @return the final Ollama response (no tool_calls present), or empty if the loop terminated
   *     without a final response (null body or max iterations reached)
   */
  @SuppressWarnings("unchecked")
  private Optional<Map<String, Object>> executeToolLoop(
      final String model, final String sessionId, final SseEmitter emitter)
      throws IOException, com.fasterxml.jackson.core.JsonProcessingException {
    int toolIndex = 0;
    for (int i = 0; i < MAX_TOOL_ITERATIONS; i++) {
      final List<Map<String, ?>> msgs = new ArrayList<>(buildMessagesWithSystemPrompt(sessionId));
      final Optional<Map<String, Object>> responseOpt = callOllamaWithTools(model, msgs, false);
      if (responseOpt.isEmpty()) {
        return Optional.empty();
      }
      final Map<String, Object> responseJson = responseOpt.get();
      final List<Map<String, Object>> toolCalls = extractToolCalls(responseJson);
      if (toolCalls.isEmpty()) {
        // No tool calls — this is the final response ready for returning/streaming
        return Optional.of(responseJson);
      }

      // Save the assistant's tool-calling message to context
      final Map<String, Object> assistantMsg =
          (Map<String, Object>) responseJson.get(Constants.MESSAGE);
      chatContextService.addMessage(
          sessionId,
          Constants.ROLE_ASSISTANT,
          assistantMsg.getOrDefault(Constants.CONTENT, "").toString());

      // Execute each tool call
      for (final Map<String, Object> toolCall : toolCalls) {
        final Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
        final String toolName = (String) function.get("name");
        final Map<String, Object> args = resolveArguments(function);
        final int currentIndex = toolIndex++;

        if (emitter != null) {
          emitter.send(
              SseEmitter.event()
                  .name("tool_call")
                  .data(
                      MAPPER.writeValueAsString(
                          Map.of("name", toolName, "arguments", args, "index", currentIndex))));
        }

        LOGGER.info("Tool call: {} with args: {}", toolName, args);
        final String result = toolRegistry.executeTool(toolName, args);
        chatContextService.addMessage(sessionId, "tool", result);

        if (emitter != null) {
          emitter.send(
              SseEmitter.event()
                  .name("tool_result")
                  .data(
                      MAPPER.writeValueAsString(
                          Map.of("name", toolName, "result", result, "index", currentIndex))));
        }
      }
    }
    return Optional.empty(); // max iterations reached
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
      chatContextService.addMessage(sessionId, Constants.ROLE_USER, message);

      final Optional<Map<String, Object>> finalResponse = executeToolLoop(model, sessionId, null);
      if (finalResponse.isEmpty()) {
        return Map.of("role", Constants.ROLE_ASSISTANT, Constants.CONTENT, Constants.NO_RESPONSE);
      }
      final Map<String, String> reply = parseOllamaResponse(finalResponse.get());
      chatContextService.addMessage(sessionId, reply.get("role"), reply.get(Constants.CONTENT));
      return reply;
    } catch (Exception e) {
      LOGGER.error("Error communicating with Ollama: {}", e.getMessage(), e);
      return Map.of(
          "role", Constants.ROLE_ASSISTANT, Constants.CONTENT, Constants.ERROR_COMMUNICATING);
    }
  }

  @SuppressFBWarnings(
      value = {"REC_CATCH_EXCEPTION"},
      justification =
          "Broad exception handling is intentional for robust error reporting in SSE streaming.")
  @Override
  public void streamMessage(
      final String message, final String model, final String sessionId, final SseEmitter emitter) {
    try {
      if (!isOllamaRunning()) {
        LOGGER.warn("Ollama server is not running when streaming message.");
        emitter.send(SseEmitter.event().data(Constants.SERVER_NOT_RUNNING));
        emitter.send(SseEmitter.event().data("[DONE]"));
        emitter.complete();
        return;
      }
      chatContextService.addMessage(sessionId, Constants.ROLE_USER, message);

      final Optional<Map<String, Object>> finalResponse =
          executeToolLoop(model, sessionId, emitter);
      if (finalResponse.isEmpty()) {
        emitter.send(SseEmitter.event().data(Constants.NO_RESPONSE));
        emitter.send(SseEmitter.event().data("[DONE]"));
        emitter.complete();
        return;
      }
      streamFinalResponse(model, sessionId, emitter);
    } catch (Exception e) {
      LOGGER.error("Error streaming from Ollama: {}", e.getMessage(), e);
      try {
        emitter.send(SseEmitter.event().data(Constants.ERROR_COMMUNICATING));
        emitter.send(SseEmitter.event().data("[DONE]"));
        emitter.complete();
      } catch (Exception inner) {
        emitter.completeWithError(inner);
      }
    }
  }

  private void streamFinalResponse(
      final String model, final String sessionId, final SseEmitter emitter)
      throws IOException, com.fasterxml.jackson.core.JsonProcessingException {
    final String url = ollamaApiUrl + chatPath;
    final List<Map<String, String>> messagesForOllama = buildMessagesWithSystemPrompt(sessionId);
    final Map<String, Object> payload = new HashMap<>();
    payload.put("model", model);
    payload.put("messages", messagesForOllama);
    payload.put("temperature", temperature);
    payload.put("stream", true);

    final StringBuilder fullContent = new StringBuilder();

    restTemplate.execute(
        url,
        HttpMethod.POST,
        req -> {
          req.getHeaders().setContentType(MediaType.APPLICATION_JSON);
          MAPPER.writeValue(req.getBody(), payload);
        },
        response -> {
          try (BufferedReader reader =
              new BufferedReader(
                  new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
              line = line.trim();
              if (line.isEmpty()) {
                continue;
              }
              try {
                final Map json = MAPPER.readValue(line, Map.class);
                if (json.get("done") != null && Boolean.TRUE.equals(json.get("done"))) {
                  break;
                }
                final Map msgObj = (Map) json.get(Constants.MESSAGE);
                if (msgObj != null && msgObj.get(Constants.CONTENT) != null) {
                  final String chunk = msgObj.get(Constants.CONTENT).toString();
                  fullContent.append(chunk);
                  emitter.send(SseEmitter.event().data(chunk));
                }
              } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                LOGGER.debug("Malformed line in streamed response: {}", line);
              }
            }
          }
          return null;
        });

    chatContextService.addMessage(sessionId, Constants.ROLE_ASSISTANT, fullContent.toString());
    emitter.send(SseEmitter.event().data("[DONE]"));
    emitter.complete();
  }

  @Override
  public void resetContext(String sessionId) {
    chatContextService.resetContext(sessionId);
  }
}
