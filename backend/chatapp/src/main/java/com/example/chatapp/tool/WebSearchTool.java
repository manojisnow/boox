package com.example.chatapp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** Web search tool using the DuckDuckGo Instant Answer API. */
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

  private final RestTemplate restTemplate;

  @Value("${tools.websearch.max-results:5}")
  private int maxResults;

  public WebSearchTool(final RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @Override
  public String getName() {
    return TOOL_NAME;
  }

  @Override
  public String getDescription() {
    return "Look up a query using the DuckDuckGo Instant Answer API. Returns instant answers,"
        + " brief summaries, and related topics for well-known entities. Does not crawl"
        + " arbitrary web pages or retrieve real-time news.";
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

  @Override
  @SuppressWarnings({"unchecked", "PMD.AvoidCatchingGenericException"})
  public String execute(final Map<String, Object> arguments) {
    final String query = (String) arguments.get("query");
    if (query == null || query.isBlank()) {
      return "Error: No search query provided.";
    }
    LOGGER.info("Executing web search for: {}", query);
    try {
      return searchDuckDuckGo(query);
    } catch (Exception e) {
      LOGGER.error("Web search failed for query '{}': {}", query, e.getMessage(), e);
      return "Search failed: " + e.getMessage();
    }
  }

  @SuppressWarnings({"unchecked", "PMD.LawOfDemeter"})
  private String searchDuckDuckGo(final String query)
      throws com.fasterxml.jackson.core.JsonProcessingException, java.io.IOException {
    final String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
    final String url = DDG_API_URL + "?q=" + encoded + "&format=json&no_html=1&skip_disambig=1";

    final ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
    final String body = response.getBody();
    if (body == null || body.isEmpty()) {
      return "No results found for: " + query;
    }

    final Map<String, Object> json = MAPPER.readValue(body, Map.class);

    final StringBuilder results = new StringBuilder();
    results.append("Search results for: ").append(query).append("\n\n");

    // Extract abstract (main instant answer)
    final String abstractText = (String) json.getOrDefault("AbstractText", "");
    final String abstractSource = (String) json.getOrDefault("AbstractSource", "");
    if (abstractText != null && !abstractText.isEmpty()) {
      results.append("Summary");
      if (abstractSource != null && !abstractSource.isEmpty()) {
        results.append(" (").append(abstractSource).append(")");
      }
      results.append(": ").append(abstractText).append("\n\n");
    }

    // Extract answer (direct answer)
    final String answer = (String) json.getOrDefault("Answer", "");
    if (answer != null && !answer.isEmpty()) {
      results.append("Answer: ").append(answer).append("\n\n");
    }

    // Extract related topics
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

    if (results.toString().equals("Search results for: " + query + "\n\n")) {
      return "No relevant results found for: " + query;
    }

    return results.toString().trim();
  }
}
