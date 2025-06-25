package com.example.chatapp.engine;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;

@Component("ollama")
public class OllamaChatEngine implements ChatEngine {
    private final RestTemplate restTemplate;
    private final ChatContextService chatContextService;

    @Value("${ollama.api.url}")
    private String ollamaApiUrl;

    public OllamaChatEngine(RestTemplate restTemplate, ChatContextService chatContextService) {
        this.restTemplate = restTemplate;
        this.chatContextService = chatContextService;
    }

    private boolean isOllamaRunning() {
        try {
            String url = ollamaApiUrl + "/api/tags";
            restTemplate.getForEntity(url, Map.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, String> parseOllamaResponse(Map body) {
        if (body != null && body.containsKey("message")) {
            Map messageObj = (Map) body.get("message");
            String role = messageObj.getOrDefault("role", "assistant").toString();
            String content = messageObj.getOrDefault("content", "(No content)").toString();
            return Map.of("role", role, "content", content);
        }
        return Map.of("role", "assistant", "content", "(No response from Ollama)");
    }

    @Override
    public List<ModelInfo> getModels() {
        if (!isOllamaRunning()) {
            return List.of(new ModelInfo("(Ollama server is not running)", ""));
        }
        try {
            String url = ollamaApiUrl + "/api/tags";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map body = response.getBody();
            if (body != null && body.containsKey("models")) {
                List<Map<String, Object>> models = (List<Map<String, Object>>) body.get("models");
                return models.stream()
                    .map(m -> new ModelInfo((String) m.get("name"), "Ollama model"))
                    .collect(Collectors.toList());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return List.of(new ModelInfo("(error fetching models)", ""));
        }
        return Collections.emptyList();
    }

    @Override
    public Map<String, String> sendMessage(String message, String model, String sessionId, boolean stream) {
        if (!isOllamaRunning()) {
            return Map.of("role", "assistant", "content", "(Ollama server is not running)");
        }
        try {
            String url = ollamaApiUrl + "/api/chat";
            // Add user message to context before sending
            chatContextService.addMessage(sessionId, "user", message);
            List<Map<String, String>> context = chatContextService.getContext(sessionId);
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", model);
            payload.put("messages", context);
            payload.put("temperature", 0.7);
            payload.put("stream", stream);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, request, String.class);
            String body = response.getBody();
            if (body == null || body.isEmpty()) {
                return Map.of("role", "assistant", "content", "(No response from Ollama)");
            }
            if (!stream) {
                // Parse single JSON response
                ObjectMapper mapper = new ObjectMapper();
                Map json = mapper.readValue(body, Map.class);
                Map<String, String> reply = parseOllamaResponse(json);
                chatContextService.addMessage(sessionId, reply.get("role"), reply.get("content"));
                return reply;
            } else {
                // Concatenate all message.content fields in order, stop at done=true
                ObjectMapper mapper = new ObjectMapper();
                String[] lines = body.split("\\r?\\n");
                StringBuilder fullContent = new StringBuilder();
                String role = "assistant";
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    try {
                        Map json = mapper.readValue(line, Map.class);
                        if (json.get("done") != null && Boolean.TRUE.equals(json.get("done"))) {
                            break;
                        }
                        Map msgObj = (Map) json.get("message");
                        if (msgObj != null && msgObj.get("content") != null) {
                            String chunk = msgObj.get("content").toString();
                            if (fullContent.length() > 0 && !fullContent.toString().endsWith("\n") && !chunk.startsWith("\n")) {
                                fullContent.append("\n");
                            }
                            fullContent.append(chunk);
                            if (msgObj.get("role") != null) {
                                role = msgObj.get("role").toString();
                            }
                        }
                    } catch (Exception e) {
                        // Ignore malformed lines
                    }
                }
                if (fullContent.length() == 0) {
                    return Map.of("role", "assistant", "content", "(No valid response from Ollama)");
                }
                chatContextService.addMessage(sessionId, role, fullContent.toString());
                return Map.of("role", role, "content", fullContent.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("role", "assistant", "content", "(Error communicating with Ollama)");
        }
    }

    @Override
    public void resetContext(String sessionId) {
        chatContextService.resetContext(sessionId);
    }
}

