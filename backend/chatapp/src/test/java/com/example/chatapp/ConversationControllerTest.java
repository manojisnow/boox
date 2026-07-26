package com.example.chatapp;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.chatapp.controller.ConversationController;
import com.example.chatapp.controller.dto.ConversationSummary;
import com.example.chatapp.controller.dto.MessageView;
import com.example.chatapp.persistence.ConversationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConversationControllerTest {

  @Mock private ConversationService conversationService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ConversationController(conversationService)).build();
  }

  @Test
  void list_returnsSummaries() throws Exception {
    when(conversationService.list())
        .thenReturn(
            List.of(
                new ConversationSummary(
                    "c1", "Title", "ollama", "m1", Instant.now(), null, null, List.of())));
    mockMvc
        .perform(get("/api/conversations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("c1"))
        .andExpect(jsonPath("$[0].title").value("Title"));
  }

  @Test
  void messages_returnsViews() throws Exception {
    when(conversationService.messages("c1"))
        .thenReturn(
            List.of(
                new MessageView("user", "hi", List.of()),
                new MessageView("assistant", "hello", List.of())));
    mockMvc
        .perform(get("/api/conversations/c1/messages"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[1].role").value("assistant"));
  }

  @Test
  void rename_existing_returnsOk() throws Exception {
    when(conversationService.rename(eq("c1"), eq("New")))
        .thenReturn(
            Optional.of(
                new ConversationSummary(
                    "c1", "New", "ollama", "m1", Instant.now(), null, null, List.of())));
    mockMvc
        .perform(
            patch("/api/conversations/c1")
                .contentType("application/json")
                .content("{\"title\":\"New\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("New"));
  }

  @Test
  void rename_missing_returnsNotFound() throws Exception {
    when(conversationService.rename(eq("nope"), eq("New"))).thenReturn(Optional.empty());
    mockMvc
        .perform(
            patch("/api/conversations/nope")
                .contentType("application/json")
                .content("{\"title\":\"New\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void delete_returnsNoContent() throws Exception {
    mockMvc.perform(delete("/api/conversations/c1")).andExpect(status().isNoContent());
    verify(conversationService).delete("c1");
  }
}
