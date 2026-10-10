package com.example.chatapp.controller;

import com.example.chatapp.agent.AgentOrchestrator;
import com.example.chatapp.agent.AgentRunService;
import com.example.chatapp.agent.AgentRunStartRequest;
import com.example.chatapp.agent.AgentRunView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP API for creating and inspecting agent tasks. */
@RestController
@RequestMapping("/api/agent-runs")
public class AgentRunController {

  private final AgentRunService agentRunService;
  private final AgentOrchestrator agentOrchestrator;

  public AgentRunController(
      final AgentRunService agentRunService, final AgentOrchestrator agentOrchestrator) {
    this.agentRunService = agentRunService;
    this.agentOrchestrator = agentOrchestrator;
  }

  @PostMapping
  public ResponseEntity<AgentRunView> create(
      @Valid @RequestBody final AgentRunStartRequest request) {
    final AgentRunView run = agentRunService.create(request);
    agentOrchestrator.schedule(run.getId());
    return ResponseEntity.accepted().body(run);
  }

  @GetMapping("/{runId}")
  public ResponseEntity<AgentRunView> get(@PathVariable final String runId) {
    return ResponseEntity.of(agentRunService.get(runId));
  }

  @GetMapping
  public List<AgentRunView> list(@RequestParam final String conversationId) {
    return agentRunService.listForConversation(conversationId);
  }

  @PostMapping("/{runId}/cancel")
  public ResponseEntity<AgentRunView> cancel(@PathVariable final String runId) {
    return ResponseEntity.of(agentRunService.cancel(runId));
  }
}
