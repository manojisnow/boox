package com.example.chatapp.config.migration;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Adds explicit ownership and independent project notes in a single DDL transaction. */
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
    value = "SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE",
    justification =
        "DDL is versioned application code; preserved index SQL is read only after strict legacy schema validation.")
public class V2__LocalMemory extends BaseJavaMigration {
  @Override
  public Integer getChecksum() {
    return 1;
  }

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    List<String> indexes = new ArrayList<>();
    try (var statement = connection.createStatement();
        var rows =
            statement.executeQuery(
                "select sql from sqlite_master where type='index' and sql is not null and tbl_name in ('conversation','chat_message','agent_run','agent_step')")) {
      while (rows.next()) indexes.add(rows.getString(1));
    }
    execute(
        connection,
        "CREATE TABLE local_user(id varchar(255) PRIMARY KEY NOT NULL,created_at TEXT NOT NULL)",
        "CREATE TABLE installation(id INTEGER PRIMARY KEY CHECK(id=1),local_user_id varchar(255) NOT NULL REFERENCES local_user(id))",
        "CREATE TABLE user_preferences(user_id varchar(255) PRIMARY KEY REFERENCES local_user(id),version BIGINT NOT NULL,onboarding_status TEXT NOT NULL,response_length TEXT NOT NULL,explanation_style TEXT NOT NULL,familiarity TEXT NOT NULL,background TEXT NOT NULL,goals TEXT NOT NULL,instructions TEXT NOT NULL,updated_at TEXT NOT NULL)",
        "CREATE TABLE project(id varchar(255) PRIMARY KEY NOT NULL,owner_id varchar(255) NOT NULL REFERENCES local_user(id),name TEXT NOT NULL,description TEXT NOT NULL,instructions TEXT NOT NULL,version BIGINT NOT NULL,created_at TEXT NOT NULL,updated_at TEXT NOT NULL,UNIQUE(id,owner_id))");
    String owner = UUID.randomUUID().toString();
    try (var insert = connection.prepareStatement("INSERT INTO local_user VALUES (?,?)")) {
      insert.setString(1, owner);
      insert.setString(2, java.time.Instant.now().toString());
      insert.executeUpdate();
    }
    try (var insert = connection.prepareStatement("INSERT INTO installation VALUES(1,?)")) {
      insert.setString(1, owner);
      insert.executeUpdate();
    }
    execute(
        connection,
        "CREATE TABLE conversation_new(num_ctx integer,summarized_count integer not null default 0,temperature float,created_at timestamp not null,updated_at timestamp not null,id varchar(255) PRIMARY KEY NOT NULL,model varchar(255),server varchar(255),stop_sequences text,summary text,system_prompt text,title varchar(255) NOT NULL,web_session_id varchar(255),owner_id varchar(255) NOT NULL REFERENCES local_user(id),project_id varchar(255),use_preferences boolean NOT NULL DEFAULT true,use_project_context boolean NOT NULL DEFAULT true,settings_version bigint NOT NULL DEFAULT 0,UNIQUE(id,project_id,owner_id),FOREIGN KEY(project_id,owner_id) REFERENCES project(id,owner_id))",
        "CREATE TABLE chat_message_new(seq integer not null,created_at timestamp not null,id integer PRIMARY KEY AUTOINCREMENT,content text,conversation_id varchar(255) NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,images text,role varchar(255) not null,UNIQUE(id,conversation_id))",
        "CREATE TABLE agent_run_new(created_at timestamp NOT NULL,updated_at timestamp NOT NULL,version bigint NOT NULL,conversation_id varchar(255) NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,error text,final_output text,goal text NOT NULL,id varchar(255) PRIMARY KEY NOT NULL,model varchar(255) NOT NULL,request_json text,server varchar(255) NOT NULL,status varchar(255) NOT NULL CHECK(status IN ('QUEUED','RUNNING','WAITING_FOR_APPROVAL','CANCEL_REQUESTED','COMPLETED','FAILED','CANCELLED')),context_used_json text)",
        "CREATE TABLE agent_step_new(sequence integer NOT NULL,completed_at timestamp,created_at timestamp NOT NULL,id integer PRIMARY KEY,agent_run_id varchar(255) NOT NULL REFERENCES agent_run(id) ON DELETE CASCADE,detail text,status varchar(255) NOT NULL CHECK(status IN ('PENDING','RUNNING','COMPLETED','FAILED','SKIPPED')),summary text NOT NULL,type varchar(255) NOT NULL CHECK(type IN ('PLAN','MODEL','TOOL','APPROVAL','FINAL')))");
    try (var insert =
        connection.prepareStatement(
            "INSERT INTO conversation_new SELECT num_ctx,summarized_count,temperature,created_at,updated_at,id,model,server,stop_sequences,summary,system_prompt,title,web_session_id,?,NULL,true,true,0 FROM conversation")) {
      insert.setString(1, owner);
      insert.executeUpdate();
    }
    execute(
        connection,
        "INSERT INTO chat_message_new SELECT seq,created_at,id,content,conversation_id,images,role FROM chat_message",
        "INSERT INTO agent_run_new SELECT created_at,updated_at,coalesce(version,0),conversation_id,error,final_output,goal,id,model,request_json,server,status,NULL FROM agent_run",
        "INSERT INTO agent_step_new SELECT sequence,completed_at,created_at,id,agent_run_id,detail,status,summary,type FROM agent_step",
        "DROP TABLE agent_step",
        "DROP TABLE chat_message",
        "DROP TABLE agent_run",
        "DROP TABLE conversation",
        "ALTER TABLE conversation_new RENAME TO conversation",
        "ALTER TABLE chat_message_new RENAME TO chat_message",
        "ALTER TABLE agent_run_new RENAME TO agent_run",
        "ALTER TABLE agent_step_new RENAME TO agent_step");
    for (String index : indexes) execute(connection, index);
    execute(
        connection,
        "CREATE INDEX conversation_owner_updated ON conversation(owner_id,updated_at)",
        "CREATE INDEX conversation_project ON conversation(project_id,owner_id)",
        "CREATE INDEX agent_run_conversation ON agent_run(conversation_id,created_at)",
        "CREATE INDEX chat_message_conversation_seq ON chat_message(conversation_id,seq)",
        "CREATE TABLE project_memory(id varchar(255) PRIMARY KEY NOT NULL,project_id varchar(255) NOT NULL,owner_id varchar(255) NOT NULL,title TEXT NOT NULL,content TEXT NOT NULL,kind TEXT NOT NULL,origin TEXT NOT NULL,pinned INTEGER NOT NULL,version BIGINT NOT NULL,created_at TEXT NOT NULL,updated_at TEXT NOT NULL,FOREIGN KEY(project_id,owner_id) REFERENCES project(id,owner_id) ON DELETE CASCADE)",
        "CREATE TABLE project_memory_source(memory_id varchar(255) PRIMARY KEY NOT NULL REFERENCES project_memory(id) ON DELETE CASCADE,source_conversation_id varchar(255) NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,source_message_id INTEGER NOT NULL,FOREIGN KEY(source_message_id,source_conversation_id) REFERENCES chat_message(id,conversation_id) ON DELETE CASCADE)",
        "CREATE TABLE context_use(execution_id varchar(255) PRIMARY KEY NOT NULL,conversation_id varchar(255) NOT NULL REFERENCES conversation(id) ON DELETE CASCADE,metadata_json TEXT NOT NULL,created_at TEXT NOT NULL)",
        "CREATE INDEX context_use_conversation ON context_use(conversation_id,created_at)");
    String predicate =
        " NOT EXISTS (SELECT 1 FROM project_memory m JOIN conversation c ON c.id=NEW.source_conversation_id AND c.project_id=m.project_id AND c.owner_id=m.owner_id JOIN chat_message s ON s.id=NEW.source_message_id AND s.conversation_id=c.id WHERE m.id=NEW.memory_id AND m.origin='message') ";
    execute(
        connection,
        "CREATE TRIGGER validate_memory_source_insert BEFORE INSERT ON project_memory_source WHEN"
            + predicate
            + "BEGIN SELECT RAISE(ABORT,'Source does not belong to the same project and owner'); END",
        "CREATE TRIGGER validate_memory_source_update BEFORE UPDATE ON project_memory_source WHEN"
            + predicate
            + "BEGIN SELECT RAISE(ABORT,'Source does not belong to the same project and owner'); END");
    try (var statement = connection.createStatement();
        var rows = statement.executeQuery("PRAGMA foreign_key_check")) {
      if (rows.next())
        throw new java.sql.SQLException("Migration would create invalid relationships");
    }
  }

  private static void execute(Connection connection, String... statements)
      throws java.sql.SQLException {
    try (var statement = connection.createStatement()) {
      for (String sql : statements) statement.execute(sql);
    }
  }
}
