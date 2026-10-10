package com.example.chatapp.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import tools.jackson.databind.ObjectMapper;

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
            "AbstractURL", "https://en.wikipedia.org/wiki/Cat",
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
    assertTrue(result.contains("Source: https://en.wikipedia.org/wiki/Cat"));
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
  void execute_returnsNoEvidence_whenAllProvidersFail() {
    when(restTemplate.exchange(
            contains("duckduckgo.com"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)))
        .thenThrow(new RuntimeException("Connection refused"));
    when(restTemplate.exchange(
            contains("wikipedia.org"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenThrow(new RuntimeException("offline"));
    when(restTemplate.exchange(
            contains("news.google.com"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)))
        .thenThrow(new RuntimeException("offline"));
    assertTrue(tool.execute(Map.of("query", "test")).startsWith("No results"));
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
        .thenReturn(ResponseEntity.ok().build());

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
            + "<title>India: Modi announces new policy - BBC News</title>"
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

  @Test
  void urlDecodingIsNotSearchEvidence() throws Exception {
    when(restTemplate.exchange(
            contains("duckduckgo.com"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)))
        .thenReturn(jsonResponse(Map.of("Answer", "URL Decoded: spring.io/projects/spring-boot")));
    assertTrue(tool.searchDuckDuckGo("spring.io/projects/spring-boot").isEmpty());
  }

  @Test
  void rejectsUnrelatedFallbackResultsAndContinuesAfterProviderOutage() throws Exception {
    when(restTemplate.exchange(
            contains("duckduckgo.com"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)))
        .thenThrow(new org.springframework.web.client.ResourceAccessException("offline"));
    when(restTemplate.exchange(
            contains("w/api.php"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            jsonResponse(
                Map.of(
                    "query",
                    Map.of(
                        "search",
                        List.of(
                            Map.of(
                                "title",
                                "Windows 11 version history",
                                "snippet",
                                "Microsoft Windows"))))));
    when(restTemplate.exchange(
            contains("news.google.com"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)))
        .thenReturn(
            ResponseEntity.ok(
                "<rss><channel><item><title>UEFI release</title><link>https://example.com/uefi</link></item></channel></rss>"));
    final String result = tool.execute(Map.of("query", "Spring Boot latest stable release"));
    assertTrue(result.startsWith("No results"));
    assertTrue(result.contains("Full web search is not configured"));
    verify(restTemplate, never()).exchange(contains("rest_v1"), any(), any(), eq(String.class));
  }

  @Test
  void googleSearchUsesConfiguredProviderAndKeepsOnlyRelevantRequestedDomain() throws Exception {
    setField(tool, "googleApiKey", "test-key");
    setField(tool, "googleSearchEngineId", "test-engine");
    when(restTemplate.exchange(
            contains("googleapis.com/customsearch"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)))
        .thenReturn(
            jsonResponse(
                Map.of(
                    "items",
                    List.of(
                        Map.of(
                            "title",
                            "Spring Boot",
                            "snippet",
                            "Release requires Java 17 or later.",
                            "link",
                            "https://docs.spring.io/spring-boot/system-requirements.html"),
                        Map.of(
                            "title",
                            "Spring Boot",
                            "snippet",
                            "Unofficial claims",
                            "link",
                            "https://spring.io.evil.example/"),
                        Map.of(
                            "title",
                            "Windows 11",
                            "snippet",
                            "Windows release",
                            "link",
                            "https://spring.io/windows")))));
    final String result =
        tool.execute(Map.of("query", "Spring Boot supported Java site:spring.io"));
    assertTrue(result.contains("https://docs.spring.io/"));
    assertFalse(result.contains("evil.example"));
    assertFalse(result.contains("Windows"));
    verify(restTemplate, never()).exchange(contains("duckduckgo"), any(), any(), eq(String.class));
  }

  @Test
  void braveUsesHeaderAuthenticationAndFiltersUnrelatedExcerpts() throws Exception {
    setField(tool, "braveApiKey", "test-token");
    setField(tool, "maxResults", 1);
    when(restTemplate.exchange(
            contains("api.search.brave.com"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)))
        .thenReturn(
            jsonResponse(
                Map.of(
                    "web",
                    Map.of(
                        "results",
                        List.of(
                            Map.of(
                                "title",
                                "Windows",
                                "description",
                                "Unrelated release",
                                "url",
                                "https://example.com/windows"),
                            Map.of(
                                "title",
                                "Spring Boot",
                                "description",
                                "<b>Spring Boot</b> requires Java 17 or later",
                                "url",
                                "https://docs.spring.io/requirements"),
                            Map.of(
                                "title",
                                "Spring Boot",
                                "description",
                                "Other result",
                                "url",
                                "https://spring.io/other"))))));
    final String result =
        tool.execute(Map.of("query", "Spring Boot Java requirements site:spring.io"));
    assertTrue(result.contains("requires Java 17"));
    assertFalse(result.contains("<b>"));
    assertFalse(result.contains("Windows"));
    assertFalse(result.contains("Other result"));
    final org.mockito.ArgumentCaptor<HttpEntity> request =
        org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate)
        .exchange(
            org.mockito.ArgumentMatchers.<String>argThat(url -> !url.contains("test-token")),
            eq(HttpMethod.GET),
            request.capture(),
            eq(String.class));
    assertEquals("test-token", request.getValue().getHeaders().getFirst("X-Subscription-Token"));
  }

  @Test
  void searchApisTreatEmptyAndMalformedResultsAsNoEvidence() throws Exception {
    setField(tool, "braveApiKey", "test-token");
    when(restTemplate.exchange(
            contains("api.search.brave.com"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)))
        .thenReturn(
            ResponseEntity.ok().build(),
            ResponseEntity.ok(""),
            ResponseEntity.ok("{}"),
            ResponseEntity.ok("{\"web\":{\"results\":null}}"),
            ResponseEntity.ok("{\"web\":{\"results\":[false,{}]}}"));
    for (int i = 0; i < 5; i++) assertTrue(tool.searchBrave("Spring Boot").isEmpty());
    setField(tool, "googleApiKey", "test-key");
    setField(tool, "googleSearchEngineId", "test-engine");
    when(restTemplate.exchange(
            contains("googleapis.com"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)))
        .thenReturn(
            ResponseEntity.ok().build(),
            ResponseEntity.ok(""),
            ResponseEntity.ok("{}"),
            ResponseEntity.ok(
                "{\"items\":[false,{}, {\"title\":\"Spring Boot\",\"snippet\":\"\",\"link\":\"https://spring.io\"}]}"));
    for (int i = 0; i < 4; i++) assertTrue(tool.searchGoogle("Spring Boot").isEmpty());
  }

  @Test
  void wikipediaTrimsLeadingNewlinesAndChoosesRelevantCandidate() throws Exception {
    when(restTemplate.exchange(
            contains("w/api.php"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
        .thenReturn(
            jsonResponse(
                Map.of(
                    "query",
                    Map.of(
                        "search",
                        List.of(Map.of("title", "Windows 11"), Map.of("title", "Spring Boot"))))));
    when(restTemplate.exchange(
            contains("rest_v1/page/summary"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)))
        .thenReturn(
            jsonResponse(
                Map.of(
                    "extract",
                    "\n\nSpring Boot is a Java framework.\nSecond paragraph",
                    "content_urls",
                    Map.of(
                        "desktop", Map.of("page", "https://en.wikipedia.org/wiki/Spring_Boot")))));
    final String result = tool.searchWikipedia("Spring Boot latest release").orElseThrow();
    assertTrue(result.contains("Spring Boot is a Java framework."));
    assertFalse(result.contains("Second paragraph"));
  }

  @Test
  void siteRestrictionSkipsWikipediaAndFiltersNewsPublisher() throws Exception {
    stubDdgEmpty();
    when(restTemplate.exchange(
            contains("news.google.com"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)))
        .thenReturn(
            ResponseEntity.ok(
                "<rss><channel><item><title>Spring Boot release</title><link>https://news.google.com/rss/articles/abc</link><source url=\"https://spring.io\">Spring</source></item><item><title>Spring Boot rumors</title><link>https://news.google.com/rss/articles/def</link><source url=\"https://example.com\">Other</source></item></channel></rss>"));
    final String result = tool.execute(Map.of("query", "Spring Boot release site:spring.io"));
    assertTrue(result.contains("Spring Boot release"));
    assertFalse(result.contains("rumors"));
    verify(restTemplate, never()).exchange(contains("wikipedia"), any(), any(), eq(String.class));
  }

  @Test
  void relevanceRequiresTopicWordsAndSiteFilterChecksHostBoundary() {
    assertFalse(
        WebSearchTool.relevant(
            "Spring Boot latest stable release 2026", "Windows 11 version history"));
    assertFalse(WebSearchTool.relevant("Spring Boot", "Spring season"));
    assertTrue(WebSearchTool.relevant("Spring Boot latest release", "Spring Boot Java framework"));
    assertFalse(WebSearchTool.relevant("Spring Boot", ""));
    assertFalse(WebSearchTool.relevant("Spring Boot", null));
    assertTrue(WebSearchTool.relevant("latest 2026", "A release"));
    assertTrue(WebSearchTool.matchesSite("site:spring.io", "https://docs.spring.io/page"));
    assertFalse(WebSearchTool.matchesSite("site:spring.io", "https://spring.io.evil.example/page"));
    assertFalse(WebSearchTool.matchesSite("site:spring.io", "https://evilspring.io/page"));
    assertFalse(WebSearchTool.matchesSite("site:spring.io", "file:///etc/passwd"));
    assertFalse(WebSearchTool.matchesSite("site:spring.io", ""));
    assertFalse(WebSearchTool.matchesSite("topic", "not a url"));
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
