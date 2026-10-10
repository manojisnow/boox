package com.example.chatapp.tool;

import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Search adapter with a small model-facing schema and runtime-owned provider metadata. */
@Component
@ConditionalOnExpression(
    "${tools.websearch.enabled:true} and '${tools.websearch.provider:legacy}' == 'parallel'")
public class ParallelSearchTool implements Tool {
  private final ParallelWebClient client;

  public ParallelSearchTool(final ParallelWebClient client) {
    this.client = client;
  }

  @Override
  public String getName() {
    return "web_search";
  }

  @Override
  public String getDescription() {
    return "Search public web sources for current facts using Parallel. State the information needed and preferred sources in query. Results are excerpts, not guaranteed facts. Use web_fetch to read source pages if details are missing.";
  }

  @Override
  public Map<String, Object> getParameterSchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of(
            "query",
            Map.of(
                "type",
                "string",
                "description",
                "Focused search query including source or freshness requirements",
                "maxLength",
                1_000)),
        "required",
        List.of("query"),
        "additionalProperties",
        false);
  }

  @Override
  public String execute(final Map<String, Object> arguments) {
    return "Error: Web access requires trusted conversation context.";
  }

  @Override
  public String execute(
      final Map<String, Object> arguments, final String conversationId, final String model) {
    if (!(arguments.get("query") instanceof String query)
        || query.isBlank()
        || query.length() > 1_000)
      return "Error: Provide a non-empty search query of at most 1000 characters.";
    return client.call(
        "web_search",
        Map.of("objective", query, "search_queries", List.of(query)),
        conversationId,
        model);
  }
}
