package com.example.chatapp.controller;

import com.example.chatapp.service.ChatService;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class StreamController {

  private static final Logger LOGGER = LoggerFactory.getLogger(StreamController.class);
  private static final long SSE_TIMEOUT = 300_000L; // 5 minutes

  private final ChatService chatService;
  private final TaskExecutor executor;

  @Autowired
  public StreamController(
      final ChatService chatService, @Qualifier("streamTaskExecutor") final TaskExecutor executor) {
    this.chatService = chatService;
    this.executor = executor;
  }

  @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamMessage(@Valid @RequestBody final SendMessageRequest request) {
    LOGGER.info(
        "Received stream request: server={}, model={}, session={}",
        request.getServer(),
        request.getModel(),
        request.getSessionId());

    final SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
    emitter.onCompletion(() -> LOGGER.debug("SSE connection completed"));
    emitter.onTimeout(
        () -> {
          LOGGER.warn("SSE connection timed out");
          emitter.complete();
        });

    executor.execute(() -> chatService.streamMessage(request, emitter));
    return emitter;
  }
}
