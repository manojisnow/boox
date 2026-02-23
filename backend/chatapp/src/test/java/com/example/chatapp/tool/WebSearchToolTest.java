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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class WebSearchToolTest {

  @Mock RestTemplate restTemplate;

  WebSearchTool tool;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @BeforeEach
  void setUp() {
    tool = new WebSearchTool(restTemplate);
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

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private ResponseEntity<String> jsonResponse(Object obj) throws Exception {
    return new ResponseEntity<>(MAPPER.writeValueAsString(obj), HttpStatus.OK);
  }

  private ResponseEntity<String> emptyDdgResponse() throws Exception {
    return jsonResponse(Map.of("AbstractText", "", "Answer", "", "RelatedTopics", List.of()));
  }

  private ResponseEntity<String> emptyWikiSearchResponse() throws Exception {
    return jsonResponse(Map.of("query", Map.of("search", List.of())));
  }

  /** Stubs DuckDuckGo URL to return an empty-results response. */
  private void stubDdgEmpty() throws Exception {
    when(restTemplate.exchange(
            contains("duckduckgo.com"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)))
        .thenReturn(emptyDdgResponse());
  }

  /** Stubs Wikipedia search and summary URLs to return empty results. */
  private void stubWikiEmpty() throws Exception {
    when(restTemplate.exchange(
            contains("w/api.php"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(emptyWikiSearchResponse());
  }

  // ---------------------------------------------------------------------------
  // Metadata
  // ---------------------------------------------------------------------------

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
    @SuppressWarnings("unchecked")
    Map<String, Object> props = (Map<String, Object>) schema.get("properties");
    assertTrue(props.containsKey("query"));
  }

  // ---------------------------------------------------------------------------
  // Argument validation
  // ---------------------------------------------------------------------------

  @Test
  void execute_returnsError_whenQueryBlank() {
    assertTrue(tool.execute(Map.of("query", "")).startsWith("Error"));
  }

  @Test
  void execute_returnsError_whenQueryMissing() {
    assertTrue(tool.execute(Map.of()).startsWith("Error"));
  }

  @Test
  void execute_extractsQueryFromSchemaObject_andSearches() throws Exception {
    // Model returns {description="cats", type="string"} — should extract "cats" and search
    Map<String, Object> ddgResponse =
        Map.of("AbstractText", "Cats are mammals.", "AbstractSource", "Wikipedia");
    when(restTemplate.exchange(
            contains("duckduckgo.com"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)))
        .thenReturn(jsonResponse(ddgResponse));

    String result = tool.execute(Map.of("query", Map.of("description", "cats", "type", "string")));
    assertTrue(result.contains("Cats are mammals."));
  }

  @Test
  void execute_returnsError_whenSchemaObjectHasNoUsableValue() {
    // Only type keyword values present — nothing to extract
    assertTrue(tool.execute(Map.of("query", Map.of("type", "string"))).startsWith("Error"));
  }

  // ---------------------------------------------------------------------------
  // Provider 1: DuckDuckGo
  // ---------------------------------------------------------------------------

  @Test
  void execute_ddg_returnsAbstractText() throws Exception {
    Map<String, Object> ddgResponse =
        Map.of(
            "AbstractText", "Cats are domesticated mammals.",
            "AbstractSource", "Wikipedia",
            "RelatedTopics",
                List.of(
                    Map.of(
                        "Text",
                        "Cat - a domestic animal",
                        "FirstURL",
                        "https://en.wikipedia.org/wiki/Cat")));
    when(restTemplate.exchange(
            contains("duckduckgo.com"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)))
        .thenReturn(jsonResponse(ddgResponse));

    String result = tool.execute(Map.of("query", "cats"));
    assertTrue(result.contains("Cats are domesticated mammals."));
    assertTrue(result.contains("Wikipedia"));
  }

  @Test
  void execute_ddg_returnsAnswer() throws Exception {
    Map<String, Object> ddgResponse = Map.of("AbstractText", "", "Answer", "42");
    when(restTemplate.exchange(
            contains("duckduckgo.com"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)))
        .thenReturn(jsonResponse(ddgResponse));

    assertTrue(tool.execute(Map.of("query", "meaning of life")).contains("Answer: 42"));
  }

  @Test
  void execute_ddg_returnsSearchFailed_whenApiThrows() {
    when(restTemplate.exchange(
            contains("duckduckgo.com"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)))
        .thenThrow(new RuntimeException("Connection refused"));

    assertTrue(tool.execute(Map.of("query", "test")).contains("Search failed"));
  }

  // ---------------------------------------------------------------------------
  // Provider 2: Wikipedia fallback
  // ---------------------------------------------------------------------------

  @Test
  void execute_wikipedia_usedWhenDdgReturnsEmpty() throws Exception {
    stubDdgEmpty();

    Map<String, Object> wikiSearch =
        Map.of("query", Map.of("search", List.of(Map.of("title", "Prime Minister of India"))));
    when(restTemplate.exchange(
            contains("w/api.php"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(jsonResponse(wikiSearch));

    Map<String, Object> wikiSummary =
        Map.of(
            "title",
            "Prime Minister of India",
            "extract",
            "The Prime Minister of India is the head of government. Narendra Modi"
                + " has been the current PM since 2014.",
            "content_urls",
            Map.of(
                "desktop",
                Map.of("page", "https://en.wikipedia.org/wiki/Prime_Minister_of_India")));
    when(restTemplate.exchange(
            contains("rest_v1/page/summary"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)))
        .thenReturn(jsonResponse(wikiSummary));

    String result = tool.execute(Map.of("query", "prime minister of india"));
    assertTrue(result.contains("Prime Minister of India"));
    assertTrue(result.contains("Narendra Modi"));
    assertTrue(result.contains("wikipedia.org"));
  }

  @Test
  void execute_wikipedia_skippedWhenSearchReturnsEmpty() throws Exception {
    stubDdgEmpty();
    stubWikiEmpty();

    // Google News RSS also returns nothing
    when(restTemplate.exchange(
            contains("news.google.com"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)))
        .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

    String result = tool.execute(Map.of("query", "xyznonexistent"));
    assertTrue(result.contains("No results found"));
  }

  // ---------------------------------------------------------------------------
  // Provider 3: Google News RSS fallback
  // ---------------------------------------------------------------------------

  @Test
  void execute_newsRss_usedWhenDdgAndWikiReturnEmpty() throws Exception {
    stubDdgEmpty();
    stubWikiEmpty();

    String rssXml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<rss version=\"2.0\"><channel>"
            + "<item>"
            + "<title>Modi announces new policy - BBC News</title>"
            + "<link>https://news.google.com/rss/articles/test</link>"
            + "<pubDate>Sat, 22 Feb 2026 12:00:00 GMT</pubDate>"
            + "</item>"
            + "</channel></rss>";
    when(restTemplate.exchange(
            contains("news.google.com"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)))
        .thenReturn(new ResponseEntity<>(rssXml, HttpStatus.OK));

    String result = tool.execute(Map.of("query", "india news"));
    assertTrue(result.contains("Modi announces new policy"));
    assertTrue(result.contains("BBC News"));
  }

  @Test
  void execute_newsRss_returnsNoResults_whenRssHasNoItems() throws Exception {
    stubDdgEmpty();
    stubWikiEmpty();

    String emptyRss =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<rss version=\"2.0\"><channel></channel></rss>";
    when(restTemplate.exchange(
            contains("news.google.com"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)))
        .thenReturn(new ResponseEntity<>(emptyRss, HttpStatus.OK));

    assertTrue(tool.execute(Map.of("query", "xyznotfound")).contains("No results found"));
  }

  // ---------------------------------------------------------------------------
  // stripHtml utility
  // ---------------------------------------------------------------------------

  @Test
  void stripHtml_removesTagsAndTrims() {
    assertEquals("hello world", WebSearchTool.stripHtml("<b>hello</b> <i>world</i>"));
  }

  @Test
  void stripHtml_handlesNull() {
    assertEquals("", WebSearchTool.stripHtml(null));
  }
}
