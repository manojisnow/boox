package com.example.chatapp.context;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.chatapp.memory.MemoryService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PersonalizationResolverTest {
  @Test
  void optOutExcludesProfileAndProjectAndRejectsExplicitNotes() {
    MemoryService memory = mock(MemoryService.class);
    ExecutionCoordinator coordinator = new ExecutionCoordinator();
    when(memory.conversationSettings("chat"))
        .thenReturn(
            Map.of("usePreferences", false, "useProjectContext", false, "projectId", "project"));
    PersonalizationResolver resolver = new PersonalizationResolver(memory, coordinator);
    String token = coordinator.reserve("owner", "chat");
    PersonalizationContext result =
        resolver.resolve("owner", "chat", token, "question", List.of(), 3000);
    assertEquals("", result.text());
    verify(memory, never()).preferences();
    verify(memory, never()).project(anyString());
    assertThrows(
        ResponseStatusException.class,
        () -> resolver.resolve("owner", "chat", token, "question", List.of("note"), 3000));
  }

  @Test
  void selectedPinnedAndRelevantNotesAreFrozenAndRegistered() {
    MemoryService memory = mock(MemoryService.class);
    ExecutionCoordinator coordinator = new ExecutionCoordinator();
    when(memory.conversationSettings("chat")).thenReturn(Map.of("projectId", "project"));
    when(memory.preferences()).thenReturn(Map.of("responseLength", "concise", "version", 2));
    when(memory.project("project"))
        .thenReturn(Map.of("instructions", "Work locally", "version", 3, "name", "Ullavox"));
    when(memory.memories("project"))
        .thenReturn(
            List.of(
                Map.of(
                    "id",
                    "unrelated",
                    "title",
                    "Holiday",
                    "content",
                    "Visit the beach",
                    "version",
                    1),
                Map.of(
                    "id", "relevant", "title", "Deployment", "content", "Use Docker", "version", 2),
                Map.of(
                    "id",
                    "pin",
                    "title",
                    "Offline",
                    "content",
                    "Keep data local",
                    "pinned",
                    true,
                    "version",
                    1),
                Map.of("id", "selected", "title", "Storage", "content", "SQLite", "version", 1)));
    PersonalizationResolver resolver = new PersonalizationResolver(memory, coordinator);
    String token = coordinator.reserve("owner", "chat");
    PersonalizationContext result =
        resolver.resolve("owner", "chat", token, "deployment", List.of("selected"), 3000);
    assertTrue(result.text().contains("SQLite"));
    assertTrue(result.text().contains("Docker"));
    assertFalse(result.text().contains("beach"));
    assertTrue(result.text().indexOf("SQLite") < result.text().indexOf("Keep data local"));
    assertThrows(
        ResponseStatusException.class,
        () -> coordinator.assertNotReferenced("owner", "preferences:owner"));
    assertThrows(
        ResponseStatusException.class,
        () -> coordinator.assertNotReferenced("owner", "memory:selected"));
    when(memory.preferences()).thenReturn(Map.of("responseLength", "detailed", "version", 3));
    assertTrue(result.text().contains("concise"));
    assertFalse(result.text().contains("detailed"));
    assertFalse(result.contextUsed().toString().contains("SQLite"));
  }

  @Test
  void failedSelectionRegistersNoReferences() {
    MemoryService memory = mock(MemoryService.class);
    ExecutionCoordinator coordinator = new ExecutionCoordinator();
    when(memory.conversationSettings("chat")).thenReturn(Map.of("projectId", "project"));
    when(memory.preferences()).thenReturn(Map.of("responseLength", "concise"));
    when(memory.project("project")).thenReturn(Map.of());
    when(memory.memories("project")).thenReturn(List.of());
    String token = coordinator.reserve("owner", "chat");
    assertThrows(
        ResponseStatusException.class,
        () ->
            new PersonalizationResolver(memory, coordinator)
                .resolve("owner", "chat", token, "question", List.of("deleted"), 3000));
    assertDoesNotThrow(() -> coordinator.assertNotReferenced("owner", "preferences:owner"));
  }

  @Test
  void explicitNotesReserveSpaceBeforeOptionalBackground() {
    MemoryService memory = mock(MemoryService.class);
    ExecutionCoordinator coordinator = new ExecutionCoordinator();
    when(memory.conversationSettings("chat")).thenReturn(Map.of("projectId", "project"));
    when(memory.preferences())
        .thenReturn(Map.of("background", "background".repeat(240), "version", 1));
    when(memory.project("project")).thenReturn(Map.of());
    when(memory.memories("project"))
        .thenReturn(
            List.of(
                Map.of(
                    "id",
                    "selected",
                    "title",
                    "Decision",
                    "content",
                    "SQLite ".repeat(80),
                    "version",
                    1)));
    String token = coordinator.reserve("owner", "chat");
    PersonalizationContext result =
        new PersonalizationResolver(memory, coordinator)
            .resolve("owner", "chat", token, "what did we decide", List.of("selected"), 3000);
    assertTrue(result.text().contains("SQLite"));
    assertFalse(result.text().contains("backgroundbackground"));
    assertTrue(result.contextUsed().get("omitted").toString().contains("background"));
  }
}
