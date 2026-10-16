package com.example.chatapp.context;

import java.util.*;

/** Conservative complete-request accounting. Tool exchange groups are never sliced. */
public final class PromptBudget {
  private PromptBudget() {}

  public static int estimate(String text) {
    return text == null ? 0 : (text.length() + 3) / 4;
  }

  public static int cost(Map<String, Object> message) {
    int result = 8 + estimate(Objects.toString(message.get("content"), ""));
    if (message.get("images") instanceof List<?> images) result += 768 * images.size();
    if (message.containsKey("tool_calls")) result += estimate(message.get("tool_calls").toString());
    return result;
  }

  public static List<Map<String, Object>> fit(
      List<Map<String, Object>> messages, Object tools, int capacity, int reserve, int ceiling) {
    int available =
        Math.min(ceiling > 0 ? ceiling : Integer.MAX_VALUE, capacity - reserve - 128)
            - estimate(Objects.toString(tools, ""));
    List<Map<String, Object>> result = new ArrayList<>(messages);
    while (result.stream().mapToInt(PromptBudget::cost).sum() > available) {
      int latestUser = -1;
      for (int i = 0; i < result.size(); i++)
        if ("user".equals(result.get(i).get("role"))) latestUser = i;
      int first = 0;
      while (first < result.size() && "system".equals(result.get(first).get("role"))) first++;
      if (first >= latestUser || first >= result.size()) {
        int optionalSummary = -1;
        for (int i = 0; i < result.size(); i++)
          if ("system".equals(result.get(i).get("role"))
              && Objects.toString(result.get(i).get("content"), "")
                  .startsWith("Summary of earlier conversation")) optionalSummary = i;
        if (optionalSummary >= 0) {
          result.remove(optionalSummary);
          continue;
        }
        throw new IllegalArgumentException(
            "The required request and context exceed the model input budget. Reduce attachments, instructions or selected notes, or increase context size.");
      }
      int next = first + 1;
      while (next < result.size() && !"user".equals(result.get(next).get("role"))) next++;
      if (next > latestUser)
        throw new IllegalArgumentException(
            "The complete tool exchange exceeds the model input budget.");
      result.subList(first, next).clear();
    }
    return result;
  }
}
