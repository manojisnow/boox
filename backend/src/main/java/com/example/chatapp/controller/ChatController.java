package com.example.chatapp.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import com.example.chatapp.engine.ModelInfo;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// @CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    private final com.example.chatapp.service.ChatService chatService;

    @Autowired
    public ChatController(com.example.chatapp.service.ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/servers")
    public List<String> getServers() {
        logger.info("Received request: GET /api/chat/servers");
        return chatService.getServers();
    }

    @GetMapping("/models")
    public List<String> getModels(@RequestParam String server) {
        logger.info("Received request: GET /api/chat/models for server {}", server);
        List<ModelInfo> modelInfos = chatService.getModels(server);
        return modelInfos.stream().map(ModelInfo::getName).toList();
    }

    @PostMapping("/send")
    public Map<String, String> sendMessage(@Valid @RequestBody SendMessageRequest request) {
        logger.info("Received request: POST /api/chat/send for session {}", request.getSessionId());
        return chatService.sendMessage(
            request.getMessage(),
            request.getServer(),
            request.getModel(),
            request.getSessionId(),
            request.getStream() != null && request.getStream()
        );
    }

    @GetMapping("/search")
    public String searchWeb(@RequestParam String query) {
        logger.info("Received request: GET /api/chat/search for query '{}'");
        return chatService.searchWeb(query);
    }

    @PostMapping("/reset-context")
    public void resetContext(@Valid @RequestBody ResetContextRequest request) {
        logger.info("Received request: POST /api/chat/reset-context for session {}", request.getSessionId());
        chatService.resetContext(request.getServer(), request.getSessionId());
    }
}
