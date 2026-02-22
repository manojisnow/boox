package com.example.chatapp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.chatapp.controller.SendMessageRequest;
import com.example.chatapp.controller.StreamController;
import com.example.chatapp.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class StreamControllerTest {

  @Mock private ChatService chatService;

  private StreamController streamController;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    // Use SyncTaskExecutor so tasks run on the calling thread — keeps the test deterministic
    // without relying on thread-pool timing.
    streamController = new StreamController(chatService, new SyncTaskExecutor());
  }

  @Test
  void streamMessage_returnsSseEmitter() {
    SendMessageRequest req = new SendMessageRequest();
    req.setMessage("hello");
    req.setServer("ollama");
    req.setModel("m");
    req.setSessionId("sid");
    req.setStream(true);

    SseEmitter emitter = streamController.streamMessage(req);

    assertNotNull(emitter);
    verify(chatService, timeout(1000)).streamMessage(eq(req), any(SseEmitter.class));
  }
}
