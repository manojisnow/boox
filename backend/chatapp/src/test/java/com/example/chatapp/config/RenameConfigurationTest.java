package com.example.chatapp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

class RenameConfigurationTest {
  @ParameterizedTest
  @CsvSource({
    "application.properties, ./data/ullavox.db",
    "application-docker.properties, /app/data/ullavox.db"
  })
  void databasePathCanSelectAnExistingFile(final String resource, final String defaultPath)
      throws IOException {
    final Properties properties = new Properties();
    try (var input = new ClassPathResource(resource).getInputStream()) {
      properties.load(input);
    }
    final String template = properties.getProperty("spring.datasource.url");
    final MockEnvironment environment = new MockEnvironment();
    assertEquals("jdbc:sqlite:" + defaultPath, environment.resolveRequiredPlaceholders(template));
    environment.setProperty("ULLAVOX_DB_PATH", "/existing/conversations.db");
    assertEquals(
        "jdbc:sqlite:/existing/conversations.db",
        environment.resolveRequiredPlaceholders(template));
    assertEquals(
        "com.example.chatapp.persistence.UllavoxSqliteDialect",
        properties.getProperty("spring.jpa.properties.hibernate.dialect"));
  }
}
