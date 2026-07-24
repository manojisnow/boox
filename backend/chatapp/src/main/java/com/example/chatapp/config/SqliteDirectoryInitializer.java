package com.example.chatapp.config;

import java.io.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Ensures the parent directory of the SQLite database file exists before the datasource is created.
 * SQLite's JDBC driver fails to open a database whose directory is missing. Runs early (before the
 * application context) via {@code META-INF/spring.factories}.
 */
public class SqliteDirectoryInitializer implements EnvironmentPostProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(SqliteDirectoryInitializer.class);
  private static final String SQLITE_PREFIX = "jdbc:sqlite:";

  @Override
  public void postProcessEnvironment(
      final ConfigurableEnvironment environment, final SpringApplication application) {
    final String url = environment.getProperty("spring.datasource.url");
    if (url == null || !url.startsWith(SQLITE_PREFIX)) {
      return;
    }
    String path = url.substring(SQLITE_PREFIX.length());
    final int query = path.indexOf('?');
    if (query >= 0) {
      path = path.substring(0, query);
    }
    // Skip in-memory databases (":memory:", "file::memory:...").
    if (path.isEmpty() || path.startsWith(":") || path.contains(":memory:")) {
      return;
    }
    final File parent = new File(path).getAbsoluteFile().getParentFile();
    if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
      LOGGER.warn("Could not create SQLite database directory: {}", parent);
    }
  }
}
