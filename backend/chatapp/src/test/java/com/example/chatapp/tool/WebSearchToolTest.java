package com.example.chatapp.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class WebSearchToolTest {

  @Mock RestTemplate restTemplate;

  WebSearchTool tool;

  @BeforeEach
  void setUp() {
    tool = new WebSearchTool(restTemplate);
    // Set @Value fields via reflection
    setField(tool, "maxResults", 5);
  }

  static void setField(Object target, String field, Object value) {
    try {
      java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void getName_returnsWebSearch() {
    assertEquals("web_search", tool.getName());
  }

  @Test
  void getDescription_returnsNonEmpty() {
    assertFalse(tool.getDescription().isEmpty());
  }

  @Test
  void getParameterSchema_hasQueryProperty() {
    Map<String, Object> schema = tool.getParameterSchema();
    assertEquals("object", schema.get("type"));
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");
    assertTrue(props.containsKey("query"));
  }

  @Test
  void execute_returnsResults_whenDdgReturnsData() throws Exception {
    // Arrange
    Map<String, Object> ddgResponse =
        Map.of(
            "AbstractText", "Cats are domesticated mammals.",
            "AbstractSource", "Wikipedia",
            "RelatedTopics",
                List.of(
                    Map.of(
                        "Text",
                        "Cat - Wikipedia",
                        "FirstURL",
                        "https://en.wikipedia.org/wiki/Cat")));
    String json = new ObjectMapper().writeValueAsString(ddgResponse);
    when(restTemplate.getForEntity(anyString(), eq(String.class)))
        .thenReturn(new ResponseEntity<>(json, HttpStatus.OK));

    // Act
    String result = tool.execute(Map.of("query", "cats"));

    // Assert
    assertTrue(result.contains("cats"));
    assertTrue(result.contains("Cats are domesticated mammals."));
    assertTrue(result.contains("Wikipedia"));
  }

  @Test
  void execute_returnsNoResults_whenDdgReturnsEmpty() throws Exception {
    // Arrange
    Map<String, Object> ddgResponse = Map.of("AbstractText", "", "Answer", "");
    String json = new ObjectMapper().writeValueAsString(ddgResponse);
    when(restTemplate.getForEntity(anyString(), eq(String.class)))
        .thenReturn(new ResponseEntity<>(json, HttpStatus.OK));

    // Act
    String result = tool.execute(Map.of("query", "xyznonexistent"));

    // Assert
    assertTrue(result.contains("No relevant results"));
  }

  @Test
  void execute_returnsError_whenQueryBlank() {
    String result = tool.execute(Map.of("query", ""));
    assertTrue(result.contains("Error"));
  }

  @Test
  void execute_returnsError_whenQueryNull() {
    String result = tool.execute(Map.of());
    assertTrue(result.contains("Error"));
  }

  @Test
  void execute_returnsError_whenApiThrows() {
    when(restTemplate.getForEntity(anyString(), eq(String.class)))
        .thenThrow(new RuntimeException("Connection refused"));

    String result = tool.execute(Map.of("query", "test"));
    assertTrue(result.contains("Search failed"));
  }

  @Test
  void execute_handlesNullBody() {
    when(restTemplate.getForEntity(anyString(), eq(String.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

    String result = tool.execute(Map.of("query", "test"));
    assertTrue(result.contains("No results found"));
  }

  @Test
  void execute_extractsAnswer() throws Exception {
    Map<String, Object> ddgResponse = Map.of("AbstractText", "", "Answer", "42");
    String json = new ObjectMapper().writeValueAsString(ddgResponse);
    when(restTemplate.getForEntity(anyString(), eq(String.class)))
        .thenReturn(new ResponseEntity<>(json, HttpStatus.OK));

    String result = tool.execute(Map.of("query", "meaning of life"));
    assertTrue(result.contains("Answer: 42"));
  }
}
