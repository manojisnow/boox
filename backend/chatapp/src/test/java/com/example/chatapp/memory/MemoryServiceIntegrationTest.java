package com.example.chatapp.memory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.example.chatapp.config.SchemaMigrator;
import com.example.chatapp.context.ExecutionCoordinator;
import com.example.chatapp.context.PersonalizationResolver;
import com.example.chatapp.persistence.Conversation;
import com.example.chatapp.persistence.ConversationRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/** Exercises real migrated SQLite constraints and domain transactions, without model calls. */
class MemoryServiceIntegrationTest {
  @TempDir Path temporary;
  private JdbcTemplate jdbc;
  private CurrentUserResolver users;
  private ExecutionCoordinator coordinator;
  private MemoryService service;
  private ConversationRepository conversations;
  private DataSourceTransactionManager manager;

  @BeforeEach
  void migrateFreshDatabase() {
    SQLiteConfig config = new SQLiteConfig();
    config.enforceForeignKeys(true);
    SQLiteDataSource source = new SQLiteDataSource(config);
    source.setUrl("jdbc:sqlite:" + temporary.resolve("memory.db"));
    SchemaMigrator.migrate(source);
    jdbc = new JdbcTemplate(source);
    users = new CurrentUserResolver(jdbc);
    coordinator = new ExecutionCoordinator();
    manager = new DataSourceTransactionManager(source);
    conversations = mock(ConversationRepository.class);
    when(conversations.findById(anyString()))
        .thenAnswer(
            call -> {
              String id = call.getArgument(0);
              List<String> owners =
                  jdbc.queryForList(
                      "select owner_id from conversation where id=?", String.class, id);
              if (owners.isEmpty()) return Optional.empty();
              Conversation conversation = new Conversation(id);
              conversation.setOwnerId(owners.getFirst());
              return Optional.of(conversation);
            });
    service = new MemoryService(jdbc, users, coordinator, manager, conversations);
  }

  private String conversation(String id) {
    jdbc.update(
        "insert into conversation(id,owner_id,title,use_preferences,use_project_context,settings_version,summarized_count,created_at,updated_at) values(?,?,?,1,1,0,0,?,?)",
        id,
        users.currentUserId(),
        id,
        Instant.now().toString(),
        Instant.now().toString());
    return id;
  }

  private Map<String, Object> project(String name) {
    return service.saveProject(
        null, Map.of("name", name, "instructions", "Keep this project's decisions local"));
  }

  private void assign(String chat, String project) {
    service.saveConversationSettings(
        chat,
        Map.of("version", service.conversationSettings(chat).get("version"), "projectId", project));
  }

  private Map<String, Object> note(String project, String title, String content) {
    return service.saveMemory(
        project,
        null,
        Map.of("title", title, "content", content, "kind", "decision", "pinned", true));
  }

  private void assertStatus(HttpStatus status, org.junit.jupiter.api.function.Executable action) {
    assertEquals(status, assertThrows(ResponseStatusException.class, action).getStatusCode());
  }

  @Test
  void preferencesUseVersionsAndClearingWaitsForActualWorkerExit() {
    assertEquals("not_started", service.preferences().get("onboardingStatus"));
    Map<String, Object> first =
        service.savePreferences(
            Map.of(
                "version",
                0,
                "onboardingStatus",
                "completed",
                "responseLength",
                "concise",
                "background",
                "synthetic private background"));
    assertStatus(
        HttpStatus.CONFLICT,
        () -> service.savePreferences(Map.of("version", 0, "onboardingStatus", "skipped")));
    String token = coordinator.reserve(users.currentUserId(), "chat");
    coordinator.start(
        users.currentUserId(), "chat", token, Set.of("preferences:" + users.currentUserId()));
    assertStatus(HttpStatus.CONFLICT, service::clearPreferences);
    assertStatus(
        HttpStatus.CONFLICT,
        () ->
            service.savePreferences(
                Map.of(
                    "version",
                    first.get("version"),
                    "onboardingStatus",
                    "completed",
                    "responseLength",
                    "concise")));
    Map<String, Object> edit =
        service.savePreferences(
            Map.of(
                "version",
                first.get("version"),
                "onboardingStatus",
                "completed",
                "responseLength",
                "detailed",
                "background",
                "synthetic private background"));
    assertEquals("detailed", edit.get("responseLength"));
    coordinator.finish(users.currentUserId(), "chat", token);
    assertStatus(
        HttpStatus.CONFLICT,
        () -> service.clearPreferences(((Number) first.get("version")).longValue()));
    assertEquals("skipped", service.clearPreferences().get("onboardingStatus"));
    assertEquals("", service.preferences().get("background"));
  }

  @Test
  void ownersAndProjectRelationsAreEnforcedByServicesAndSqlite() {
    String chat = conversation("owned-chat");
    String first = project("First").get("id").toString();
    String secondOwner = UUID.randomUUID().toString();
    jdbc.update(
        "insert into local_user(id,created_at) values(?,?)", secondOwner, Instant.now().toString());
    CurrentUserResolver other = spy(new CurrentUserResolver(jdbc));
    doReturn(secondOwner).when(other).currentUserId();
    MemoryService second = new MemoryService(jdbc, other, coordinator, manager, conversations);
    String secondProject = second.saveProject(null, Map.of("name", "Other")).get("id").toString();
    assertStatus(HttpStatus.NOT_FOUND, () -> second.conversationSettings(chat));
    assertStatus(HttpStatus.NOT_FOUND, () -> second.project(first));
    assertStatus(
        HttpStatus.NOT_FOUND,
        () ->
            second.saveMemory(
                first, null, Map.of("title", "bad", "content", "bad", "kind", "note")));
    assertStatus(HttpStatus.NOT_FOUND, () -> assign(chat, secondProject));
    assertNull(service.conversationSettings(chat).get("projectId"));
    assertThrows(
        DataAccessException.class,
        () -> jdbc.update("update conversation set project_id=? where id=?", secondProject, chat));
  }

  @Test
  void savedNotesSurviveSourceRemovalAndConversationMove() {
    String chat = conversation("source-chat"),
        first = project("First").get("id").toString(),
        second = project("Second").get("id").toString();
    assign(chat, first);
    jdbc.update(
        "insert into chat_message(conversation_id,seq,role,content,created_at) values(?,0,'assistant','Use SQLite',?)",
        chat,
        Instant.now().toString());
    Long message =
        jdbc.queryForObject(
            "select id from chat_message where conversation_id=?", Long.class, chat);
    Map<String, Object> saved =
        service.saveMemory(
            first,
            null,
            Map.of(
                "title",
                "Storage",
                "content",
                "Use SQLite",
                "kind",
                "decision",
                "sourceConversationId",
                chat,
                "sourceMessageId",
                message));
    String id = saved.get("id").toString();
    service.saveMemory(
        first,
        id,
        Map.of(
            "version",
            saved.get("version"),
            "title",
            "Edited storage",
            "content",
            "SQLite remains our choice",
            "kind",
            "decision"));
    assign(chat, second);
    Map<String, Object> moved = service.memories(first).getFirst();
    assertEquals("SQLite remains our choice", moved.get("content"));
    assertNull(moved.get("sourceMessageId"));
    assertTrue(service.memories(second).isEmpty());
    jdbc.update("delete from chat_message where id=?", message);
    jdbc.update("delete from conversation where id=?", chat);
    assertEquals(id, service.memories(first).getFirst().get("id"));
    service.deleteProject(first);
    assertEquals(
        0,
        jdbc.queryForObject("select count(*) from project_memory where id=?", Integer.class, id));
  }

  @Test
  void sourceMessageDeletionCascadesOnlyProvenanceAndCannotForgeForeignSource() {
    String chat = conversation("chat"), foreignChat = conversation("different-project");
    String first = project("First").get("id").toString(),
        second = project("Second").get("id").toString();
    assign(chat, first);
    assign(foreignChat, second);
    jdbc.update(
        "insert into chat_message(conversation_id,seq,role,content,created_at) values(?,0,'user','Decision',?)",
        chat,
        Instant.now().toString());
    Long source =
        jdbc.queryForObject(
            "select id from chat_message where conversation_id=?", Long.class, chat);
    Map<String, Object> saved =
        service.saveMemory(
            first,
            null,
            Map.of(
                "title",
                "Decision",
                "content",
                "Keep SQLite",
                "kind",
                "decision",
                "sourceConversationId",
                chat,
                "sourceMessageId",
                source));
    assertStatus(
        HttpStatus.NOT_FOUND,
        () ->
            service.saveMemory(
                second,
                null,
                Map.of(
                    "title",
                    "Forged",
                    "content",
                    "bad",
                    "kind",
                    "note",
                    "sourceConversationId",
                    chat,
                    "sourceMessageId",
                    source)));
    jdbc.update("delete from chat_message where id=?", source);
    assertEquals(saved.get("id"), service.memories(first).getFirst().get("id"));
    assertNull(service.memories(first).getFirst().get("sourceConversationId"));
  }

  @Test
  void frozenContextMetadataContainsVersionsAndProjectDeletionWaitsForWorker() {
    String chat = conversation("chat"), project = project("Project").get("id").toString();
    assign(chat, project);
    Map<String, Object> saved = note(project, "Storage", "Synthetic secret note body");
    String token = coordinator.reserve(users.currentUserId(), chat);
    var snapshot =
        new PersonalizationResolver(service, coordinator)
            .resolve(
                users.currentUserId(),
                chat,
                token,
                "storage",
                List.of(saved.get("id").toString()),
                3000);
    service.recordContextUsed(chat, token, snapshot.contextUsed());
    assertFalse(service.latestContextUsed(chat).toString().contains("Synthetic secret note body"));
    assertTrue(service.latestContextUsed(chat).toString().contains(saved.get("id").toString()));
    assertStatus(
        HttpStatus.CONFLICT, () -> service.deleteMemory(project, saved.get("id").toString()));
    assertStatus(HttpStatus.CONFLICT, () -> service.deleteProject(project));
    assertStatus(
        HttpStatus.CONFLICT,
        () ->
            service.saveConversationSettings(
                chat,
                Map.of(
                    "version",
                    service.conversationSettings(chat).get("version"),
                    "usePreferences",
                    false)));
    service.saveMemory(
        project,
        saved.get("id").toString(),
        Map.of(
            "version",
            saved.get("version"),
            "title",
            "Storage",
            "content",
            "New version",
            "kind",
            "decision"));
    assertTrue(snapshot.text().contains("Synthetic secret note body"));
    coordinator.finish(users.currentUserId(), chat, token);
    service.deleteProject(project);
    assertNull(service.conversationSettings(chat).get("projectId"));
    assertEquals(
        1,
        jdbc.queryForObject("select count(*) from conversation where id=?", Integer.class, chat));
  }

  @Test
  void staleDeleteVersionsCannotRemoveUpdatedProjectOrNote() {
    Map<String, Object> original = project("Project");
    String project = original.get("id").toString();
    Map<String, Object> changed =
        service.saveProject(
            project,
            Map.of(
                "version",
                original.get("version"),
                "name",
                "Updated project",
                "instructions",
                "Keep local"));
    assertStatus(
        HttpStatus.CONFLICT,
        () -> service.deleteProject(project, ((Number) original.get("version")).longValue()));
    Map<String, Object> saved = note(project, "Decision", "Original");
    String id = saved.get("id").toString();
    Map<String, Object> edit =
        service.saveMemory(
            project,
            id,
            Map.of(
                "version",
                saved.get("version"),
                "title",
                "Decision",
                "content",
                "Updated",
                "kind",
                "decision"));
    assertStatus(
        HttpStatus.CONFLICT,
        () -> service.deleteMemory(project, id, ((Number) saved.get("version")).longValue()));
    service.deleteMemory(project, id, ((Number) edit.get("version")).longValue());
    assertTrue(service.memories(project).isEmpty());
    service.deleteProject(project, ((Number) changed.get("version")).longValue());
    assertTrue(service.projects().isEmpty());
  }
}
