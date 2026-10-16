package com.example.chatapp.config.migration;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Reproduces the released schema on empty installations before the ownership upgrade. */
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
    value = "SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE",
    justification = "Only the bundled immutable legacy DDL resource is executed.")
public class V1__LegacyBaseline extends BaseJavaMigration {
  @Override
  public Integer getChecksum() {
    return 1;
  }

  @Override
  public void migrate(Context context) throws Exception {
    try (var input = V1__LegacyBaseline.class.getResourceAsStream("/db/legacy-baseline.sql")) {
      if (input == null) throw new SQLException("Missing legacy schema resource");
      String script = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      try (var statement = context.getConnection().createStatement()) {
        for (String sql : script.split(";")) if (!sql.isBlank()) statement.execute(sql);
      }
    }
  }
}
