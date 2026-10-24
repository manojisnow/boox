package com.example.chatapp.config;

import com.example.chatapp.config.migration.V1__LegacyBaseline;
import com.example.chatapp.config.migration.V2__LocalMemory;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/** Explicit adoption of the supported legacy schema; migration connections never enter the pool. */
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
    value = "SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE",
    justification =
        "Table names are a fixed whitelist and statements come from versioned migration resources, never request input.")
public final class SchemaMigrator {
  private static final Object MIGRATION_LOCK = new Object();

  private SchemaMigrator() {}

  public static void migrate(DataSource applicationDataSource) {
    synchronized (MIGRATION_LOCK) {
      migrateLocked(applicationDataSource);
    }
  }

  private static void migrateLocked(DataSource applicationDataSource) {
    try {
      String url;
      boolean legacy;
      try (Connection connection = applicationDataSource.getConnection()) {
        url = connection.getMetaData().getURL();
        Set<String> tables = tables(connection);
        legacy = !tables.isEmpty() && !tables.contains("flyway_schema_history");
        if (legacy) validateLegacy(connection, tables);
      }
      SQLiteConfig config = new SQLiteConfig();
      config.enforceForeignKeys(false);
      config.setBusyTimeout(10000);
      SQLiteDataSource migrations = new SQLiteDataSource(config);
      migrations.setUrl(url);
      Flyway flyway =
          Flyway.configure()
              .dataSource(migrations)
              .locations(new String[0])
              .javaMigrations(new V1__LegacyBaseline(), new V2__LocalMemory())
              .baselineVersion("1")
              .baselineOnMigrate(false)
              .cleanDisabled(true)
              .load();
      if (legacy) flyway.baseline();
      flyway.migrate();
      try (Connection connection = applicationDataSource.getConnection();
          var statement = connection.createStatement();
          var rows = statement.executeQuery("PRAGMA foreign_key_check")) {
        if (rows.next())
          throw new SQLException("Database contains invalid relationships after migration");
      }
    } catch (SQLException exception) {
      throw new IllegalStateException("Cannot migrate the local database safely", exception);
    }
  }

  private static Set<String> tables(Connection connection) throws SQLException {
    Set<String> result = new HashSet<>();
    try (var statement = connection.createStatement();
        var rows =
            statement.executeQuery(
                "select name from sqlite_master where type='table' and name not like 'sqlite_%'")) {
      while (rows.next()) result.add(rows.getString(1));
    }
    return result;
  }

  private static void validateLegacy(Connection connection, Set<String> tables)
      throws SQLException {
    Map<String, Set<String>> expected =
        Map.of(
            "conversation",
                Set.of(
                    "num_ctx",
                    "summarized_count",
                    "temperature",
                    "created_at",
                    "updated_at",
                    "id",
                    "model",
                    "server",
                    "stop_sequences",
                    "summary",
                    "system_prompt",
                    "title",
                    "web_session_id"),
            "chat_message",
                Set.of("seq", "created_at", "id", "content", "conversation_id", "images", "role"),
            "agent_run",
                Set.of(
                    "created_at",
                    "updated_at",
                    "version",
                    "conversation_id",
                    "error",
                    "final_output",
                    "goal",
                    "id",
                    "model",
                    "request_json",
                    "server",
                    "status"),
            "agent_step",
                Set.of(
                    "sequence",
                    "completed_at",
                    "created_at",
                    "id",
                    "agent_run_id",
                    "detail",
                    "status",
                    "summary",
                    "type"));
    if (!tables.equals(expected.keySet()))
      throw new IllegalStateException(
          "Unsupported database schema; restore or migrate a supported Ullavox version first");
    for (var entry : expected.entrySet()) {
      Set<String> columns = new HashSet<>();
      try (var statement = connection.createStatement();
          var rows = statement.executeQuery("PRAGMA table_info(" + entry.getKey() + ")")) {
        while (rows.next()) columns.add(rows.getString("name"));
      }
      if (!columns.equals(entry.getValue()))
        throw new IllegalStateException("Unsupported legacy columns in " + entry.getKey());
    }
    try (Connection reference = java.sql.DriverManager.getConnection("jdbc:sqlite::memory:");
        var resource = SchemaMigrator.class.getResourceAsStream("/db/legacy-baseline.sql")) {
      if (resource == null) throw new SQLException("Missing reference schema");
      String script = new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
      try (var statement = reference.createStatement()) {
        for (String sql : script.split(";")) if (!sql.isBlank()) statement.execute(sql);
      }
      for (String table : expected.keySet()) {
        if (!columnShape(reference, table).equals(normalizedLegacyShape(connection, table))
            || !constraints(reference, table).equals(constraints(connection, table)))
          throw new IllegalStateException(
              "Unsupported legacy column types or constraints in " + table);
      }
    } catch (java.io.IOException error) {
      throw new SQLException("Cannot inspect the reference schema", error);
    }
    try (var statement = connection.createStatement();
        var rows =
            statement.executeQuery(
                "select count(*) from sqlite_master where type in ('view','trigger')")) {
      if (rows.next() && rows.getInt(1) != 0)
        throw new IllegalStateException("Custom views/triggers require an explicit migration");
    }
    for (String sql :
        new String[] {
          "select count(*) from chat_message m left join conversation c on c.id=m.conversation_id where c.id is null",
          "select count(*) from agent_run r left join conversation c on c.id=r.conversation_id where c.id is null",
          "select count(*) from agent_step s left join agent_run r on r.id=s.agent_run_id where r.id is null"
        }) {
      try (var statement = connection.createStatement();
          var rows = statement.executeQuery(sql)) {
        if (rows.next() && rows.getInt(1) != 0)
          throw new IllegalStateException(
              "Legacy database contains orphaned records; no migration performed");
      }
    }
  }

  private static Map<String, String> columnShape(Connection connection, String table)
      throws SQLException {
    Map<String, String> result = new java.util.HashMap<>();
    try (var statement = connection.createStatement();
        var rows = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
      while (rows.next())
        result.put(
            rows.getString("name"),
            rows.getString("type").toLowerCase(java.util.Locale.ROOT)
                + ":"
                + rows.getInt("notnull")
                + ":"
                + rows.getInt("pk")
                + ":"
                + java.util.Objects.toString(rows.getString("dflt_value"), ""));
    }
    return result;
  }

  private static Map<String, String> normalizedLegacyShape(Connection connection, String table)
      throws SQLException {
    Map<String, String> shape = columnShape(connection, table);
    // Released Hibernate schemas differ only in @Version nullability. Preserve this
    // recognized variant; V2 explicitly initializes null legacy versions to zero.
    if (table.equals("agent_run") && "bigint:0:0:".equals(shape.get("version")))
      shape.put("version", "bigint:1:0:");
    return shape;
  }

  private static Set<String> constraints(Connection connection, String table) throws SQLException {
    Set<String> result = new HashSet<>();
    try (var statement = connection.createStatement();
        var rows = statement.executeQuery("PRAGMA foreign_key_list(" + table + ")")) {
      while (rows.next())
        result.add(
            "foreign:"
                + rows.getString("table")
                + ":"
                + rows.getString("from")
                + ":"
                + rows.getString("to")
                + ":"
                + rows.getString("on_update")
                + ":"
                + rows.getString("on_delete"));
    }
    try (var statement = connection.createStatement();
        var rows = statement.executeQuery("PRAGMA index_list(" + table + ")")) {
      while (rows.next())
        if (rows.getInt("unique") == 1 && !"pk".equals(rows.getString("origin")))
          result.add("unique:" + rows.getString("name"));
    }
    String ddl;
    try (var statement =
        connection.prepareStatement(
            "select sql from sqlite_master where type='table' and name=?")) {
      statement.setString(1, table);
      try (var rows = statement.executeQuery()) {
        ddl =
            rows.next()
                ? rows.getString(1).replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT)
                : "";
      }
    }
    int start = ddl.indexOf("check(");
    while (start >= 0) {
      int end = start + 6, depth = 1;
      while (end < ddl.length() && depth > 0) {
        char ch = ddl.charAt(end++);
        if (ch == '(') depth++;
        if (ch == ')') depth--;
      }
      result.add("check:" + canonicalPredicate(ddl.substring(start + 6, end - 1)));
      start = ddl.indexOf("check(", end);
    }
    return result;
  }

  private static String canonicalPredicate(String expression) {
    String result = expression;
    while (result.startsWith("(") && result.endsWith(")")) {
      int depth = 0;
      boolean wrapsAll = true;
      for (int index = 0; index < result.length() - 1; index++) {
        char ch = result.charAt(index);
        if (ch == '(') depth++;
        if (ch == ')') depth--;
        if (depth == 0) {
          wrapsAll = false;
          break;
        }
      }
      if (!wrapsAll) break;
      result = result.substring(1, result.length() - 1);
    }
    return result;
  }
}
