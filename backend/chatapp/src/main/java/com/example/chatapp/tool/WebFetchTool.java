package com.example.chatapp.tool;

import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Bounded public-page extraction through Parallel, never direct access to the local network. */
@Component
@ConditionalOnExpression(
    "${tools.websearch.enabled:true} and '${tools.websearch.provider:legacy}' == 'parallel'")
public class WebFetchTool implements Tool {
  private final ParallelWebClient client;

  public WebFetchTool(final ParallelWebClient client) {
    this.client = client;
  }

  @Override
  public String getName() {
    return "web_fetch";
  }

  @Override
  public String getDescription() {
    return "Read excerpts from up to three public web URLs. Use for user-provided links or source URLs returned by search, especially current official documentation. Supply a focused objective; excerpts can be incomplete or outdated.";
  }

  @Override
  public Map<String, Object> getParameterSchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of(
            "urls",
                Map.of(
                    "type",
                    "array",
                    "items",
                    Map.of("type", "string"),
                    "minItems",
                    1,
                    "maxItems",
                    3),
            "objective", Map.of("type", "string", "maxLength", 200)),
        "required",
        List.of("urls"),
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
    if (!(arguments.get("urls") instanceof List<?> urls)
        || urls.isEmpty()
        || urls.size() > 3
        || urls.stream()
            .anyMatch(
                url -> !(url instanceof String) || !ParallelWebClient.publicUrl((String) url)))
      return "Error: Provide one to three public HTTP or HTTPS page URLs without credentials or local addresses.";
    final Object objective =
        arguments.getOrDefault("objective", "Read the relevant facts on these pages.");
    if (!(objective instanceof String text) || text.length() > 200)
      return "Error: The page-reading objective must be text of at most 200 characters.";
    return client.call(
        "web_fetch",
        Map.of(
            "urls", urls, "objective", objective, "full_content", false, "allow_live_fetch", true),
        conversationId,
        model);
  }
}
