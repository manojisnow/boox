package com.example.chatapp.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

class SchemaMigratorTest {
  @TempDir Path temporary;

  private SQLiteDataSource source(String name) {
    SQLiteConfig config = new SQLiteConfig();
    config.enforceForeignKeys(true);
    SQLiteDataSource source = new SQLiteDataSource(config);
    source.setUrl("jdbc:sqlite:" + temporary.resolve(name + ".db"));
    return source;
  }

  private JdbcTemplate legacy(SQLiteDataSource source) throws Exception {
    return legacy(source, false);
  }

  private JdbcTemplate legacy(SQLiteDataSource source, boolean nullableAgentVersion)
      throws Exception {
    return legacy(source, nullableAgentVersion, java.util.function.UnaryOperator.identity());
  }

  private JdbcTemplate legacy(
      SQLiteDataSource source,
      boolean nullableAgentVersion,
      java.util.function.UnaryOperator<String> transform)
      throws Exception {
    String schema;
    try (var resource = getClass().getResourceAsStream("/db/legacy-baseline.sql")) {
      assertNotNull(resource);
      schema = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
    }
    if (nullableAgentVersion)
      schema =
          schema
              .replace("version bigint not null", "version bigint")
              .replace("check ((", "check (")
              .replace("')))", "'))");
    schema = transform.apply(schema);
    JdbcTemplate jdbc = new JdbcTemplate(source);
    for (String statement : schema.split(";")) if (!statement.isBlank()) jdbc.execute(statement);
    return jdbc;
  }

  private void seed(JdbcTemplate jdbc) {
    jdbc.update(
        "insert into conversation values(4096,2,0.4,1000,2000,'chat','model','ollama','[\"STOP\"]','running summary','custom prompt','Saved chat','provider-session')");
    jdbc.update(
        "insert into chat_message values(0,1000,42,'original user message','chat','[\"image-data\"]','user')");
    jdbc.update(
        "insert into agent_run values(1000,2000,3,'chat',null,'checked answer','goal','run','model','{\"attachments\":[{\"name\":\"file.txt\",\"content\":\"retained content\"}]}','ollama','COMPLETED')");
    jdbc.update(
        "insert into agent_step values(0,2000,1000,7,'run','retrieved evidence','COMPLETED','Search result: synthetic','TOOL')");
    jdbc.execute("create index legacy_message_order on chat_message(conversation_id,seq)");
  }

  private Set<String> tables(JdbcTemplate jdbc) {
    return new HashSet<>(
        jdbc.queryForList(
            "select name from sqlite_master where type='table' and name not like 'sqlite_%'",
            String.class));
  }

  @Test
  void freshMigrationIsRepeatableAndApplicationConnectionsAlwaysEnforceForeignKeys()
      throws Exception {
    SQLiteDataSource source = source("fresh");
    SchemaMigrator.migrate(source);
    JdbcTemplate jdbc = new JdbcTemplate(source);
    String owner =
        jdbc.queryForObject("select local_user_id from installation where id=1", String.class);
    assertNotNull(owner);
    assertEquals(1, jdbc.queryForObject("select count(*) from local_user", Integer.class));
    SchemaMigrator.migrate(source);
    assertEquals(
        owner,
        jdbc.queryForObject("select local_user_id from installation where id=1", String.class));
    try (Connection first = source.getConnection();
        Connection second = source.getConnection()) {
      for (Connection connection : List.of(first, second))
        try (var statement = connection.createStatement();
            var row = statement.executeQuery("pragma foreign_keys")) {
          assertTrue(row.next());
          assertEquals(1, row.getInt(1));
        }
    }
    assertTrue(jdbc.queryForList("pragma foreign_key_check").isEmpty());
    assertThrows(
        DataAccessException.class,
        () ->
            jdbc.update(
                "insert into agent_run(created_at,updated_at,version,conversation_id,goal,id,model,server,status) values(0,0,0,'missing','goal','bad','model','ollama','QUEUED')"));
  }

  @Test
  void populatedLegacyUpgradePreservesAllOldFieldsAndAssignsOneStableOwner() throws Exception {
    SQLiteDataSource source = source("legacy");
    JdbcTemplate jdbc = legacy(source);
    seed(jdbc);
    Map<String, Object> chat = jdbc.queryForMap("select * from conversation where id='chat'");
    Map<String, Object> message = jdbc.queryForMap("select * from chat_message where id=42");
    Map<String, Object> run = jdbc.queryForMap("select * from agent_run where id='run'");
    Map<String, Object> step = jdbc.queryForMap("select * from agent_step where id=7");
    SchemaMigrator.migrate(source);
    Map<String, Object> upgraded = jdbc.queryForMap("select * from conversation where id='chat'");
    chat.forEach((key, value) -> assertEquals(value, upgraded.get(key), key));
    assertEquals(message, jdbc.queryForMap("select * from chat_message where id=42"));
    Map<String, Object> upgradedRun = jdbc.queryForMap("select * from agent_run where id='run'");
    run.forEach((key, value) -> assertEquals(value, upgradedRun.get(key), key));
    assertEquals(step, jdbc.queryForMap("select * from agent_step where id=7"));
    assertEquals(
        jdbc.queryForObject("select local_user_id from installation", String.class),
        upgraded.get("owner_id"));
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from sqlite_master where name='legacy_message_order'", Integer.class));
    assertTrue(jdbc.queryForList("pragma foreign_key_check").isEmpty());
    SchemaMigrator.migrate(source);
    assertEquals(1, jdbc.queryForObject("select count(*) from local_user", Integer.class));
  }

  @Test
  void unknownSchemaAndOrphansFailBeforeAnyAdoptionWrites() throws Exception {
    SQLiteDataSource unknown = source("unknown");
    JdbcTemplate custom = new JdbcTemplate(unknown);
    custom.execute("create table valuable(id integer primary key,content text)");
    custom.update("insert into valuable values(1,'preserve me')");
    assertThrows(IllegalStateException.class, () -> SchemaMigrator.migrate(unknown));
    assertEquals(Set.of("valuable"), tables(custom));
    assertEquals(
        "preserve me", custom.queryForObject("select content from valuable", String.class));
    SQLiteDataSource orphan = source("orphan");
    JdbcTemplate legacy = legacy(orphan);
    legacy.update(
        "insert into agent_run values(0,0,0,'missing',null,null,'goal','orphan','model',null,'ollama','QUEUED')");
    Set<String> before = tables(legacy);
    assertThrows(IllegalStateException.class, () -> SchemaMigrator.migrate(orphan));
    assertEquals(before, tables(legacy));
    assertFalse(tables(legacy).contains("flyway_schema_history"));
    assertEquals(1, legacy.queryForObject("select count(*) from agent_run", Integer.class));
  }

  @Test
  void injectedFailureDuringRebuildRollsBackCopiedTablesAndCanBeRetried() throws Exception {
    SQLiteDataSource source = source("rollback");
    JdbcTemplate jdbc = legacy(source);
    seed(jdbc);
    // A supported legacy index collides only near the end of V2, after table copies/renames.
    jdbc.execute("create index conversation_owner_updated on conversation(title)");
    assertThrows(RuntimeException.class, () -> SchemaMigrator.migrate(source));
    assertEquals(
        "original user message",
        jdbc.queryForObject("select content from chat_message where id=42", String.class));
    assertEquals(
        "retained content",
        new tools.jackson.databind.ObjectMapper()
            .readTree(
                jdbc.queryForObject(
                    "select request_json from agent_run where id='run'", String.class))
            .path("attachments")
            .get(0)
            .path("content")
            .asText());
    assertFalse(tables(jdbc).contains("local_user"));
    assertTrue(
        jdbc.queryForList("pragma table_info(conversation)").stream()
            .noneMatch(row -> "owner_id".equals(row.get("name"))));
    jdbc.execute("drop index conversation_owner_updated");
    SchemaMigrator.migrate(source);
    assertNotNull(
        jdbc.queryForObject("select owner_id from conversation where id='chat'", String.class));
  }

  @Test
  void recognizedLegacyNullableAgentVersionPreservesEvidenceAndBackfillsZero() throws Exception {
    SQLiteDataSource source = source("nullable-version");
    JdbcTemplate jdbc = legacy(source, true);
    seed(jdbc);
    jdbc.update("update agent_run set version=null where id='run'");
    String request =
        jdbc.queryForObject("select request_json from agent_run where id='run'", String.class);
    SchemaMigrator.migrate(source);
    assertEquals(
        0L, jdbc.queryForObject("select version from agent_run where id='run'", Long.class));
    assertEquals(
        request,
        jdbc.queryForObject("select request_json from agent_run where id='run'", String.class));
    assertEquals(
        "retrieved evidence",
        jdbc.queryForObject("select detail from agent_step where id=7", String.class));
    assertEquals(
        "[\"image-data\"]",
        jdbc.queryForObject("select images from chat_message where id=42", String.class));
    assertTrue(jdbc.queryForList("pragma foreign_key_check").isEmpty());
    SchemaMigrator.migrate(source);
    assertEquals(1, jdbc.queryForObject("select count(*) from local_user", Integer.class));
  }

  @Test
  void messageIdsNeverReuseDeletedLegacyHighestSourceId() throws Exception {
    SQLiteDataSource source = source("message-id-lifetime");
    JdbcTemplate jdbc = legacy(source);
    seed(jdbc);
    SchemaMigrator.migrate(source);
    long previous = jdbc.queryForObject("select max(id) from chat_message", Long.class);
    jdbc.update("delete from chat_message where id=?", previous);
    jdbc.update(
        "insert into chat_message(seq,created_at,content,conversation_id,role) values(1,1001,'New message','chat','user')");
    long replacement = jdbc.queryForObject("select max(id) from chat_message", Long.class);
    assertTrue(
        replacement > previous,
        "A stale saved-note source ID must never refer to a replacement message");
  }

  @Test
  void customLegacyCheckForeignKeyAndUniqueConstraintsFailWithoutAdoptionWrites() throws Exception {
    Map<String, java.util.function.UnaryOperator<String>> variants = new LinkedHashMap<>();
    variants.put(
        "check",
        schema ->
            schema.replace(
                "title varchar(255) not null",
                "title varchar(255) not null check(length(title)<100)"));
    variants.put(
        "foreign-key",
        schema ->
            schema.replace(
                "conversation_id varchar(255) not null",
                "conversation_id varchar(255) not null references conversation(id)"));
    variants.put(
        "unique",
        schema ->
            schema.replace("web_session_id varchar(255)", "web_session_id varchar(255) unique"));
    for (var variant : variants.entrySet()) {
      SQLiteDataSource source = source("custom-" + variant.getKey());
      JdbcTemplate jdbc = legacy(source, false, variant.getValue());
      seed(jdbc);
      Set<String> beforeTables = tables(jdbc);
      List<Map<String, Object>> beforeSchema =
          jdbc.queryForList("select name,sql from sqlite_master where type='table' order by name");
      assertThrows(
          IllegalStateException.class, () -> SchemaMigrator.migrate(source), variant.getKey());
      assertEquals(beforeTables, tables(jdbc), variant.getKey());
      assertEquals(
          beforeSchema,
          jdbc.queryForList("select name,sql from sqlite_master where type='table' order by name"),
          variant.getKey());
      assertFalse(tables(jdbc).contains("flyway_schema_history"), variant.getKey());
      assertEquals(
          "original user message",
          jdbc.queryForObject("select content from chat_message where id=42", String.class),
          variant.getKey());
    }
  }
}
