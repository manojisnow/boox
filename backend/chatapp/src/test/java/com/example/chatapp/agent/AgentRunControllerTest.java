package com.example.chatapp.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.chatapp.controller.AgentRunController;
import com.example.chatapp.controller.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AgentRunControllerTest {
  @Test
  void createInspectListAndCancelUseDurableRunState() {
    final AgentRunService service = mock(AgentRunService.class);
    final AgentOrchestrator orchestrator = mock(AgentOrchestrator.class);
    final AgentRunController controller = new AgentRunController(service, orchestrator);
    final AgentRunView view =
        new AgentRunView(
            "r",
            "c",
            "goal",
            "ollama",
            "model",
            AgentRunStatus.QUEUED,
            null,
            null,
            Instant.now(),
            Instant.now(),
            List.of());
    when(orchestrator.start(any())).thenReturn(view);
    assertEquals(
        HttpStatus.ACCEPTED, controller.create(new AgentRunStartRequest()).getStatusCode());
    when(service.get("r")).thenReturn(Optional.of(view));
    assertEquals(view, controller.get("r").getBody());
    assertEquals(HttpStatus.NOT_FOUND, controller.get("missing").getStatusCode());
    when(service.listForConversation("c")).thenReturn(List.of(view));
    assertEquals(List.of(view), controller.list("c"));
    when(service.cancel("r")).thenReturn(Optional.of(view));
    assertEquals(view, controller.cancel("r").getBody());
    final GlobalExceptionHandler errors = new GlobalExceptionHandler();
    assertEquals(
        HttpStatus.CONFLICT,
        errors
            .handleStatusException(new ResponseStatusException(HttpStatus.CONFLICT, "busy"))
            .getStatusCode());
  }
}
