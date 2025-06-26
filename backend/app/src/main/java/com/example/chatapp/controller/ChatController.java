package com.example.chatapp.controller;

import com.example.chatapp.engine.ModelInfo;
import java.util.List;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// @CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/chat")
public class ChatController {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChatController.class);

  private final com.example.chatapp.service.ChatService chatService;

  @Autowired
  @SuppressWarnings("PMD.LawOfDemeter")
  public ChatController(final com.example.chatapp.service.ChatService chatService) {
    this.chatService = chatService;
  }

  @GetMapping("/servers")
  public List<String> getServers() {
    LOGGER.info("Received request: GET /api/chat/servers");
    return chatService.getServers();
  }

  @GetMapping("/models")
  public ResponseEntity<List<ModelInfo>> getModels(@RequestParam final String server) {
    final List<ModelInfo> modelInfos = chatService.getModels(server);
    return ResponseEntity.ok(modelInfos);
  }

  @PostMapping("/send")
  public ResponseEntity<?> sendMessage(@Valid @RequestBody final SendMessageRequest request) {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info("Received sendMessage request: {}", request);
    }
    return ResponseEntity.ok(chatService.sendMessage(request));
  }

  @GetMapping("/search")
  public ResponseEntity<?> searchWeb(@RequestParam final String query) {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info("Received searchWeb request: {}", query);
    }
    return ResponseEntity.ok(chatService.searchWeb(query));
  }

  @PostMapping("/reset-context")
  public ResponseEntity<?> resetContext(@Valid @RequestBody final ResetContextRequest request) {
    if (LOGGER.isInfoEnabled()) {
      LOGGER.info("Received resetContext request: {}", request);
    }
    chatService.resetContext(request);
    return ResponseEntity.ok().build();
  }
}
