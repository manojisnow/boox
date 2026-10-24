import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

/** Standalone feasibility probe. Uses only an in-memory synthetic database, never Ullavox data. */
public class SqliteOwnershipProbe {
  private static int passed;

  public static void main(String[] args) throws Exception {
    Class.forName("org.sqlite.JDBC");
    try (Connection db = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      System.out.println("SQLite " + value(db, "select sqlite_version()"));
      sql(db, "PRAGMA foreign_keys=ON");
      check("FK enforcement enabled", value(db, "PRAGMA foreign_keys").equals("1"));
      sql(db, "CREATE TABLE conversation (id TEXT PRIMARY KEY, title TEXT NOT NULL, summary TEXT, summarized_count INTEGER NOT NULL DEFAULT 0, system_prompt TEXT, web_session_id TEXT, temperature REAL, num_ctx INTEGER, stop_sequences TEXT)");
      sql(db, "CREATE TABLE chat_message (id INTEGER PRIMARY KEY, conversation_id TEXT NOT NULL REFERENCES conversation(id), content TEXT, images TEXT)");
      sql(db, "CREATE TABLE agent_run (id TEXT PRIMARY KEY, conversation_id TEXT NOT NULL, request_json TEXT, final_output TEXT)");
      sql(db, "INSERT INTO conversation VALUES ('c1','Legacy chat','old summary',2,'brief','opaque-session',0.3,8192,'[\"STOP\"]')");
      sql(db, "INSERT INTO chat_message VALUES (71,'c1','synthetic message','[\"synthetic-image\"]')");
      sql(db, "INSERT INTO agent_run VALUES ('r1','c1','{\"attachments\":[]}', 'synthetic result')");
      String before = snapshot(db);
      String owner = UUID.randomUUID().toString();
      boolean failed = false;
      try {
        migrate(db, owner, true);
      } catch (SQLException expected) {
        failed = true;
      }
      check("injected migration failure rolls back data and DDL", failed && before.equals(snapshot(db)) && value(db, "SELECT count(*) FROM sqlite_master WHERE name='local_user'").equals("0"));
      migrate(db, owner, false);
      check("legacy values and child rows survive rebuild", before.equals(snapshot(db)));
      check("existing chat assigned to local owner", value(db, "SELECT owner_id FROM conversation WHERE id='c1'").equals(owner));
      migrate(db, "must-not-replace-owner", false);
      check("repeat migration preserves one stable owner", value(db, "SELECT count(*) FROM local_user").equals("1") && value(db, "SELECT local_user_id FROM installation").equals(owner));
      expectRejected(db, "INSERT INTO conversation(id,title,owner_id) VALUES ('missing-owner','bad',NULL)", "missing owner rejected");
      sql(db, "INSERT INTO local_user VALUES ('other-user')");
      sql(db, "INSERT INTO project VALUES ('p1','" + owner + "','Ullavox')");
      expectRejected(db, "INSERT INTO conversation(id,title,owner_id,project_id) VALUES ('cross-owner','bad','other-user','p1')", "cross-owner project assignment rejected");
      sql(db, "UPDATE conversation SET project_id='p1' WHERE id='c1'");
      sql(db, "INSERT INTO conversation(id,title,owner_id,project_id) VALUES ('c2','Other chat','" + owner + "','p1')");
      sql(db, "INSERT INTO project_memory VALUES ('m1','p1','" + owner + "','message','Explicit saved decision')");
      sql(db, "INSERT INTO project_memory VALUES ('manual','p1','" + owner + "','manual','Manually written note')");
      expectRejected(db, "INSERT INTO project_memory_source VALUES ('m1','p1','" + owner + "','c2',71)", "source message must belong to source conversation");
      expectRejected(db, "INSERT INTO project_memory_source VALUES ('m1','p1','" + owner + "','c1',NULL)", "partial provenance rejected");
      sql(db, "INSERT INTO project_memory_source VALUES ('m1','p1','" + owner + "','c1',71)");
      sql(db, "UPDATE project_memory SET content='Edited saved decision' WHERE id='m1'");
      sql(db, "DELETE FROM chat_message WHERE id=71");
      check("source removal preserves edited and manual notes", value(db, "SELECT content FROM project_memory WHERE id='m1'").equals("Edited saved decision") && value(db, "SELECT count(*) FROM project_memory WHERE id='manual'").equals("1"));
      check("source removal clears provenance only", value(db, "SELECT count(*) FROM project_memory_source").equals("0") && value(db, "SELECT origin FROM project_memory WHERE id='m1'").equals("message"));
      sql(db, "INSERT INTO project VALUES ('p2','" + owner + "','Other project')");
      sql(db, "INSERT INTO chat_message VALUES (72,'c2','source for move',NULL)");
      sql(db, "INSERT INTO project_memory VALUES ('move-note','p1','" + owner + "','message','Keep in original project')");
      sql(db, "INSERT INTO project_memory_source VALUES ('move-note','p1','" + owner + "','c2',72)");
      db.setAutoCommit(false);
      sql(db, "DELETE FROM project_memory_source WHERE source_conversation_id='c2'");
      sql(db, "UPDATE conversation SET project_id='p2' WHERE id='c2'");
      db.commit();
      db.setAutoCommit(true);
      check("moving source preserves note in original project with no source link", value(db, "SELECT project_id FROM project_memory WHERE id='move-note'").equals("p1") && value(db, "SELECT count(*) FROM project_memory_source WHERE memory_id='move-note'").equals("0"));
      sql(db, "DELETE FROM agent_run WHERE conversation_id='c1'");
      sql(db, "DELETE FROM conversation WHERE id='c1'");
      check("deleting source conversation preserves all saved notes", value(db, "SELECT count(*) FROM project_memory").equals("3"));
      sql(db, "DELETE FROM project WHERE id='p1'");
      check("deleting owning project removes notes without deleting other-project chat", value(db, "SELECT count(*) FROM project_memory").equals("0") && value(db, "SELECT count(*) FROM conversation WHERE id='c2'").equals("1"));
      check("foreign-key check clean", value(db, "SELECT count(*) FROM pragma_foreign_key_check").equals("0"));
      check("integrity check clean", value(db, "PRAGMA integrity_check").equals("ok"));
      check("foreign keys still enabled after migrations", value(db, "PRAGMA foreign_keys").equals("1"));
      System.out.println(passed + " checks passed; no application database opened.");
    }
  }

  private static void migrate(Connection db, String owner, boolean injectFailure) throws SQLException {
    if (value(db, "PRAGMA user_version").equals("1")) return;
    // Rebuild the parent in place without renaming the old parent first. Disable FK enforcement
    // before beginning the transaction; validate all references before committing.
    sql(db, "PRAGMA foreign_keys=OFF");
    db.setAutoCommit(false);
    try {
      sql(db, "CREATE TABLE local_user(id TEXT PRIMARY KEY NOT NULL)");
      sql(db, "INSERT INTO local_user VALUES ('" + owner + "')");
      sql(db, "CREATE TABLE installation(singleton INTEGER PRIMARY KEY CHECK(singleton=1), local_user_id TEXT NOT NULL REFERENCES local_user(id))");
      sql(db, "INSERT INTO installation VALUES (1,'" + owner + "')");
      sql(db, "CREATE TABLE project(id TEXT PRIMARY KEY NOT NULL, owner_id TEXT NOT NULL REFERENCES local_user(id), name TEXT NOT NULL, UNIQUE(id,owner_id))");
      sql(db, "CREATE TABLE conversation_new(id TEXT PRIMARY KEY, title TEXT NOT NULL, summary TEXT, summarized_count INTEGER NOT NULL DEFAULT 0, system_prompt TEXT, web_session_id TEXT, temperature REAL, num_ctx INTEGER, stop_sequences TEXT, owner_id TEXT NOT NULL REFERENCES local_user(id), project_id TEXT, UNIQUE(id,project_id,owner_id), FOREIGN KEY(project_id,owner_id) REFERENCES project(id,owner_id))");
      sql(db, "INSERT INTO conversation_new SELECT *, '" + owner + "', NULL FROM conversation");
      sql(db, "DROP TABLE conversation");
      sql(db, "ALTER TABLE conversation_new RENAME TO conversation");
      sql(db, "CREATE UNIQUE INDEX message_provenance ON chat_message(id,conversation_id)");
      sql(db, "CREATE TABLE project_memory(id TEXT PRIMARY KEY, project_id TEXT NOT NULL, owner_id TEXT NOT NULL, origin TEXT NOT NULL CHECK(origin IN ('manual','message')), content TEXT NOT NULL, UNIQUE(id,project_id,owner_id), FOREIGN KEY(project_id,owner_id) REFERENCES project(id,owner_id) ON DELETE CASCADE)");
      sql(db, "CREATE TABLE project_memory_source(memory_id TEXT PRIMARY KEY, project_id TEXT NOT NULL, owner_id TEXT NOT NULL, source_conversation_id TEXT NOT NULL, source_message_id INTEGER NOT NULL, FOREIGN KEY(memory_id,project_id,owner_id) REFERENCES project_memory(id,project_id,owner_id) ON DELETE CASCADE, FOREIGN KEY(source_conversation_id,project_id,owner_id) REFERENCES conversation(id,project_id,owner_id) ON DELETE CASCADE, FOREIGN KEY(source_message_id,source_conversation_id) REFERENCES chat_message(id,conversation_id) ON DELETE CASCADE)");
      if (injectFailure) sql(db, "INSERT INTO deliberately_missing_table VALUES (1)");
      if (!value(db, "SELECT count(*) FROM pragma_foreign_key_check").equals("0")) throw new SQLException("invalid foreign keys");
      sql(db, "PRAGMA user_version=1");
      db.commit();
    } catch (SQLException error) {
      db.rollback();
      throw error;
    } finally {
      db.setAutoCommit(true);
      sql(db, "PRAGMA foreign_keys=ON");
    }
  }

  private static String snapshot(Connection db) throws SQLException {
    return value(db, "SELECT json_array(id,title,summary,summarized_count,system_prompt,web_session_id,temperature,num_ctx,stop_sequences) FROM conversation WHERE id='c1'")
        + value(db, "SELECT json_array(id,conversation_id,content,images) FROM chat_message WHERE id=71")
        + value(db, "SELECT json_array(id,conversation_id,request_json,final_output) FROM agent_run WHERE id='r1'");
  }

  private static void expectRejected(Connection db, String query, String label) throws SQLException {
    try {
      sql(db, query);
    } catch (SQLException expected) {
      check(label, expected.getErrorCode() == 19);
      return;
    }
    throw new AssertionError(label);
  }

  private static void sql(Connection db, String query) throws SQLException {
    try (var statement = db.createStatement()) { statement.execute(query); }
  }

  private static String value(Connection db, String query) throws SQLException {
    try (var statement = db.createStatement(); var rows = statement.executeQuery(query)) {
      return rows.next() ? rows.getString(1) : "";
    }
  }

  private static void check(String label, boolean success) {
    if (!success) throw new AssertionError(label);
    passed++;
    System.out.println("PASS " + label);
  }
}
