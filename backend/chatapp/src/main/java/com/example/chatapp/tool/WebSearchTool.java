package com.example.chatapp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Web search tool using a three-provider fallback chain — all free, no API keys required.
 *
 * <ol>
 *   <li><b>DuckDuckGo Instant Answer API</b> — instant answers, math, units, well-known entities.
 *   <li><b>Wikipedia REST API</b> — factual knowledge, people, places, history, concepts.
 *   <li><b>Google News RSS</b> — current news and recent events.
 * </ol>
 *
 * <p>Each provider is tried in order. The first one that returns meaningful content wins. If all
 * three return nothing, a "no results" message is returned and the model falls back to its training
 * data.
 */
@Component
@ConditionalOnProperty(
    name = "tools.websearch.enabled",
    havingValue = "true",
    matchIfMissing = true)
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP2"},
    justification = "Spring-managed beans; RestTemplate is shared and thread-safe.")
public class WebSearchTool implements Tool {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebSearchTool.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String TOOL_NAME = "web_search";

  private static final String DDG_API_URL = "https://api.duckduckgo.com/";
  private static final String WIKIPEDIA_SEARCH_URL = "https://en.wikipedia.org/w/api.php";
  private static final String WIKIPEDIA_SUMMARY_URL =
      "https://en.wikipedia.org/api/rest_v1/page/summary/";
  private static final String GOOGLE_NEWS_RSS_URL = "https://news.google.com/rss/search";

  /** JSON schema type keywords — used to detect when a model echoes the schema as an argument. */
  private static final Set<String> JSON_TYPE_KEYWORDS =
      Set.of("string", "number", "integer", "boolean", "array", "object", "null");

  private final RestTemplate restTemplate;

  @Value("${tools.websearch.max-results:5}")
  private int maxResults;

  public WebSearchTool(final RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  // ---------------------------------------------------------------------------
  // Tool interface
  // ---------------------------------------------------------------------------

  @Override
  public String getName() {
    return TOOL_NAME;
  }

  @Override
  public String getDescription() {
    return "Search the web for current information, news, facts, and general knowledge."
        + " Use when the user needs up-to-date information that may not be in your training data.";
  }

  @Override
  public Map<String, Object> getParameterSchema() {
    final Map<String, Object> schema = new HashMap<>();
    schema.put("type", "object");
    final Map<String, Object> properties = new HashMap<>();
    final Map<String, Object> queryProp = new HashMap<>();
    queryProp.put("type", "string");
    queryProp.put("description", "The search query");
    properties.put("query", queryProp);
    schema.put("properties", properties);
    schema.put("required", List.of("query"));
    return schema;
  }

  // ---------------------------------------------------------------------------
  // execute
  // ---------------------------------------------------------------------------

  @Override
  @SuppressWarnings({"unchecked", "PMD.AvoidCatchingGenericException"})
  public String execute(final Map<String, Object> arguments) {
    final Object queryObj = arguments.get("query");
    if (queryObj == null) {
      return "Error: No search query provided.";
    }

    final String query;
    if (queryObj instanceof String) {
      query = (String) queryObj;
    } else if (queryObj instanceof Map) {
      // Some models return a schema-like object instead of a plain string. The actual search
      // terms are usually in one of the string values (e.g. "description"). Take the first
      // non-blank value that is not a JSON type keyword.
      final String extracted =
          ((Map<?, ?>) queryObj)
              .values().stream()
                  .filter(v -> v instanceof String)
                  .map(v -> ((String) v).strip())
                  .filter(v -> !v.isBlank() && !JSON_TYPE_KEYWORDS.contains(v.toLowerCase()))
                  .findFirst()
                  .orElse(null);
      if (extracted != null) {
        LOGGER.warn(
            "Model passed query as schema object {}; using extracted value: {}",
            queryObj,
            extracted);
        query = extracted;
      } else {
        LOGGER.warn("Tool received unusable argument for 'query': {}", queryObj);
        return "Error: Could not extract a search query from the provided argument: " + queryObj;
      }
    } else {
      LOGGER.warn(
          "Tool received unexpected argument type for 'query': {}", queryObj.getClass().getName());
      return "Error: Unexpected argument type for 'query': " + queryObj.getClass().getSimpleName();
    }

    if (query.isBlank()) {
      return "Error: No search query provided.";
    }

    LOGGER.info("Executing web search for: {}", query);
    try {
      // 1. DuckDuckGo Instant Answer
      final Optional<String> ddgResult = searchDuckDuckGo(query);
      if (ddgResult.isPresent()) {
        return ddgResult.get();
      }

      // 2. Wikipedia
      LOGGER.info("DuckDuckGo returned no results for '{}', trying Wikipedia", query);
      final Optional<String> wikiResult = searchWikipedia(query);
      if (wikiResult.isPresent()) {
        return wikiResult.get();
      }

      // 3. Google News RSS
      LOGGER.info("Wikipedia returned no results for '{}', trying Google News RSS", query);
      return searchGoogleNewsRss(query).orElse("No results found for: " + query);

    } catch (Exception e) {
      LOGGER.error("Web search failed for query '{}': {}", query, e.getMessage(), e);
      return "Search failed: " + e.getMessage();
    }
  }

  // ---------------------------------------------------------------------------
  // Provider 1: DuckDuckGo Instant Answer API
  // ---------------------------------------------------------------------------

  @SuppressWarnings({"unchecked", "PMD.LawOfDemeter"})
  Optional<String> searchDuckDuckGo(final String query)
      throws com.fasterxml.jackson.core.JsonProcessingException, java.io.IOException {
    final String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
    final String url = DDG_API_URL + "?q=" + encoded + "&format=json&no_html=1&skip_disambig=1";

    final HttpHeaders headers = new HttpHeaders();
    headers.set(
        HttpHeaders.USER_AGENT,
        "Mozilla/5.0 (compatible; Boox-ChatBot/1.0; +https://github.com/boox)");
    headers.set(HttpHeaders.ACCEPT, "application/json");
    final HttpEntity<Void> entity = new HttpEntity<>(headers);
    final ResponseEntity<String> response =
        restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

    final String body = response != null ? response.getBody() : null;
    if (body == null || body.isEmpty()) {
      return Optional.empty();
    }

    final Map<String, Object> json = MAPPER.readValue(body, Map.class);
    final StringBuilder results = new StringBuilder();
    results.append("Search results for: ").append(query).append("\n\n");

    final String abstractText = (String) json.getOrDefault("AbstractText", "");
    final String abstractSource = (String) json.getOrDefault("AbstractSource", "");
    if (abstractText != null && !abstractText.isEmpty()) {
      results.append("Summary");
      if (abstractSource != null && !abstractSource.isEmpty()) {
        results.append(" (").append(abstractSource).append(")");
      }
      results.append(": ").append(abstractText).append("\n\n");
    }

    final String answer = (String) json.getOrDefault("Answer", "");
    if (answer != null && !answer.isEmpty()) {
      results.append("Answer: ").append(answer).append("\n\n");
    }

    final Object relatedTopics = json.get("RelatedTopics");
    if (relatedTopics instanceof List) {
      final List<Map<String, Object>> topics = (List<Map<String, Object>>) relatedTopics;
      int count = 0;
      for (final Map<String, Object> topic : topics) {
        if (count >= maxResults) {
          break;
        }
        final String text = (String) topic.get("Text");
        final String firstUrl = (String) topic.get("FirstURL");
        if (text != null && !text.isEmpty()) {
          results.append("- ").append(text);
          if (firstUrl != null && !firstUrl.isEmpty()) {
            results.append(" (").append(firstUrl).append(")");
          }
          results.append("\n");
          count++;
        }
      }
    }

    final String header = "Search results for: " + query + "\n\n";
    if (results.toString().equals(header)) {
      return Optional.empty();
    }
    return Optional.of(results.toString().trim());
  }

  // ---------------------------------------------------------------------------
  // Provider 2: Wikipedia REST API
  // ---------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  Optional<String> searchWikipedia(final String query)
      throws com.fasterxml.jackson.core.JsonProcessingException, java.io.IOException {
    // Step 1: find the most relevant article title
    final String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
    final String searchUrl =
        WIKIPEDIA_SEARCH_URL
            + "?action=query&list=search&srsearch="
            + encoded
            + "&format=json&srlimit=1&srprop=snippet&utf8=1";

    final HttpHeaders wikiHeaders = new HttpHeaders();
    wikiHeaders.set(
        HttpHeaders.USER_AGENT,
        "Mozilla/5.0 (compatible; Boox-ChatBot/1.0; +https://github.com/boox)");
    final HttpEntity<Void> entity = new HttpEntity<>(wikiHeaders);
    final ResponseEntity<String> searchResponse =
        restTemplate.exchange(searchUrl, HttpMethod.GET, entity, String.class);

    final String searchBody = searchResponse != null ? searchResponse.getBody() : null;
    if (searchBody == null || searchBody.isEmpty()) {
      return Optional.empty();
    }

    final Map<String, Object> searchJson = MAPPER.readValue(searchBody, Map.class);
    final Map<String, Object> queryObj = (Map<String, Object>) searchJson.get("query");
    if (queryObj == null) {
      return Optional.empty();
    }
    final List<Map<String, Object>> searchResults =
        (List<Map<String, Object>>) queryObj.get("search");
    if (searchResults == null || searchResults.isEmpty()) {
      return Optional.empty();
    }

    final String articleTitle = (String) searchResults.get(0).get("title");
    if (articleTitle == null || articleTitle.isEmpty()) {
      return Optional.empty();
    }

    // Step 2: fetch the article summary
    final String normalizedTitle = articleTitle.replace(' ', '_');
    final String summaryUrl =
        WIKIPEDIA_SUMMARY_URL + URLEncoder.encode(normalizedTitle, StandardCharsets.UTF_8);

    final ResponseEntity<String> summaryResponse =
        restTemplate.exchange(summaryUrl, HttpMethod.GET, entity, String.class);

    final String summaryBody = summaryResponse != null ? summaryResponse.getBody() : null;
    if (summaryBody == null || summaryBody.isEmpty()) {
      return Optional.empty();
    }

    final Map<String, Object> summaryJson = MAPPER.readValue(summaryBody, Map.class);
    final String extract = (String) summaryJson.getOrDefault("extract", "");
    if (extract == null || extract.isBlank()) {
      return Optional.empty();
    }

    // Trim to first paragraph or 600 chars, whichever is shorter
    final String firstParagraph =
        extract.contains("\n") ? extract.substring(0, extract.indexOf('\n')) : extract;
    final String summary =
        firstParagraph.length() > 600 ? firstParagraph.substring(0, 600) + "..." : firstParagraph;

    @SuppressWarnings("unchecked")
    final Map<String, Object> contentUrls =
        (Map<String, Object>) summaryJson.getOrDefault("content_urls", Map.of());
    @SuppressWarnings("unchecked")
    final Map<String, Object> desktop =
        (Map<String, Object>) contentUrls.getOrDefault("desktop", Map.of());
    final String pageUrl = (String) desktop.getOrDefault("page", "");

    final StringBuilder result = new StringBuilder();
    result.append("Wikipedia: ").append(articleTitle).append("\n\n");
    result.append(summary);
    if (!pageUrl.isEmpty()) {
      result.append("\n\nSource: ").append(pageUrl);
    }
    return Optional.of(result.toString().trim());
  }

  // ---------------------------------------------------------------------------
  // Provider 3: Google News RSS
  // ---------------------------------------------------------------------------

  @SuppressFBWarnings(
      value = "XXE_DOCUMENT_BUILDER_FACTORY",
      justification = "XXE is disabled via setFeature calls below.")
  Optional<String> searchGoogleNewsRss(final String query) throws Exception {
    final String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
    final String url = GOOGLE_NEWS_RSS_URL + "?q=" + encoded + "&hl=en-US&gl=US&ceid=US:en";

    final HttpHeaders headers = new HttpHeaders();
    headers.set(
        HttpHeaders.USER_AGENT,
        "Mozilla/5.0 (compatible; Boox-ChatBot/1.0; +https://github.com/boox)");
    final HttpEntity<Void> entity = new HttpEntity<>(headers);
    final ResponseEntity<String> response =
        restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

    final String body = response != null ? response.getBody() : null;
    if (body == null || body.isEmpty()) {
      return Optional.empty();
    }

    // Parse RSS XML — disable XXE for security
    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setExpandEntityReferences(false);

    final DocumentBuilder builder = factory.newDocumentBuilder();
    final Document doc =
        builder.parse(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));

    final NodeList items = doc.getElementsByTagName("item");
    if (items.getLength() == 0) {
      return Optional.empty();
    }

    final StringBuilder results = new StringBuilder();
    results.append("News results for: ").append(query).append("\n\n");

    final int limit = Math.min(items.getLength(), maxResults);
    for (int i = 0; i < limit; i++) {
      final Element item = (Element) items.item(i);
      final String title = getXmlText(item, "title");
      final String link = getXmlText(item, "link");
      final String pubDate = getXmlText(item, "pubDate");

      if (!title.isEmpty()) {
        results.append(i + 1).append(". ").append(title).append("\n");
        if (!pubDate.isEmpty()) {
          results.append("   ").append(pubDate).append("\n");
        }
        if (!link.isEmpty()) {
          results.append("   ").append(link).append("\n");
        }
        results.append("\n");
      }
    }

    final String header = "News results for: " + query + "\n\n";
    if (results.toString().equals(header)) {
      return Optional.empty();
    }
    return Optional.of(results.toString().trim());
  }

  // ---------------------------------------------------------------------------
  // Utilities
  // ---------------------------------------------------------------------------

  private static String getXmlText(final Element parent, final String tagName) {
    final NodeList nodes = parent.getElementsByTagName(tagName);
    if (nodes.getLength() == 0) {
      return "";
    }
    final String text = nodes.item(0).getTextContent();
    return text == null ? "" : text.trim();
  }

  static String stripHtml(final String html) {
    if (html == null) {
      return "";
    }
    return html.replaceAll("<[^>]+>", "").trim();
  }
}
