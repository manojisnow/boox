package com.example.chatapp.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.chatapp.engine.ChatContextService;
import com.example.chatapp.engine.GenerationConfig;
import com.example.chatapp.tool.ToolRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentExecutionServiceTest {
  private final AgentModelClient client = mock(AgentModelClient.class);
  private final ToolRegistry tools = mock(ToolRegistry.class);
  private final AgentRunService runs = mock(AgentRunService.class);
  private final ChatContextService context = mock(ChatContextService.class);
  private final ObjectMapper mapper = new ObjectMapper();
  private AgentExecutionService service;

  @BeforeEach
  void setup() {
    service = new AgentExecutionService(client, tools, runs, context);
    when(runs.priorEvidence(any(), any())).thenReturn("");
    when(context.getGenerationConfig("conv")).thenReturn(GenerationConfig.empty());
    when(context.getContext("conv")).thenReturn(List.of());
    when(tools.getToolDefinitions())
        .thenReturn(List.of(Map.of("function", Map.of("name", "web_search"))));
  }

  private AgentRunView run() {
    return new AgentRunView(
        "run",
        "conv",
        "Explain",
        "ollama",
        "model",
        AgentRunStatus.RUNNING,
        null,
        null,
        Instant.now(),
        Instant.now(),
        List.of());
  }

  private JsonNode json(final String value) throws Exception {
    return mapper.readTree(value);
  }

  @Test
  void reviewsDraftAndNeverChangesSharedSystemPrompt() throws Exception {
    when(context.getSystemPrompt("conv")).thenReturn("Concise");
    when(client.chat(any(), any(), any(), any()))
        .thenReturn(json("{\"content\":\"draft\"}"), json("{\"content\":\"reviewed answer\"}"));
    assertEquals("reviewed answer", service.execute(run()));
    verify(client, times(2)).chat(eq("model"), any(), any(), any());
    verify(context, never()).setSystemPrompt(any(), any());
    verify(runs)
        .progress(eq("run"), eq(AgentStepType.MODEL), any(), isNull(), eq(AgentStepStatus.RUNNING));
  }

  @Test
  void preservesToolCallsAndActualEvidenceDuringReview() throws Exception {
    when(client.chat(any(), any(), any(), any()))
        .thenReturn(
            json(
                "{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"function\":{\"name\":\"web_search\",\"arguments\":{\"query\":\"facts\"}}}]}"),
            json("{\"content\":\"draft\"}"),
            json("{\"content\":\"checked\"}"));
    when(tools.executeTool(eq("web_search"), any()))
        .thenReturn("Actual source: https://example.com/facts");
    assertEquals("checked", service.execute(run()));
    final ArgumentCaptor<List<Map<String, Object>>> messages = ArgumentCaptor.forClass(List.class);
    verify(client, times(3)).chat(any(), messages.capture(), any(), any());
    assertTrue(messages.getValue().stream().anyMatch(row -> row.containsKey("tool_calls")));
    assertTrue(
        messages.getValue().stream()
            .anyMatch(
                row ->
                    "web_search".equals(row.get("tool_name"))
                        && row.get("content").toString().contains("Actual source")));
  }

  @Test
  void rejectsUnavailableToolsAndMalformedArguments() throws Exception {
    when(client.chat(any(), any(), any(), any()))
        .thenReturn(json("{\"tool_calls\":[{\"function\":{\"name\":\"shell\"}}]}"));
    assertThrows(IllegalStateException.class, () -> service.execute(run()));
    verify(tools, never()).executeTool(any(), any());
    when(client.chat(any(), any(), any(), any()))
        .thenReturn(
            json(
                "{\"tool_calls\":[{\"function\":{\"name\":\"web_search\",\"arguments\":{\"query\":{\"type\":\"string\"}}}}]}"));
    assertThrows(IllegalStateException.class, () -> service.execute(run()));
  }

  @Test
  void emptyAnswersAndModelErrorsAreFailures() throws Exception {
    when(client.chat(any(), any(), any(), any())).thenReturn(json("{\"content\":\"\"}"));
    assertThrows(IllegalStateException.class, () -> service.execute(run()));
    when(client.chat(any(), any(), any(), any())).thenThrow(new IllegalStateException("offline"));
    assertThrows(IllegalStateException.class, () -> service.execute(run()));
    doReturn(json("{\"content\":\"draft\"}"), json("{\"content\":\"\"}"))
        .when(client)
        .chat(any(), any(), any(), any());
    assertThrows(IllegalStateException.class, () -> service.execute(run()));
  }

  @Test
  void rejectsInventedCitationsAndAcceptsOnlyEvidenceUrls() throws Exception {
    when(client.chat(any(), any(), any(), any()))
        .thenReturn(
            json("{\"content\":\"draft\"}"),
            json("{\"content\":\"Source https://invented.example/facts\"}"));
    assertThrows(IllegalStateException.class, () -> service.execute(run()));
    reset(client);
    when(runs.requestJson("run"))
        .thenReturn(
            "{\"attachments\":[{\"name\":\"source.md\",\"content\":\"https://example.com/facts\"}]}");
    when(client.chat(any(), any(), any(), any()))
        .thenReturn(
            json("{\"content\":\"draft\"}"),
            json("{\"content\":\"Source https://example.com/facts.\"}"));
    assertEquals("Source https://example.com/facts.", service.execute(run()));
  }

  @Test
  void cancellationStopsBeforeToolsOrFinalAnswer() throws Exception {
    when(runs.isCancellationRequested("run")).thenReturn(true);
    assertThrows(CancellationException.class, () -> service.execute(run()));
    verifyNoInteractions(client);
  }

  @Test
  void boundedSearchLimitStopsRepeatedCalls() throws Exception {
    when(client.chat(any(), any(), any(), any()))
        .thenReturn(
            json(
                "{\"tool_calls\":[{\"function\":{\"name\":\"web_search\",\"arguments\":\"{\\\"query\\\":\\\"facts\\\"}\"}}]}"));
    when(tools.executeTool(any(), any())).thenReturn("No results found.");
    assertThrows(IllegalStateException.class, () -> service.execute(run()));
    verify(tools, times(5)).executeTool(any(), any());
  }

  @Test
  void filesAndSettingsAreIncludedWithoutLosingBlankResetValues() throws Exception {
    when(runs.requestJson("run"))
        .thenReturn(
            "{\"attachments\":[{\"name\":\"data.csv\",\"content\":\"item,value\\na,7\"}],\"chatRequest\":{\"replaceGenerationConfig\":true,\"temperature\":0.4,\"numCtx\":8192,\"stopSequences\":[\"END\"],\"images\":[\"base64\"],\"systemPrompt\":\"Concise\"}}");
    when(context.getContext("conv"))
        .thenReturn(
            List.of(
                Map.of("role", "tool", "content", "old tool"),
                Map.of("role", "user", "content", "Explain"),
                Map.of("role", "assistant", "content", "Previous answer")));
    when(client.chat(any(), any(), any(), any()))
        .thenReturn(json("{\"content\":\"draft\"}"), json("{\"content\":\"answer\"}"));
    assertEquals("answer", service.execute(run()));
    final ArgumentCaptor<Map<String, Object>> options = ArgumentCaptor.forClass(Map.class);
    final ArgumentCaptor<List<Map<String, Object>>> messages = ArgumentCaptor.forClass(List.class);
    verify(client, times(2)).chat(any(), messages.capture(), options.capture(), any());
    assertEquals(0.4, options.getAllValues().getFirst().get("temperature"));
    assertFalse(options.getValue().containsKey("stop"));
    assertTrue(
        messages.getValue().stream()
            .anyMatch(row -> row.get("content").toString().contains("data.csv")));
    assertTrue(messages.getValue().stream().anyMatch(row -> row.containsKey("images")));
    verify(context).setGenerationConfig(eq("conv"), any());
  }

  @Test
  void fileOnlyScopeHidesToolsAndRejectsModelSearchRequests() throws Exception {
    when(runs.requestJson("run"))
        .thenReturn("{\"attachments\":[{\"name\":\"totals.csv\",\"content\":\"amount\\n5\\n7\"}]}");
    when(client.chat(any(), any(), any(), any()))
        .thenReturn(
            json(
                "{\"tool_calls\":[{\"function\":{\"name\":\"web_search\",\"arguments\":{\"query\":\"totals\"}}}]}"));
    assertThrows(IllegalStateException.class, () -> service.execute(run()));
    verify(client).chat(any(), any(), any(), eq(List.of()));
    verify(tools, never()).executeTool(any(), any());
  }

  @Test
  void mixedScopeAllowsSearchAndExplicitFilesOnlyDisablesItWithoutAttachments() throws Exception {
    when(runs.requestJson("run"))
        .thenReturn(
            "{\"allowWebSearch\":true,\"attachments\":[{\"name\":\"data.csv\",\"content\":\"amount\\n7\"}]}");
    when(client.chat(any(), any(), any(), any()))
        .thenReturn(json("{\"content\":\"draft\"}"), json("{\"content\":\"answer\"}"));
    assertEquals("answer", service.execute(run()));
    verify(client).chat(any(), any(), any(), argThat(defs -> !defs.isEmpty()));
    clearInvocations(client);
    when(runs.requestJson("run")).thenReturn("{\"allowWebSearch\":false}");
    assertEquals("answer", service.execute(run()));
    verify(client, times(2)).chat(any(), any(), any(), eq(List.of()));
  }

  @Test
  void followUpReceivesOriginalFileAndReviewTargetsLatestQuestion() throws Exception {
    when(runs.requestJson("run"))
        .thenReturn(
            "{\"attachments\":[{\"name\":\"totals.csv\",\"content\":\"amount\\n5\\n7\"}],\"allowWebSearch\":false}");
    when(context.getContext("conv"))
        .thenReturn(
            List.of(
                Map.of("role", "user", "content", "Calculate total"),
                Map.of("role", "assistant", "content", "12"),
                Map.of("role", "user", "content", "Explain")));
    when(client.chat(any(), any(), any(), any()))
        .thenReturn(
            json("{\"content\":\"Add 5 and 7\"}"),
            json("{\"content\":\"Start at 5, add 7, yielding 12\"}"));
    assertEquals("Start at 5, add 7, yielding 12", service.execute(run()));
    final ArgumentCaptor<List<Map<String, Object>>> transcript =
        ArgumentCaptor.forClass(List.class);
    verify(client, times(2)).chat(any(), transcript.capture(), any(), eq(List.of()));
    assertTrue(
        transcript
            .getValue()
            .getLast()
            .get("content")
            .toString()
            .contains("latest user request is: Explain"));
    assertTrue(
        transcript.getValue().stream()
            .anyMatch(row -> row.get("content").toString().contains("amount\n5\n7")));
  }

  @Test
  void followUpCanCitePreviouslyRetrievedEvidence() throws Exception {
    when(runs.priorEvidence("conv", "run")).thenReturn("Source https://example.com/facts");
    when(client.chat(any(), any(), any(), any()))
        .thenReturn(
            json("{\"content\":\"draft\"}"),
            json("{\"content\":\"Source https://example.com/facts\"}"));
    assertEquals("Source https://example.com/facts", service.execute(run()));
  }

  @Test
  void absentFilesAreExplicitEvenWhenHistoryContainsOldFileExcerpt() throws Exception {
    when(context.getContext("conv"))
        .thenReturn(List.of(Map.of("role", "assistant", "content", "totals.csv: b,7")));
    when(client.chat(any(), any(), any(), any()))
        .thenReturn(json("{\"content\":\"No current file access\"}"));
    service.execute(run());
    final ArgumentCaptor<List<Map<String, Object>>> transcript =
        ArgumentCaptor.forClass(List.class);
    verify(client, times(2)).chat(any(), transcript.capture(), any(), any());
    assertTrue(
        transcript.getValue().stream()
            .anyMatch(row -> row.get("content").toString().contains("Current file access: NONE")));
    assertTrue(
        transcript
            .getValue()
            .getLast()
            .get("content")
            .toString()
            .contains("never claim direct file verification"));
  }
}
