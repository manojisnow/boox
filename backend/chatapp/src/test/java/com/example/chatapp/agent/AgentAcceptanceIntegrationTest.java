package com.example.chatapp.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.example.chatapp.config.SchemaMigrationConfiguration;
import com.example.chatapp.context.ExecutionCoordinator;
import com.example.chatapp.context.PersonalizationResolver;
import com.example.chatapp.memory.CurrentUserResolver;
import com.example.chatapp.persistence.ConversationRepository;
import com.example.chatapp.persistence.JpaChatContextService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** First-turn acceptance uses the actual SQLite FKs and the actual JPA transaction manager. */
@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:sqlite:target/ullavox-agent-acceptance-migration-test.db",
      "spring.flyway.enabled=false"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  SchemaMigrationConfiguration.class,
  CurrentUserResolver.class,
  ExecutionCoordinator.class,
  AgentRunService.class,
  JpaChatContextService.class,
  AgentOrchestrator.class,
  AgentAcceptanceIntegrationTest.Dispatch.class
})
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AgentAcceptanceIntegrationTest {
  @Autowired AgentOrchestrator orchestrator;
  @Autowired JpaChatContextService context;
  @Autowired ConversationRepository conversations;
  @Autowired AgentRunRepository runs;
  @Autowired ExecutionCoordinator coordinator;
  @Autowired CurrentUserResolver users;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean AgentExecutionService execution;
  @MockitoBean PersonalizationResolver personalization;

  @TestConfiguration
  static class Dispatch {
    @Bean("agentTaskExecutor")
    TaskExecutor executor() {
      return task -> {
        assertFalse(
            TransactionSynchronizationManager.isActualTransactionActive(),
            "Dispatch must occur after acceptance commits");
        throw new TaskRejectedException("Synthetic queue rejection");
      };
    }
  }

  private AgentRunStartRequest request() {
    AgentRunStartRequest request = new AgentRunStartRequest();
    request.setConversationId("first-agent-" + UUID.randomUUID());
    request.setServer("ollama");
    request.setModel("synthetic-model");
    request.setGoal("Explain this synthetic task");
    return request;
  }

  @Test
  void firstTurnCommitsConversationAndUserBeforeRunAndDispatchRejectionKeepsAcceptedHistory() {
    AgentRunStartRequest request = request();
    AgentRunView run = orchestrator.start(request);
    assertEquals(AgentRunStatus.FAILED, run.getStatus());
    assertEquals(
        users.currentUserId(),
        conversations.findById(request.getConversationId()).orElseThrow().getOwnerId());
    assertEquals(1, context.getContext(request.getConversationId()).size());
    assertEquals("user", context.getContext(request.getConversationId()).getFirst().get("role"));
    assertTrue(runs.findById(run.getId()).isPresent());
    assertTrue(jdbc.queryForList("pragma foreign_key_check").isEmpty());
    assertEquals(
        "idle",
        coordinator.status(users.currentUserId(), request.getConversationId()).get("state"));
  }

  @Test
  void failureBeforeCommitRollsBackConversationMessageAndRunAndReleasesReservation() {
    AgentRunStartRequest request = request();
    AgentAttachment oversized = new AgentAttachment();
    oversized.setName("synthetic.txt");
    oversized.setContent("x".repeat(24001));
    request.setAttachments(List.of(oversized));
    assertThrows(ResponseStatusException.class, () -> orchestrator.start(request));
    assertTrue(conversations.findById(request.getConversationId()).isEmpty());
    assertTrue(
        runs.findByConversationIdOrderByCreatedAtDesc(request.getConversationId()).isEmpty());
    assertEquals(
        0,
        jdbc.queryForObject(
            "select count(*) from chat_message where conversation_id=?",
            Integer.class,
            request.getConversationId()));
    assertEquals(
        "idle",
        coordinator.status(users.currentUserId(), request.getConversationId()).get("state"));
  }
}
