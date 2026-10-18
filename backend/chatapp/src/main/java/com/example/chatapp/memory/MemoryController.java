package com.example.chatapp.memory;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Local preferences, project, note and per-conversation context controls. */
@RestController
@RequestMapping("/api")
@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
    value = "EI_EXPOSE_REP2",
    justification = "Spring-managed dependencies are shared application services.")
public class MemoryController {
  private final MemoryService memory;

  public MemoryController(MemoryService memory) {
    this.memory = memory;
  }

  @GetMapping("/preferences")
  public Map<String, Object> preferences() {
    return memory.preferences();
  }

  @PutMapping("/preferences")
  public Map<String, Object> savePreferences(@RequestBody Map<String, Object> input) {
    return memory.savePreferences(input);
  }

  @DeleteMapping("/preferences")
  public Map<String, Object> clearPreferences(@RequestParam(required = false) Long version) {
    return memory.clearPreferences(version);
  }

  @GetMapping("/projects")
  public List<Map<String, Object>> projects() {
    return memory.projects();
  }

  @GetMapping("/projects/{id}")
  public Map<String, Object> project(@PathVariable String id) {
    return memory.project(id);
  }

  @PostMapping("/projects")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Map<String, Object> createProject(@RequestBody Map<String, Object> input) {
    return memory.saveProject(null, input);
  }

  @PutMapping("/projects/{id}")
  public Map<String, Object> updateProject(
      @PathVariable String id, @RequestBody Map<String, Object> input) {
    return memory.saveProject(id, input);
  }

  @DeleteMapping("/projects/{id}")
  public ResponseEntity<Void> deleteProject(
      @PathVariable String id, @RequestParam(required = false) Long version) {
    memory.deleteProject(id, version);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/projects/{projectId}/memories")
  public List<Map<String, Object>> memories(@PathVariable String projectId) {
    return memory.memories(projectId);
  }

  @PostMapping("/projects/{projectId}/memories")
  @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
  public Map<String, Object> createMemory(
      @PathVariable String projectId, @RequestBody Map<String, Object> input) {
    return memory.saveMemory(projectId, null, input);
  }

  @PutMapping("/projects/{projectId}/memories/{id}")
  public Map<String, Object> updateMemory(
      @PathVariable String projectId,
      @PathVariable String id,
      @RequestBody Map<String, Object> input) {
    return memory.saveMemory(projectId, id, input);
  }

  @DeleteMapping("/projects/{projectId}/memories/{id}")
  public ResponseEntity<Void> deleteMemory(
      @PathVariable String projectId,
      @PathVariable String id,
      @RequestParam(required = false) Long version) {
    memory.deleteMemory(projectId, id, version);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/conversations/{id}/settings")
  public Map<String, Object> settings(@PathVariable String id) {
    return memory.conversationSettings(id);
  }

  @PatchMapping("/conversations/{id}/settings")
  public Map<String, Object> settings(
      @PathVariable String id, @RequestBody Map<String, Object> input) {
    return memory.saveConversationSettings(id, input);
  }
}
