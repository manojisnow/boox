package com.example.chatapp.tool;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
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
import org.xml.sax.SAXException;
import tools.jackson.databind.ObjectMapper;

/** Web search with optional search APIs and relevance-checked reference/news fallbacks. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
    "'${tools.websearch.provider:legacy}' == 'legacy'")
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
  // This tool's own "query" argument name (schema key + lookup key below) - distinct
  // from the unrelated "query" key in the Wikipedia API's own response shape.
  private static final String PARAM_QUERY = "query";
  // Paragraph/section break used consistently across all three providers' formatted output.
  private static final String PARAGRAPH_BREAK = "\n\n";

  private static final String DDG_API_URL = "https://api.duckduckgo.com/";
  private static final String WIKIPEDIA_SEARCH_URL = "https://en.wikipedia.org/w/api.php";
  private static final String WIKIPEDIA_SUMMARY_URL =
      "https://en.wikipedia.org/api/rest_v1/page/summary/";
  private static final String GOOGLE_NEWS_RSS_URL = "https://news.google.com/rss/search";

  /** JSON schema type keywords — used to detect when a model echoes the schema as an argument. */
  private static final Set<String> JSON_TYPE_KEYWORDS =
      Set.of("string", "number", "integer", "boolean", "array", "object", "null");

  private static final Set<String> QUERY_MODIFIERS =
      Set.of(
          "the",
          "a",
          "an",
          "of",
          "and",
          "or",
          "for",
          "in",
          "on",
          "to",
          "is",
          "what",
          "which",
          "latest",
          "current",
          "stable",
          "release",
          "releases",
          "version",
          "versions",
          "supported",
          "support",
          "official",
          "source",
          "sources",
          "notes",
          "research",
          "explain",
          "cite",
          "links",
          "news");
  private static final Pattern SITE = Pattern.compile("(?i)\\bsite:([a-z0-9.-]+)");
  private final RestTemplate restTemplate;

  @Value("${tools.websearch.brave.api-key:}")
  private String braveApiKey = "";

  @Value("${tools.websearch.google.api-key:}")
  private String googleApiKey = "";

  @Value("${tools.websearch.google.search-engine-id:}")
  private String googleSearchEngineId = "";

  @Value("${tools.websearch.max-results:5}")
  private int maxResults;

  public WebSearchTool(
      @org.springframework.beans.factory.annotation.Qualifier("toolRestTemplate")
          final RestTemplate restTemplate) {
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
    return "Search web results using configured Brave or Google search APIs, with DuckDuckGo, Wikipedia, and news fallbacks."
        + " Results are limited excerpts, not full pages; cite returned URLs and do not infer details absent from the result.";
  }

  @Override
  public Map<String, Object> getParameterSchema() {
    final Map<String, Object> schema = new HashMap<>();
    schema.put("type", "object");
    final Map<String, Object> properties = new HashMap<>();
    final Map<String, Object> queryProp = new HashMap<>();
    queryProp.put("type", "string");
    queryProp.put("description", "The search query");
    properties.put(PARAM_QUERY, queryProp);
    schema.put("properties", properties);
    schema.put("required", List.of(PARAM_QUERY));
    return schema;
  }

  // ---------------------------------------------------------------------------
  // execute
  // ---------------------------------------------------------------------------

  @Override
  @SuppressWarnings("unchecked")
  public String execute(final Map<String, Object> arguments) {
    final Object queryObj = arguments.get(PARAM_QUERY);
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
                  .filter(
                      v -> !v.isBlank() && !JSON_TYPE_KEYWORDS.contains(v.toLowerCase(Locale.ROOT)))
                  .findFirst()
                  .orElse(null);
      if (extracted != null) {
        LOGGER.warn("Model passed query as a schema object; using an extracted text value");
        query = extracted;
      } else {
        LOGGER.warn("Tool received an unusable argument for 'query'");
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
    final List<SearchProvider> providers =
        List.of(
            this::searchBrave,
            this::searchGoogle,
            this::searchDuckDuckGo,
            this::searchWikipedia,
            this::searchGoogleNewsRss);
    for (final SearchProvider provider : providers) {
      try {
        final Optional<String> result = provider.search(query);
        if (result.isPresent()) return result.get();
      } catch (IOException | ParserConfigurationException | SAXException | RuntimeException e) {
        // One unavailable provider must not prevent the remaining providers being tried.
        LOGGER.warn("Search provider unavailable: {}", e.getClass().getSimpleName());
      }
    }
    return "No results found for: "
        + query
        + ". No relevant evidence was retrieved. Do not guess current facts."
        + (braveApiKey.isBlank() && (googleApiKey.isBlank() || googleSearchEngineId.isBlank())
            ? " Full web search is not configured; reference summaries and news may not cover this request."
            : "");
  }

  @FunctionalInterface
  private interface SearchProvider {
    Optional<String> search(String query)
        throws IOException, ParserConfigurationException, SAXException;
  }

  @SuppressWarnings("unchecked")
  Optional<String> searchBrave(final String query) throws IOException {
    if (braveApiKey.isBlank()) return Optional.empty();
    final String url =
        "https://api.search.brave.com/res/v1/web/search?q="
            + URLEncoder.encode(query, StandardCharsets.UTF_8)
            + "&count="
            + Math.max(1, Math.min(maxResults, 20));
    final HttpHeaders headers = new HttpHeaders();
    headers.set("X-Subscription-Token", braveApiKey);
    headers.set(HttpHeaders.ACCEPT, "application/json");
    final ResponseEntity<String> response =
        restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<Void>(headers), String.class);
    final String body = response.getBody();
    if (body == null || body.isBlank()) return Optional.empty();
    final Map<String, Object> json = MAPPER.readValue(body, Map.class);
    if (!(json.get("web") instanceof Map<?, ?> web)
        || !(web.get("results") instanceof List<?> items)) return Optional.empty();
    final List<Map<String, Object>> normalized = new java.util.ArrayList<>();
    for (final Object item : items) {
      if (!(item instanceof Map<?, ?> row)) continue;
      final Map<String, Object> result = new HashMap<>();
      result.put("title", row.get("title"));
      result.put("snippet", row.get("description"));
      result.put("link", row.get("url"));
      normalized.add(result);
    }
    return formatWebResults(query, normalized);
  }

  @SuppressWarnings("unchecked")
  Optional<String> searchGoogle(final String query) throws IOException {
    if (googleApiKey.isBlank() || googleSearchEngineId.isBlank()) return Optional.empty();
    final String url =
        "https://www.googleapis.com/customsearch/v1?key="
            + URLEncoder.encode(googleApiKey, StandardCharsets.UTF_8)
            + "&cx="
            + URLEncoder.encode(googleSearchEngineId, StandardCharsets.UTF_8)
            + "&q="
            + URLEncoder.encode(query, StandardCharsets.UTF_8)
            + "&num="
            + Math.max(1, Math.min(maxResults, 10));
    final ResponseEntity<String> response =
        restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, String.class);
    final String body = response.getBody();
    if (body == null || body.isBlank()) return Optional.empty();
    final Map<String, Object> json = MAPPER.readValue(body, Map.class);
    return formatWebResults(query, json.get("items"));
  }

  private Optional<String> formatWebResults(final String query, final Object items) {
    if (!(items instanceof List<?>)) return Optional.empty();
    final StringBuilder results = new StringBuilder("Web search excerpts (not full pages):\n\n");
    int count = 0;
    for (final Object item : (List<?>) items) {
      if (!(item instanceof Map<?, ?> row)) continue;
      final String title = row.get("title") instanceof String value ? value : "";
      final String snippet = row.get("snippet") instanceof String value ? stripHtml(value) : "";
      final String link = row.get("link") instanceof String value ? value : "";
      if (title.isBlank()
          || snippet.isBlank()
          || link.isBlank()
          || !relevant(query, title + " " + snippet)
          || !matchesSite(query, link)) continue;
      results
          .append(title)
          .append('\n')
          .append(snippet)
          .append("\nSource: ")
          .append(link)
          .append("\n\n");
      if (++count >= Math.max(1, maxResults)) break;
    }
    return count == 0 ? Optional.empty() : Optional.of(results.toString().trim());
  }

  // ---------------------------------------------------------------------------
  // Provider 1: DuckDuckGo Instant Answer API
  // ---------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  Optional<String> searchDuckDuckGo(final String query) throws IOException {
    final String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
    final String url = DDG_API_URL + "?q=" + encoded + "&format=json&no_html=1&skip_disambig=1";

    final HttpHeaders headers = new HttpHeaders();
    headers.set(
        HttpHeaders.USER_AGENT,
        "Mozilla/5.0 (compatible; Ullavox-ChatBot/1.6.0; +https://github.com/manojisnow/ullavox)");
    headers.set(HttpHeaders.ACCEPT, "application/json");
    final HttpEntity<Void> entity = new HttpEntity<>(headers);
    final ResponseEntity<String> response =
        restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

    final String body = response.getBody();
    if (body == null || body.isEmpty()) {
      return Optional.empty();
    }

    final Map<String, Object> json = MAPPER.readValue(body, Map.class);
    final StringBuilder results = new StringBuilder(64);
    results.append("Search results for: ").append(query).append(PARAGRAPH_BREAK);

    final String abstractText = (String) json.getOrDefault("AbstractText", "");
    final String abstractSource = (String) json.getOrDefault("AbstractSource", "");
    final String abstractUrl = (String) json.getOrDefault("AbstractURL", "");
    if (abstractText != null && relevant(query, abstractText) && matchesSite(query, abstractUrl)) {
      results.append("Summary");
      if (abstractSource != null && !abstractSource.isEmpty()) {
        results.append(" (").append(abstractSource).append(')');
      }
      results.append(": ").append(abstractText).append(PARAGRAPH_BREAK);
      if (abstractUrl != null && !abstractUrl.isBlank()) {
        results.append("Source: ").append(abstractUrl).append(PARAGRAPH_BREAK);
      }
    }

    final String answer = (String) json.getOrDefault("Answer", "");
    if (answer != null
        && !answer.isBlank()
        && !answer.startsWith("URL Decoded:")
        && !SITE.matcher(query).find()) {
      results.append("Answer: ").append(answer).append(PARAGRAPH_BREAK);
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
        if (text != null && relevant(query, text) && matchesSite(query, firstUrl)) {
          results.append("- ").append(text);
          if (firstUrl != null && !firstUrl.isEmpty()) {
            results.append(" (").append(firstUrl).append(')');
          }
          results.append('\n');
          count++;
        }
      }
    }

    final String header = "Search results for: " + query + PARAGRAPH_BREAK;
    if (results.toString().equals(header)) {
      return Optional.empty();
    }
    return Optional.of(results.toString().trim());
  }

  // ---------------------------------------------------------------------------
  // Provider 2: Wikipedia REST API
  // ---------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  Optional<String> searchWikipedia(final String query) throws IOException {
    if (!matchesSite(query, "https://en.wikipedia.org/")) return Optional.empty();
    // Step 1: find a relevant article title
    final String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
    final String searchUrl =
        WIKIPEDIA_SEARCH_URL
            + "?action=query&list=search&srsearch="
            + encoded
            + "&format=json&srlimit=5&srprop=snippet&utf8=1";

    final HttpHeaders wikiHeaders = new HttpHeaders();
    wikiHeaders.set(
        HttpHeaders.USER_AGENT,
        "Mozilla/5.0 (compatible; Ullavox-ChatBot/1.6.0; +https://github.com/manojisnow/ullavox)");
    final HttpEntity<Void> entity = new HttpEntity<>(wikiHeaders);
    final ResponseEntity<String> searchResponse =
        restTemplate.exchange(searchUrl, HttpMethod.GET, entity, String.class);

    final String searchBody = searchResponse.getBody();
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

    final String articleTitle =
        searchResults.stream()
            .filter(
                row ->
                    relevant(
                        query,
                        String.valueOf(row.get("title"))
                            + " "
                            + stripHtml(String.valueOf(row.get("snippet")))))
            .map(row -> (String) row.get("title"))
            .filter(title -> title != null && !title.isBlank())
            .findFirst()
            .orElse(null);
    if (articleTitle == null || articleTitle.isEmpty()) {
      return Optional.empty();
    }

    // Step 2: fetch the article summary
    final String normalizedTitle = articleTitle.replace(' ', '_');
    final String summaryUrl =
        WIKIPEDIA_SUMMARY_URL + URLEncoder.encode(normalizedTitle, StandardCharsets.UTF_8);

    final ResponseEntity<String> summaryResponse =
        restTemplate.exchange(summaryUrl, HttpMethod.GET, entity, String.class);

    final String summaryBody = summaryResponse.getBody();
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
        extract.strip().contains("\n")
            ? extract.strip().substring(0, extract.strip().indexOf('\n'))
            : extract.strip();
    final String summary =
        firstParagraph.length() > 600 ? firstParagraph.substring(0, 600) + "..." : firstParagraph;

    if (summary.isBlank() || !relevant(query, articleTitle + " " + summary))
      return Optional.empty();

    @SuppressWarnings("unchecked")
    final Map<String, Object> contentUrls =
        (Map<String, Object>) summaryJson.getOrDefault("content_urls", Map.of());
    @SuppressWarnings("unchecked")
    final Map<String, Object> desktop =
        (Map<String, Object>) contentUrls.getOrDefault("desktop", Map.of());
    final String pageUrl = (String) desktop.getOrDefault("page", "");

    final StringBuilder result = new StringBuilder(64);
    result.append("Wikipedia: ").append(articleTitle).append(PARAGRAPH_BREAK).append(summary);
    if (!pageUrl.isEmpty()) {
      result.append("\n\nSource: ").append(pageUrl);
    }
    return Optional.of(result.toString().trim());
  }

  // ---------------------------------------------------------------------------
  // Provider 3: Google News RSS
  // ---------------------------------------------------------------------------

  Optional<String> searchGoogleNewsRss(final String query)
      throws IOException, ParserConfigurationException, SAXException {
    final String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
    final String url = GOOGLE_NEWS_RSS_URL + "?q=" + encoded + "&hl=en-US&gl=US&ceid=US:en";

    final HttpHeaders headers = new HttpHeaders();
    headers.set(
        HttpHeaders.USER_AGENT,
        "Mozilla/5.0 (compatible; Ullavox-ChatBot/1.6.0; +https://github.com/manojisnow/ullavox)");
    final HttpEntity<Void> entity = new HttpEntity<>(headers);
    final ResponseEntity<String> response =
        restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

    final String body = response.getBody();
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

    final StringBuilder results = new StringBuilder(64);
    results
        .append("News results for: ")
        .append(query)
        .append(PARAGRAPH_BREAK)
        .append(
            "Headlines only, not full articles. A version mentioned here does not establish the latest stable release or official compatibility.\n\n");

    int count = 0;
    final int limit = items.getLength();
    for (int i = 0; i < limit; i++) {
      final Element item = (Element) items.item(i);
      final String title = getXmlText(item, "title");
      final String link = getXmlText(item, "link");
      final String pubDate = getXmlText(item, "pubDate");

      if (!title.isEmpty()
          && relevant(query, title)
          && matchesSite(query, getXmlSource(item, link))) {
        results.append(i + 1).append(". ").append(title).append('\n');
        if (!pubDate.isEmpty()) {
          results.append("   ").append(pubDate).append('\n');
        }
        if (!link.isEmpty()) {
          results.append("   ").append(link).append('\n');
        }
        results.append('\n');
        if (++count >= Math.max(1, maxResults)) break;
      }
    }

    final String header =
        "News results for: "
            + query
            + PARAGRAPH_BREAK
            + "Headlines only, not full articles. A version mentioned here does not establish the latest stable release or official compatibility.\n\n";
    if (results.toString().equals(header)) {
      return Optional.empty();
    }
    return Optional.of(results.toString().trim());
  }

  // ---------------------------------------------------------------------------
  // Utilities
  // ---------------------------------------------------------------------------

  private static String getXmlSource(final Element item, final String fallback) {
    final NodeList sources = item.getElementsByTagName("source");
    return sources.getLength() == 0 ? fallback : ((Element) sources.item(0)).getAttribute("url");
  }

  static boolean matchesSite(final String query, final String url) {
    final Matcher site = SITE.matcher(query);
    try {
      final URI uri = URI.create(url == null ? "" : url);
      final String host = uri.getHost();
      if (host == null
          || !("https".equalsIgnoreCase(uri.getScheme())
              || "http".equalsIgnoreCase(uri.getScheme())))
        return !site.find() && (url == null || url.isBlank());
      if (!site.find()) return true;
      final String requested = site.group(1).toLowerCase(Locale.ROOT);
      return host.equalsIgnoreCase(requested)
          || host.toLowerCase(Locale.ROOT).endsWith("." + requested);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  static boolean relevant(final String query, final String text) {
    if (text == null || text.isBlank()) return false;
    final Set<String> terms = new java.util.HashSet<>();
    for (final String term :
        SITE.matcher(query.toLowerCase(Locale.ROOT)).replaceAll("").split("[^\\p{L}\\p{N}]+")) {
      if (term.length() > 1 && !QUERY_MODIFIERS.contains(term) && !term.matches("[0-9]+"))
        terms.add(term);
    }
    if (terms.isEmpty()) return true;
    final Set<String> words =
        Set.copyOf(List.of(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")));
    final long matches = terms.stream().filter(words::contains).count();
    return matches >= Math.max(1, (int) Math.ceil(terms.size() * 0.6));
  }

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
