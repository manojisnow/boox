package com.example.chatapp.agent;

import com.example.chatapp.controller.SendMessageRequest;
import com.example.chatapp.engine.ChatContextService;
import com.example.chatapp.service.ChatService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Runs bounded agent tasks using Boox's existing local-model and tool-call infrastructure.
 *
 * <p>This first version deliberately has one general-purpose agent. It supplies task-specific
 * instructions, delegates model and tool execution to {@link ChatService}, and records the durable
 * lifecycle. Tool permissions and approval pauses are added above this class rather than delegated
 * to the model.
 */
@Service
public class AgentOrchestrator {
  private static final Logger LOGGER = LoggerFactory.getLogger(AgentOrchestrator.class);

  private static final String AGENT_INSTRUCTIONS =
      "You are Boox Agent, a careful local task assistant. Work toward the user's goal in a"
          + " bounded number of steps. Use available tools only when they materially help. Before"
          + " answering, verify tool results and state clearly what you did. Do not claim to have"
          + " changed files, contacted services, or completed an action unless a tool result"
          + " confirms it. If an action would have side effects, explain what approval you need"
          + " instead of pretending it was performed.\n\n";

  private final AgentRunService agentRunService;
  private final ChatService chatService;
  private final ChatContextService chatContextService;
  private final TaskExecutor executor;

  public AgentOrchestrator(
      final AgentRunService agentRunService,
      final ChatService chatService,
      final ChatContextService chatContextService,
      @Qualifier("agentTaskExecutor") final TaskExecutor executor) {
    this.agentRunService = agentRunService;
    this.chatService = chatService;
    this.chatContextService = chatContextService;
    this.executor = executor;
  }

  /** Schedules a run and immediately returns control to the HTTP request thread. */
  public void schedule(final String runId) {
    executor.execute(() -> execute(runId));
  }

  private void execute(final String runId) {
    final AgentRunView run = agentRunService.get(runId).orElse(null);
    if (run == null || run.getStatus() == AgentRunStatus.CANCELLED) {
      return;
    }
    if (run.getStatus() == AgentRunStatus.CANCEL_REQUESTED) {
      agentRunService.markCancelled(runId);
      return;
    }

    agentRunService.markRunning(runId);
    final String originalSystemPrompt = chatContextService.getSystemPrompt(run.getConversationId());
    try {
      final SendMessageRequest request = new SendMessageRequest();
      request.setMessage(run.getGoal());
      request.setServer(run.getServer());
      request.setModel(run.getModel());
      request.setSessionId(run.getConversationId());
      request.setStream(false);
      request.setSystemPrompt(agentInstructions(originalSystemPrompt));

      final Map<String, String> response = chatService.sendMessage(request);
      if (agentRunService.isCancellationRequested(runId)) {
        agentRunService.markCancelled(runId);
      } else {
        agentRunService.complete(runId, response.getOrDefault("content", ""));
      }
    } catch (Exception exception) {
      LOGGER.error("Agent run {} failed: {}", runId, exception.getMessage(), exception);
      if (agentRunService.isCancellationRequested(runId)) {
        agentRunService.markCancelled(runId);
      } else {
        agentRunService.fail(
            runId, "The agent could not complete this task: " + exception.getMessage());
      }
    } finally {
      // Agent instructions apply to this run only. Normal Ask mode retains the conversation's
      // previous user prompt once the task completes.
      chatContextService.setSystemPrompt(run.getConversationId(), originalSystemPrompt);
    }
  }

  private static String agentInstructions(final String originalSystemPrompt) {
    if (originalSystemPrompt == null || originalSystemPrompt.isBlank()) {
      return AGENT_INSTRUCTIONS;
    }
    return AGENT_INSTRUCTIONS + "Additional user preference:\n" + originalSystemPrompt;
  }
}
