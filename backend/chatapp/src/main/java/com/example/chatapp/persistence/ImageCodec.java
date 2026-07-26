package com.example.chatapp.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encodes/decodes a message's attached images (base64 strings) to and from the JSON text stored in
 * {@link ChatMessageEntity#getImages()}. Shared by {@link JpaChatContextService} (writes) and
 * {@link ConversationService} (reads, for resuming a conversation).
 */
final class ImageCodec {

  private static final Logger LOGGER = LoggerFactory.getLogger(ImageCodec.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

  private ImageCodec() {}

  /** Returns {@code null} for an empty/null list, so text-only messages store nothing extra. */
  static String toJson(final List<String> images) {
    if (images == null || images.isEmpty()) {
      return null;
    }
    try {
      return MAPPER.writeValueAsString(images);
    } catch (JsonProcessingException e) {
      LOGGER.warn("Failed to encode message images; storing none: {}", e.getMessage());
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
      LOGGER.warn("Failed to decode stored message images; returning none: {}", e.getMessage());
      return List.of();
    }
  }
}
