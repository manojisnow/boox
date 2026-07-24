package com.example.chatapp.controller;

import com.example.chatapp.controller.dto.ConversationSummary;
import com.example.chatapp.controller.dto.MessageView;
import com.example.chatapp.controller.dto.RenameRequest;
import com.example.chatapp.persistence.ConversationService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Conversation history and management endpoints backing the sidebar. */
@RestController
@RequestMapping("/api/conversations")
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP2"},
    justification = "Injected Spring service is a singleton bean; safe to retain.")
public class ConversationController {

  private final ConversationService conversationService;

  public ConversationController(final ConversationService conversationService) {
    this.conversationService = conversationService;
  }

  @GetMapping
  public List<ConversationSummary> list() {
    return conversationService.list();
  }

  @GetMapping("/{id}/messages")
  public List<MessageView> messages(@PathVariable final String id) {
    return conversationService.messages(id);
  }

  @PatchMapping("/{id}")
  public ResponseEntity<ConversationSummary> rename(
      @PathVariable final String id, @Valid @RequestBody final RenameRequest request) {
    return conversationService
        .rename(id, request.getTitle())
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable final String id) {
    conversationService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
