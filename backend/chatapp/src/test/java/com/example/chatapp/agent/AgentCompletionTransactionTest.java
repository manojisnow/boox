package com.example.chatapp.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.chatapp.persistence.JpaChatContextService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:sqlite:target/ullavox-agent-completion-migration-test.db",
      "spring.flyway.enabled=false"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  AgentRunService.class,
  JpaChatContextService.class,
  com.example.chatapp.config.SchemaMigrationConfiguration.class,
  com.example.chatapp.memory.CurrentUserResolver.class,
  com.example.chatapp.context.ExecutionCoordinator.class
})
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AgentCompletionTransactionTest {
  @Autowired private AgentRunService runs;
  @MockitoSpyBean private JpaChatContextService context;

  private AgentRunView seed() {
    final AgentRunStartRequest request = new AgentRunStartRequest();
    request.setConversationId("atomic-" + UUID.randomUUID());
    request.setGoal("Explain");
    request.setServer("ollama");
    request.setModel("model");
    context.addMessage(request.getConversationId(), "user", "Explain");
    final AgentRunView run = runs.create(request);
    return runs.markRunning(run.getId()).orElseThrow();
  }

  @Test
  void readerCannotSeeCompletedUntilHistoryIsCommittedAndRepeatedCompletionIsIdempotent()
      throws Exception {
    final AgentRunView run = seed();
    final CountDownLatch written = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    doAnswer(
            call -> {
              call.callRealMethod();
              written.countDown();
              if (!release.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Reader did not release writer");
              return null;
            })
        .when(context)
        .addMessage(eq(run.getConversationId()), eq("assistant"), eq("answer"), eq(List.of()));
    final ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      final Future<?> future = executor.submit(() -> runs.complete(run.getId(), "answer"));
      assertTrue(written.await(10, TimeUnit.SECONDS));
      try {
        assertEquals(AgentRunStatus.RUNNING, runs.get(run.getId()).orElseThrow().getStatus());
        assertEquals(1, context.getContext(run.getConversationId()).size());
      } finally {
        release.countDown();
      }
      future.get(10, TimeUnit.SECONDS);
      assertEquals(AgentRunStatus.COMPLETED, runs.get(run.getId()).orElseThrow().getStatus());
      assertEquals("answer", context.getContext(run.getConversationId()).getLast().get("content"));
      runs.complete(run.getId(), "duplicate");
      assertEquals(2, context.getContext(run.getConversationId()).size());
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void historyWriteFailureRollsBackBothHistoryAndCompletion() {
    final AgentRunView run = seed();
    doAnswer(
            call -> {
              call.callRealMethod();
              throw new IllegalStateException("Injected history failure");
            })
        .when(context)
        .addMessage(eq(run.getConversationId()), eq("assistant"), eq("answer"), eq(List.of()));
    assertThrows(IllegalStateException.class, () -> runs.complete(run.getId(), "answer"));
    assertEquals(AgentRunStatus.RUNNING, runs.get(run.getId()).orElseThrow().getStatus());
    assertNull(runs.get(run.getId()).orElseThrow().getFinalOutput());
    assertEquals(1, context.getContext(run.getConversationId()).size());
    runs.fail(run.getId(), "Injected history failure");
    assertEquals(AgentRunStatus.FAILED, runs.get(run.getId()).orElseThrow().getStatus());
  }

  @Test
  void cancellationPreventsBothCompletionAndAssistantHistory() {
    final AgentRunView run = seed();
    runs.cancel(run.getId());
    assertEquals(
        AgentRunStatus.CANCELLED,
        runs.complete(run.getId(), "late answer").orElseThrow().getStatus());
    assertNull(runs.get(run.getId()).orElseThrow().getFinalOutput());
    assertEquals(1, context.getContext(run.getConversationId()).size());
  }
}
