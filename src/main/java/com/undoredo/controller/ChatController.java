package com.undoredo.controller;

import com.undoredo.group.ActionRefusedException;
import com.undoredo.model.ChatState;
import com.undoredo.service.ChatService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // GET CURRENT CHAT STATE
    @GetMapping("/state")
    public ResponseEntity<ChatState> getState() {
        return ResponseEntity.ok(
                chatService.getState()
        );
    }

    // SEND MESSAGE
    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(
            @RequestBody Map<String, String> request) {

        try {
            String text = request.get("text");

            ChatState state =
                    chatService.sendMessage(text);

            return ResponseEntity.ok(state);

        } catch (IllegalArgumentException e) {

            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // DELETE MESSAGE
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteMessage(
            @PathVariable long id) {

        try {
            ChatState state =
                    chatService.deleteMessage(id);

            return ResponseEntity.ok(state);

        } catch (IllegalArgumentException e) {

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    // DELETE FOR EVERYONE
    @PostMapping("/delete-all/{id}")
    public ResponseEntity<ChatState> deleteForEveryone(@PathVariable long id) {
        return ResponseEntity.ok(chatService.deleteForEveryone(id));
    }
    // UNDO
    @PostMapping("/undo")
    public ResponseEntity<ChatState> undo() {

        return ResponseEntity.ok(
                chatService.undo()
        );
    }

    // REDO
    @PostMapping("/redo")
    public ResponseEntity<ChatState> redo() {

        return ResponseEntity.ok(
                chatService.redo()
        );
    }

    // GET STATISTICS
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {

        return ResponseEntity.ok(
                Map.of(
                        "undoSize", chatService.getUndoSize(),
                        "redoSize", chatService.getRedoSize(),
                        "storedChars", chatService.getStoredChars()
                )
        );
    }

    // GET INTERNAL DOCUMENT
    @GetMapping("/document")
    public ResponseEntity<String> getDocument() {

        return ResponseEntity.ok(
                chatService.getDocument()
        );
    }

    // GET COMPLETE HISTORY
    @GetMapping("/history")
    public ResponseEntity<?> getHistory() {

        return ResponseEntity.ok(
                chatService.getHistory()
        );
    }

    // ---------------- GRUPI ----------------

    @GetMapping("/group")
    public ResponseEntity<Map<String, Object>> getGroup() {
        return ResponseEntity.ok(chatService.getGroupView());
    }

    @PostMapping("/group/leave")
    public ResponseEntity<Map<String, Object>> leaveGroup() {
        return ResponseEntity.ok(chatService.leaveGroup());
    }

    @PostMapping("/group/remove")
    public ResponseEntity<Map<String, Object>> removeMember(
            @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(chatService.removeMember(request.get("name")));
    }

    @PostMapping("/group/add")
    public ResponseEntity<Map<String, Object>> addMember(
            @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(chatService.addMember(request.get("name")));
    }

    @PostMapping("/group/simulate-add")
    public ResponseEntity<Map<String, Object>> simulateAdd(
            @RequestBody Map<String, String> request) {
        return ResponseEntity.ok(chatService.simulateOtherAdminAdds(request.get("name")));
    }

    @PostMapping("/reset")
    public ResponseEntity<ChatState> reset() {
        return ResponseEntity.ok(chatService.reset());
    }

    // ---------------- GABIMET ----------------

    // Undo ose Redo i refuzuar (afat, grup plot, anëtari rikthyer...)
    @ExceptionHandler(ActionRefusedException.class)
    public ResponseEntity<Map<String, String>> handleRefused(ActionRefusedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", e.getMessage()));
    }

    // Kërkesë e pavlefshme (p.sh. grup plot, emër bosh)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
    }
}