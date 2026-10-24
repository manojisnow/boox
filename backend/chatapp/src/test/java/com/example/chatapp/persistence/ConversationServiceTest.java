package com.example.chatapp.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.example.chatapp.controller.dto.ConversationSummary;
import com.example.chatapp.controller.dto.MessageView;
import com.example.chatapp.engine.GenerationConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(
    properties = {
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.datasource.url=jdbc:sqlite:target/ullavox-conversation-service-migration-test.db",
      "spring.flyway.enabled=false"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
  ConversationService.class,
  JpaChatContextService.class,
  com.example.chatapp.config.SchemaMigrationConfiguration.class,
  com.example.chatapp.memory.CurrentUserResolver.class,
  com.example.chatapp.context.ExecutionCoordinator.class
})
@ActiveProfiles("test")
class ConversationServiceTest {

  @Autowired private ConversationService conversationService;
  @Autowired private JpaChatContextService contextService;
  @Autowired private TestEntityManager entityManager;
  @Autowired private com.example.chatapp.agent.AgentRunRepository agentRuns;

  @Test
  void list_returnsSummaries() {
    contextService.addMessage("c1", "user", "hello there");
    contextService.setMetadata("c1", "ollama", "m1");
    entityManager.flush();
    List<ConversationSummary> summaries = conversationService.list();
    assertEquals(1, summaries.size());
    assertEquals("c1", summaries.get(0).getId());
    assertEquals("hello there", summaries.get(0).getTitle());
    assertEquals("m1", summaries.get(0).getModel());
  }

  @Test
  void list_includesGenerationConfig_whenSet() {
    contextService.addMessage("c7", "user", "hi");
    contextService.setGenerationConfig("c7", new GenerationConfig(0.5, 4096, List.of("STOP")));
    entityManager.flush();
    ConversationSummary summary = conversationService.list().get(0);
    assertEquals(0.5, summary.getTemperature());
    assertEquals(4096, summary.getNumCtx());
    assertEquals(List.of("STOP"), summary.getStopSequences());
  }

  @Test
  void messages_omitsToolAndEmptyAssistantRows() {
    contextService.addMessage("c2", "user", "search something");
    contextService.addMessage("c2", "assistant", ""); // tool-call step, no visible text
    contextService.addMessage("c2", "tool", "raw tool result");
    contextService.addMessage("c2", "assistant", "Here is the answer");
    entityManager.flush();
    List<MessageView> views = conversationService.messages("c2");
    assertEquals(2, views.size());
    assertEquals("user", views.get(0).getRole());
    assertEquals("search something", views.get(0).getContent());
    assertEquals("assistant", views.get(1).getRole());
    assertEquals("Here is the answer", views.get(1).getContent());
  }

  @Test
  void messages_includesImages_whenPresent() {
    contextService.addMessage("c5", "user", "what is this", List.of("imgA", "imgB"));
    entityManager.flush();
    List<MessageView> views = conversationService.messages("c5");
    assertEquals(1, views.size());
    assertEquals(List.of("imgA", "imgB"), views.get(0).getImages());
  }

  @Test
  void messages_emptyImages_whenNoneAttached() {
    contextService.addMessage("c6", "user", "plain text");
    entityManager.flush();
    List<MessageView> views = conversationService.messages("c6");
    assertTrue(views.get(0).getImages().isEmpty());
  }

  @Test
  void rename_existing_updatesTitle() {
    contextService.addMessage("c3", "user", "original");
    entityManager.flush();
    Optional<ConversationSummary> renamed = conversationService.rename("c3", "  New Name  ");
    assertTrue(renamed.isPresent());
    assertEquals("New Name", renamed.get().getTitle());
  }

  @Test
  void rename_missing_returnsEmpty() {
    assertTrue(conversationService.rename("nope", "x").isEmpty());
  }

  @Test
  void delete_blocksActiveTasksAndCleansUpFinishedRuns() {
    contextService.addMessage("active", "user", "task");
    final com.example.chatapp.agent.AgentRun run =
        new com.example.chatapp.agent.AgentRun("r", "active", "task", "ollama", "model");
    agentRuns.saveAndFlush(run);
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> conversationService.delete("active"));
    run.setStatus(com.example.chatapp.agent.AgentRunStatus.COMPLETED);
    agentRuns.saveAndFlush(run);
    entityManager.flush();
    entityManager.clear();
    conversationService.delete("active");
    assertTrue(agentRuns.findById("r").isEmpty());
    entityManager.flush();
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> conversationService.messages("active"));
  }

  @Test
  void delete_removesConversationAndMessages() {
    contextService.addMessage("c4", "user", "to delete");
    // Flush the seed so it is committed before the delete, mirroring the real
    // request boundary (add and delete arrive as separate transactions).
    entityManager.flush();
    entityManager.clear();
    conversationService.delete("c4");
    assertTrue(conversationService.list().isEmpty());
    entityManager.flush();
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> conversationService.messages("c4"));
  }
}
