package com.example.chatapp.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encodes/decodes a {@code List<String>} to and from the JSON text stored in a single text column —
 * used for a message's attached images ({@link ChatMessageEntity#getImages()}) and a conversation's
 * stop sequences ({@link Conversation#getStopSequences()}). Shared by {@link JpaChatContextService}
 * (writes) and {@link ConversationService} (reads, for resuming a conversation).
 */
final class JsonListCodec {

  private static final Logger LOGGER = LoggerFactory.getLogger(JsonListCodec.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

  private JsonListCodec() {}

  /** Returns {@code null} for an empty/null list, so the common case stores nothing extra. */
  static String toJson(final List<String> values) {
    if (values == null || values.isEmpty()) {
      return null;
    }
    try {
      return MAPPER.writeValueAsString(values);
    } catch (JsonProcessingException e) {
      LOGGER.warn("Failed to encode list; storing none: {}", e.getMessage());
      return null;
    }
  }

  /** Returns an empty list for {@code null}/blank/malformed input. */
  static List<String> fromJson(final String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return MAPPER.readValue(json, STRING_LIST);
    } catch (JsonProcessingException e) {
      LOGGER.warn("Failed to decode stored list; returning none: {}", e.getMessage());
      return List.of();
    }
  }
}
