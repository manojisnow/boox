package com.example.chatapp.memory;

import com.example.chatapp.context.ExecutionCoordinator;
import com.example.chatapp.persistence.Conversation;
import com.example.chatapp.persistence.ConversationRepository;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** Owner-scoped local preferences and independent user-authored project knowledge. */
@Service
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring-managed dependencies are shared application services.")
public class MemoryService {
  private final JdbcTemplate jdbc;
  private final CurrentUserResolver users;
  private final ExecutionCoordinator coordinator;
  private final TransactionTemplate transactions;
  private final ConversationRepository conversations;
  private final ObjectMapper json = new ObjectMapper();

  public MemoryService(
      JdbcTemplate jdbc,
      CurrentUserResolver users,
      ExecutionCoordinator coordinator,
      PlatformTransactionManager manager,
      ConversationRepository conversations) {
    this.jdbc = jdbc;
    this.users = users;
    this.coordinator = coordinator;
    this.transactions = new TransactionTemplate(manager);
    this.conversations = conversations;
  }

  private <T> T mutate(Supplier<T> work) {
    return coordinator.withOwner(
        users.currentUserId(), () -> transactions.execute(status -> work.get()));
  }

  public Map<String, Object> preferences() {
    var rows =
        jdbc.queryForList("select * from user_preferences where user_id=?", users.currentUserId());
    Map<String, Object> result = rows.isEmpty() ? new LinkedHashMap<>() : camel(rows.getFirst());
    result.putIfAbsent("version", 0L);
    result.putIfAbsent("onboardingStatus", "not_started");
    for (String key :
        List.of(
            "responseLength",
            "explanationStyle",
            "familiarity",
            "background",
            "goals",
            "instructions")) result.putIfAbsent(key, "");
    result.remove("userId");
    result.put("renderedInstructions", renderPreferences(result));
    return result;
  }

  public static String renderPreferences(Map<String, Object> profile) {
    var lines = new ArrayList<String>();
    Map<String, String> labels = new LinkedHashMap<>();
    labels.put("responseLength", "Response length");
    labels.put("explanationStyle", "Explanation style");
    labels.put("familiarity", "Assumed familiarity");
    labels.put("background", "User-provided background");
    labels.put("goals", "User goals");
    labels.put("instructions", "Working preferences");
    labels.forEach(
        (key, label) -> {
          Object value = profile.get(key);
          if (value != null && !value.toString().isBlank()) lines.add(label + ": " + value);
        });
    return String.join("\n", lines);
  }

  public Map<String, Object> savePreferences(Map<String, Object> input) {
    return mutate(
        () -> {
          var old = preferences();
          checkVersion(input, old);
          String status = text(input, "onboardingStatus", 20);
          if (!Set.of("not_started", "completed", "skipped").contains(status))
            bad("Invalid onboarding status");
          var values = new ArrayList<String>();
          for (String key :
              List.of(
                  "responseLength",
                  "explanationStyle",
                  "familiarity",
                  "background",
                  "goals",
                  "instructions")) {
            String value =
                text(
                    input,
                    key,
                    Set.of("background", "goals").contains(key)
                        ? 1000
                        : "instructions".equals(key) ? 2000 : 120);
            values.add(value);
            if (!Objects.toString(old.get(key), "").isEmpty() && value.isEmpty())
              coordinator.assertNotReferenced(
                  users.currentUserId(), "preferences:" + users.currentUserId());
          }
          jdbc.update(
              "insert into user_preferences(user_id,version,onboarding_status,response_length,explanation_style,familiarity,background,goals,instructions,updated_at) values(?,?,?,?,?,?,?,?,?,?) on conflict(user_id) do update set version=excluded.version,onboarding_status=excluded.onboarding_status,response_length=excluded.response_length,explanation_style=excluded.explanation_style,familiarity=excluded.familiarity,background=excluded.background,goals=excluded.goals,instructions=excluded.instructions,updated_at=excluded.updated_at",
              users.currentUserId(),
              number(old, "version") + 1,
              status,
              values.get(0),
              values.get(1),
              values.get(2),
              values.get(3),
              values.get(4),
              values.get(5),
              now());
          return preferences();
        });
  }

  public Map<String, Object> clearPreferences() {
    return clearPreferences(null);
  }

  public Map<String, Object> clearPreferences(Long expectedVersion) {
    return mutate(
        () -> {
          if (expectedVersion != null && expectedVersion != number(preferences(), "version"))
            conflict();
          coordinator.assertNotReferenced(
              users.currentUserId(), "preferences:" + users.currentUserId());
          var input = new LinkedHashMap<String, Object>();
          input.put("version", preferences().get("version"));
          input.put("onboardingStatus", "skipped");
          return savePreferences(input);
        });
  }

  public List<Map<String, Object>> projects() {
    return query(
        "select * from project where owner_id=? order by updated_at desc,id",
        users.currentUserId());
  }

  public Map<String, Object> project(String id) {
    return required(
        query("select * from project where id=? and owner_id=?", id, users.currentUserId()),
        "Project");
  }

  public Map<String, Object> saveProject(String id, Map<String, Object> input) {
    return mutate(
        () -> {
          String name = text(input, "name", 160);
          if (name.isBlank()) bad("Project name is required");
          String description = text(input, "description", 2000),
              instructions = text(input, "instructions", 4000);
          if (id == null) {
            String created = UUID.randomUUID().toString();
            jdbc.update(
                "insert into project(id,owner_id,name,description,instructions,version,created_at,updated_at) values(?,?,?,?,?,0,?,?)",
                created,
                users.currentUserId(),
                name,
                description,
                instructions,
                now(),
                now());
            return project(created);
          }
          var old = project(id);
          checkVersion(input, old);
          if (!Objects.toString(old.get("instructions"), "").isEmpty() && instructions.isEmpty())
            coordinator.assertNotReferenced(users.currentUserId(), "project:" + id);
          jdbc.update(
              "update project set name=?,description=?,instructions=?,version=version+1,updated_at=? where id=? and owner_id=?",
              name,
              description,
              instructions,
              now(),
              id,
              users.currentUserId());
          return project(id);
        });
  }

  public void deleteProject(String id) {
    deleteProject(id, null);
  }

  public void deleteProject(String id, Long expectedVersion) {
    mutate(
        () -> {
          if (expectedVersion != null && expectedVersion != number(project(id), "version"))
            conflict();
          project(id);
          coordinator.assertNotReferenced(users.currentUserId(), "project:" + id);
          for (var row :
              query(
                  "select id from conversation where project_id=? and owner_id=?",
                  id,
                  users.currentUserId()))
            coordinator.assertIdle(users.currentUserId(), row.get("id").toString());
          for (var note : memories(id))
            coordinator.assertNotReferenced(users.currentUserId(), "memory:" + note.get("id"));
          jdbc.update(
              "delete from project_memory_source where memory_id in(select id from project_memory where project_id=?)",
              id);
          jdbc.update(
              "delete from project_memory where project_id=? and owner_id=?",
              id,
              users.currentUserId());
          jdbc.update(
              "update conversation set project_id=null,settings_version=settings_version+1 where project_id=? and owner_id=?",
              id,
              users.currentUserId());
          jdbc.update("delete from project where id=? and owner_id=?", id, users.currentUserId());
          return null;
        });
  }

  public List<Map<String, Object>> memories(String projectId) {
    project(projectId);
    return query(
        "select m.*,s.source_conversation_id,s.source_message_id from project_memory m left join project_memory_source s on s.memory_id=m.id where m.project_id=? and m.owner_id=? order by m.updated_at desc,m.id",
        projectId,
        users.currentUserId());
  }

  private Map<String, Object> memory(String projectId, String id) {
    return memories(projectId).stream()
        .filter(row -> id.equals(row.get("id")))
        .findFirst()
        .orElseThrow(() -> missing("Memory"));
  }

  public Map<String, Object> saveMemory(String projectId, String id, Map<String, Object> input) {
    return mutate(
        () -> {
          project(projectId);
          String title = text(input, "title", 160),
              content = text(input, "content", 2000),
              kind = text(input, "kind", 20);
          if (content.isBlank()) bad("Note content is required");
          if (!Set.of("note", "decision", "constraint").contains(kind)) bad("Invalid note kind");
          boolean pinned = bool(input, "pinned", false);
          if (id != null) {
            var old = memory(projectId, id);
            checkVersion(input, old);
            jdbc.update(
                "update project_memory set title=?,content=?,kind=?,pinned=?,version=version+1,updated_at=? where id=? and owner_id=?",
                title,
                content,
                kind,
                pinned,
                now(),
                id,
                users.currentUserId());
            return memory(projectId, id);
          }
          if (memories(projectId).size() >= 200) bad("A project may contain at most 200 notes");
          String source = nullableText(input, "sourceConversationId", 255);
          Object message = input.get("sourceMessageId");
          if ((source == null) != (message == null))
            bad("Both source conversation and message are required");
          Long messageId = null;
          if (source != null) {
            users.requireOwnedConversation(source);
            var matches =
                jdbc.queryForList(
                    "select m.id from chat_message m join conversation c on c.id=m.conversation_id where m.id=? and c.id=? and c.project_id=? and c.owner_id=? and m.role in ('user','assistant')",
                    message,
                    source,
                    projectId,
                    users.currentUserId());
            if (matches.isEmpty()) throw missing("Source message");
            messageId = ((Number) matches.getFirst().get("id")).longValue();
          }
          String created = UUID.randomUUID().toString();
          jdbc.update(
              "insert into project_memory(id,project_id,owner_id,title,content,kind,origin,pinned,version,created_at,updated_at) values(?,?,?,?,?,?,?,?,0,?,?)",
              created,
              projectId,
              users.currentUserId(),
              title,
              content,
              kind,
              source == null ? "manual" : "message",
              pinned,
              now(),
              now());
          if (source != null)
            jdbc.update(
                "insert into project_memory_source(memory_id,source_conversation_id,source_message_id) values(?,?,?)",
                created,
                source,
                messageId);
          return memory(projectId, created);
        });
  }

  public void deleteMemory(String projectId, String id) {
    deleteMemory(projectId, id, null);
  }

  public void deleteMemory(String projectId, String id, Long expectedVersion) {
    mutate(
        () -> {
          if (expectedVersion != null
              && expectedVersion != number(memory(projectId, id), "version")) conflict();
          memory(projectId, id);
          coordinator.assertNotReferenced(users.currentUserId(), "memory:" + id);
          jdbc.update("delete from project_memory_source where memory_id=?", id);
          jdbc.update(
              "delete from project_memory where id=? and owner_id=?", id, users.currentUserId());
          return null;
        });
  }

  public Map<String, Object> conversationSettings(String id) {
    users.requireOwnedConversation(id);
    var result =
        required(
            query(
                "select id,settings_version as version,project_id,use_preferences,use_project_context,system_prompt from conversation where id=? and owner_id=?",
                id,
                users.currentUserId()),
            "Conversation");
    result.put("contextUsed", latestContextUsed(id));
    return result;
  }

  public Map<String, Object> saveConversationSettings(String id, Map<String, Object> input) {
    return mutate(
        () -> {
          users.assertConversationAccessible(id, true);
          coordinator.assertIdle(users.currentUserId(), id);
          var existing = conversations.findById(id);
          if (existing.isEmpty()) {
            if (number(input, "version") != 0) conflict();
            Conversation c = new Conversation(id);
            c.setOwnerId(users.currentUserId());
            conversations.saveAndFlush(c);
          }
          var old = conversationSettings(id);
          checkVersion(input, old);
          String projectId =
              input.containsKey("projectId")
                  ? nullableText(input, "projectId", 255)
                  : (String) old.get("projectId");
          if (projectId != null) project(projectId);
          if (!Objects.equals(projectId, old.get("projectId")))
            jdbc.update("delete from project_memory_source where source_conversation_id=?", id);
          String prompt =
              input.containsKey("systemPrompt")
                  ? nullableText(input, "systemPrompt", 8000)
                  : (String) old.get("systemPrompt");
          jdbc.update(
              "update conversation set project_id=?,use_preferences=?,use_project_context=?,system_prompt=?,settings_version=settings_version+1 where id=? and owner_id=?",
              projectId,
              bool(input, "usePreferences", Boolean.TRUE.equals(old.get("usePreferences"))),
              bool(input, "useProjectContext", Boolean.TRUE.equals(old.get("useProjectContext"))),
              prompt,
              id,
              users.currentUserId());
          return conversationSettings(id);
        });
  }

  public void recordContextUsed(
      String conversationId, String executionId, Map<String, Object> metadata) {
    users.requireOwnedConversation(conversationId);
    jdbc.update(
        "insert into context_use(execution_id,conversation_id,metadata_json,created_at) values(?,?,?,?)",
        executionId,
        conversationId,
        json.writeValueAsString(metadata),
        now());
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> latestContextUsed(String id) {
    users.requireOwnedConversation(id);
    var rows =
        jdbc.queryForList(
            "select metadata_json from context_use where conversation_id=? order by created_at desc limit 1",
            String.class,
            id);
    return rows.isEmpty() ? Map.of() : json.readValue(rows.getFirst(), Map.class);
  }

  private List<Map<String, Object>> query(String sql, Object... args) {
    return jdbc.queryForList(sql, args).stream().map(MemoryService::camel).toList();
  }

  private static Map<String, Object> camel(Map<String, Object> row) {
    var result = new LinkedHashMap<String, Object>();
    row.forEach(
        (key, value) -> {
          StringBuilder field = new StringBuilder();
          boolean upper = false;
          for (char ch : key.toCharArray()) {
            if (ch == '_') {
              upper = true;
            } else {
              field.append(upper ? Character.toUpperCase(ch) : ch);
              upper = false;
            }
          }
          String name = field.toString();
          Object normalized = value;
          if (Set.of("usePreferences", "useProjectContext", "pinned").contains(name)
              && value instanceof Number n) normalized = n.intValue() != 0;
          result.put(name, normalized);
        });
    return result;
  }

  private static Map<String, Object> required(List<Map<String, Object>> rows, String type) {
    if (rows.isEmpty()) throw missing(type);
    return rows.getFirst();
  }

  private static String text(Map<String, Object> input, String key, int limit) {
    Object value = input.get(key);
    if (value == null) return "";
    if (!(value instanceof String)) bad(key + " must be text");
    String result = value.toString().trim();
    if (result.length() > limit) bad(key + " exceeds " + limit + " characters");
    return result;
  }

  private static String nullableText(Map<String, Object> input, String key, int limit) {
    String value = text(input, key, limit);
    return value.isEmpty() ? null : value;
  }

  private static boolean bool(Map<String, Object> input, String key, boolean fallback) {
    if (!input.containsKey(key)) return fallback;
    if (!(input.get(key) instanceof Boolean)) bad(key + " must be boolean");
    return Boolean.TRUE.equals(input.get(key));
  }

  private static long number(Map<String, Object> input, String key) {
    Object value = input.get(key);
    if (!(value instanceof Number)) bad(key + " is required and must be numeric");
    Number number = (Number) value;
    long result;
    try {
      result = new java.math.BigDecimal(number.toString()).longValueExact();
    } catch (ArithmeticException | NumberFormatException error) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, key + " must be a non-negative integer");
    }
    if (result < 0) bad(key + " must be a non-negative integer");
    return result;
  }

  private static void checkVersion(Map<String, Object> input, Map<String, Object> old) {
    if (number(input, "version") != number(old, "version")) conflict();
  }

  private static void conflict() {
    throw new ResponseStatusException(
        HttpStatus.CONFLICT, "Settings changed. Reload before saving.");
  }

  private static void bad(String message) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private static ResponseStatusException missing(String type) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, type + " unavailable");
  }

  private static String now() {
    return Instant.now().toString();
  }
}
