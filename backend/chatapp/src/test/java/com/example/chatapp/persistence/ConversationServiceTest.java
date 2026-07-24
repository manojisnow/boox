package com.example.chatapp.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.example.chatapp.controller.dto.ConversationSummary;
import com.example.chatapp.controller.dto.MessageView;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ConversationService.class, JpaChatContextService.class})
@ActiveProfiles("test")
class ConversationServiceTest {

  @Autowired private ConversationService conversationService;
  @Autowired private JpaChatContextService contextService;
  @Autowired private TestEntityManager entityManager;

  @Test
  void list_returnsSummaries() {
    contextService.addMessage("c1", "user", "hello there");
    contextService.setMetadata("c1", "ollama", "m1");
    List<ConversationSummary> summaries = conversationService.list();
    assertEquals(1, summaries.size());
    assertEquals("c1", summaries.get(0).getId());
    assertEquals("hello there", summaries.get(0).getTitle());
    assertEquals("m1", summaries.get(0).getModel());
  }

  @Test
  void messages_omitsToolAndEmptyAssistantRows() {
    contextService.addMessage("c2", "user", "search something");
    contextService.addMessage("c2", "assistant", ""); // tool-call step, no visible text
    contextService.addMessage("c2", "tool", "raw tool result");
    contextService.addMessage("c2", "assistant", "Here is the answer");
    List<MessageView> views = conversationService.messages("c2");
    assertEquals(2, views.size());
    assertEquals("user", views.get(0).getRole());
    assertEquals("search something", views.get(0).getContent());
    assertEquals("assistant", views.get(1).getRole());
    assertEquals("Here is the answer", views.get(1).getContent());
  }

  @Test
  void rename_existing_updatesTitle() {
    contextService.addMessage("c3", "user", "original");
    Optional<ConversationSummary> renamed = conversationService.rename("c3", "  New Name  ");
    assertTrue(renamed.isPresent());
    assertEquals("New Name", renamed.get().getTitle());
  }

  @Test
  void rename_missing_returnsEmpty() {
    assertTrue(conversationService.rename("nope", "x").isEmpty());
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
    assertTrue(conversationService.messages("c4").isEmpty());
  }
}
