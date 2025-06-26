package com.example.chatapp.engine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("ollama")
public class OllamaChatEngine implements ChatEngine {
    private final RestTemplate restTemplate;
    private final ChatContextService chatContextService;
    private static final Logger logger = LoggerFactory.getLogger(OllamaChatEngine.class);

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

    public OllamaChatEngine(RestTemplate restTemplate, ChatContextService chatContextService) {
        this.restTemplate = restTemplate;
        this.chatContextService = chatContextService;
    }

    private static class Constants {
        static final String ROLE_ASSISTANT = "assistant";
        static final String ROLE_USER = "user";
        static final String CONTENT = "content";
        static final String MESSAGE = "message";
        static final String NO_CONTENT = "(No content)";
        static final String NO_RESPONSE = "(No response from Ollama)";
        static final String NO_VALID_RESPONSE = "(No valid response from Ollama)";
        static final String SERVER_NOT_RUNNING = "(Ollama server is not running)";
        static final String ERROR_FETCHING_MODELS = "(error fetching models)";
        static final String ERROR_COMMUNICATING = "(Error communicating with Ollama)";
    }

    private boolean isOllamaRunning() {
        try {
            String url = ollamaApiUrl + tagsPath;
            restTemplate.getForEntity(url, Map.class);
            return true;
        } catch (Exception e) {
            logger.warn("Ollama server is not running: {}", e.getMessage());
            return false;
        }
    }

    private List<ModelInfo> fetchModelsFromOllama() throws Exception {
        String url = ollamaApiUrl + tagsPath;
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        Map body = response.getBody();
        if (body != null && body.containsKey("models")) {
            List<Map<String, Object>> models = (List<Map<String, Object>>) body.get("models");
            return models.stream()
                .map(m -> new ModelInfo((String) m.get("name"), modelDescription))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private Map<String, String> parseOllamaResponse(Map body) {
        if (body != null && body.containsKey(Constants.MESSAGE)) {
            Map messageObj = (Map) body.get(Constants.MESSAGE);
            String role = messageObj.getOrDefault("role", Constants.ROLE_ASSISTANT).toString();
            String content = messageObj.getOrDefault(Constants.CONTENT, Constants.NO_CONTENT).toString();
            return Map.of("role", role, Constants.CONTENT, content);
        }
        return Map.of("role", Constants.ROLE_ASSISTANT, Constants.CONTENT, Constants.NO_RESPONSE);
    }

    private Map<String, String> parseStreamedOllamaResponse(String body) {
        ObjectMapper mapper = new ObjectMapper();
        String[] lines = body.split("\\r?\\n");
        StringBuilder fullContent = new StringBuilder();
        String role = Constants.ROLE_ASSISTANT;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            try {
                Map json = mapper.readValue(line, Map.class);
                if (json.get("done") != null && Boolean.TRUE.equals(json.get("done"))) {
                    break;
                }
                Map msgObj = (Map) json.get(Constants.MESSAGE);
                if (msgObj != null && msgObj.get(Constants.CONTENT) != null) {
                    String chunk = msgObj.get(Constants.CONTENT).toString();
                    if (fullContent.length() > 0 && !fullContent.toString().endsWith("\n") && !chunk.startsWith("\n")) {
                        fullContent.append("\n");
                    }
                    fullContent.append(chunk);
                    if (msgObj.get("role") != null) {
                        role = msgObj.get("role").toString();
                    }
                }
            } catch (Exception e) {
                logger.debug("Malformed line in streamed response: {}", line);
            }
        }
        if (fullContent.length() == 0) {
            return Map.of("role", Constants.ROLE_ASSISTANT, Constants.CONTENT, Constants.NO_VALID_RESPONSE);
        }
        return Map.of("role", role, Constants.CONTENT, fullContent.toString());
    }

    @Override
    public List<ModelInfo> getModels() {
        if (!isOllamaRunning()) {
            logger.warn("Ollama server is not running when fetching models.");
            return List.of(new ModelInfo(Constants.SERVER_NOT_RUNNING, ""));
        }
        try {
            return fetchModelsFromOllama();
        } catch (Exception e) {
            logger.error("Error fetching models from Ollama: {}", e.getMessage(), e);
            return List.of(new ModelInfo(Constants.ERROR_FETCHING_MODELS, ""));
        }
    }

    @Override
    public Map<String, String> sendMessage(String message, String model, String sessionId, boolean stream) {
        if (!isOllamaRunning()) {
            logger.warn("Ollama server is not running when sending message.");
            return Map.of("role", Constants.ROLE_ASSISTANT, Constants.CONTENT, Constants.SERVER_NOT_RUNNING);
        }
        try {
            String url = ollamaApiUrl + chatPath;
            chatContextService.addMessage(sessionId, Constants.ROLE_USER, message);
            List<Map<String, String>> context = chatContextService.getContext(sessionId);
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", model);
            payload.put("messages", context);
            payload.put("temperature", temperature);
            payload.put("stream", stream);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, request, String.class);
            String body = response.getBody();
            if (body == null || body.isEmpty()) {
                logger.warn("No response from Ollama for chat request.");
                return Map.of("role", Constants.ROLE_ASSISTANT, Constants.CONTENT, Constants.NO_RESPONSE);
            }
            if (!stream) {
                ObjectMapper mapper = new ObjectMapper();
                Map json = mapper.readValue(body, Map.class);
                Map<String, String> reply = parseOllamaResponse(json);
                chatContextService.addMessage(sessionId, reply.get("role"), reply.get(Constants.CONTENT));
                return reply;
            } else {
                Map<String, String> reply = parseStreamedOllamaResponse(body);
                chatContextService.addMessage(sessionId, reply.get("role"), reply.get(Constants.CONTENT));
                return reply;
            }
        } catch (Exception e) {
            logger.error("Error communicating with Ollama: {}", e.getMessage(), e);
            return Map.of("role", Constants.ROLE_ASSISTANT, Constants.CONTENT, Constants.ERROR_COMMUNICATING);
        }
    }

    @Override
    public void resetContext(String sessionId) {
        chatContextService.resetContext(sessionId);
    }
}

