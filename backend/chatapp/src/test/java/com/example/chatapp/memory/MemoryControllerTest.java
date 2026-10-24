package com.example.chatapp.memory;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/** HTTP binding contracts, independent of the SQL domain integration tests. */
@WebMvcTest(MemoryController.class)
class MemoryControllerTest {
  @Autowired private MockMvc mvc;
  @MockitoBean private MemoryService memory;

  @Test
  void preferencesReadSaveAndVersionedClear() throws Exception {
    var profile =
        Map.<String, Object>of(
            "version", 3, "onboardingStatus", "completed", "instructions", "Use examples");
    when(memory.preferences()).thenReturn(profile);
    when(memory.savePreferences(anyMap())).thenReturn(profile);
    when(memory.clearPreferences(3L))
        .thenReturn(Map.of("version", 4, "onboardingStatus", "skipped"));
    mvc.perform(get("/api/preferences"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(3));
    mvc.perform(
            put("/api/preferences")
                .contentType("application/json")
                .content(
                    "{\"version\":2,\"onboardingStatus\":\"completed\",\"instructions\":\"Use examples\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instructions").value("Use examples"));
    verify(memory)
        .savePreferences(
            argThat(
                input ->
                    Integer.valueOf(2).equals(input.get("version"))
                        && "completed".equals(input.get("onboardingStatus"))));
    mvc.perform(delete("/api/preferences").param("version", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.onboardingStatus").value("skipped"));
    verify(memory).clearPreferences(3L);
  }

  @Test
  void preferenceConflictPreservesErrorStatusAndReason() throws Exception {
    when(memory.savePreferences(anyMap()))
        .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Reload before saving"));
    mvc.perform(put("/api/preferences").contentType("application/json").content("{\"version\":0}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("Reload before saving"));
    when(memory.clearPreferences(0L))
        .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Context is active"));
    mvc.perform(delete("/api/preferences").param("version", "0"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("Context is active"));
  }

  @Test
  void projectRoutesExposeCreatedAndNoContentContracts() throws Exception {
    var project = Map.<String, Object>of("id", "p", "name", "Ullavox", "version", 0);
    when(memory.projects()).thenReturn(List.of(project));
    when(memory.project("p")).thenReturn(project);
    when(memory.saveProject(isNull(), anyMap())).thenReturn(project);
    when(memory.saveProject(eq("p"), anyMap())).thenReturn(project);
    mvc.perform(get("/api/projects"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("p"));
    mvc.perform(get("/api/projects/p"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Ullavox"));
    mvc.perform(
            post("/api/projects").contentType("application/json").content("{\"name\":\"Ullavox\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("p"));
    mvc.perform(
            put("/api/projects/p")
                .contentType("application/json")
                .content("{\"version\":0,\"name\":\"Ullavox\"}"))
        .andExpect(status().isOk());
    mvc.perform(delete("/api/projects/p").param("version", "0")).andExpect(status().isNoContent());
    verify(memory).deleteProject("p", 0L);
  }

  @Test
  void notesPreserveStableSourceIdentifiersAndVersionedOperations() throws Exception {
    var note =
        Map.<String, Object>of(
            "id",
            "n",
            "content",
            "Offline",
            "sourceConversationId",
            "c",
            "sourceMessageId",
            42,
            "version",
            0);
    when(memory.memories("p")).thenReturn(List.of(note));
    when(memory.saveMemory(eq("p"), isNull(), anyMap())).thenReturn(note);
    when(memory.saveMemory(eq("p"), eq("n"), anyMap())).thenReturn(note);
    mvc.perform(get("/api/projects/p/memories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].sourceMessageId").value(42));
    mvc.perform(
            post("/api/projects/p/memories")
                .contentType("application/json")
                .content(
                    "{\"title\":\"Local\",\"content\":\"Offline\",\"kind\":\"constraint\",\"sourceConversationId\":\"c\",\"sourceMessageId\":42}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("n"));
    verify(memory)
        .saveMemory(
            eq("p"),
            isNull(),
            argThat(
                input ->
                    "c".equals(input.get("sourceConversationId"))
                        && Integer.valueOf(42).equals(input.get("sourceMessageId"))));
    mvc.perform(
            put("/api/projects/p/memories/n")
                .contentType("application/json")
                .content(
                    "{\"version\":0,\"content\":\"Offline\",\"kind\":\"constraint\",\"pinned\":false}"))
        .andExpect(status().isOk());
    mvc.perform(delete("/api/projects/p/memories/n").param("version", "0"))
        .andExpect(status().isNoContent());
    verify(memory).deleteMemory("p", "n", 0L);
  }

  @Test
  void conversationSettingsPreserveExplicitClearingAndFalseFlags() throws Exception {
    when(memory.conversationSettings("missing"))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation unavailable"));
    mvc.perform(get("/api/conversations/missing/settings")).andExpect(status().isNotFound());
    when(memory.conversationSettings("c")).thenReturn(Map.of("id", "c", "version", 0));
    mvc.perform(get("/api/conversations/c/settings")).andExpect(status().isOk());
    when(memory.saveConversationSettings(eq("c"), anyMap()))
        .thenReturn(Map.of("id", "c", "version", 1, "usePreferences", false));
    mvc.perform(
            patch("/api/conversations/c/settings")
                .contentType("application/json")
                .content(
                    "{\"version\":0,\"projectId\":null,\"usePreferences\":false,\"useProjectContext\":false,\"systemPrompt\":\"\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.usePreferences").value(false));
    verify(memory)
        .saveConversationSettings(
            eq("c"),
            argThat(
                input ->
                    input.containsKey("projectId")
                        && input.get("projectId") == null
                        && Boolean.FALSE.equals(input.get("usePreferences"))
                        && Boolean.FALSE.equals(input.get("useProjectContext"))
                        && "".equals(input.get("systemPrompt"))));
  }
}
