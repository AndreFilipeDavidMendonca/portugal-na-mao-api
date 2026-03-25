package pt.dot.application.api.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.dot.application.api.dto.chat.ChatMessageResponseDto;
import pt.dot.application.api.dto.chat.SendChatMessageDto;
import pt.dot.application.api.dto.chat.StartChatResponseDto;
import pt.dot.application.security.SecurityUtil;
import pt.dot.application.service.chat.ChatService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/with/{friendUserId}")
    public ResponseEntity<StartChatResponseDto> startChat(@PathVariable UUID friendUserId) {
        UUID currentUserId = SecurityUtil.getUserIdOrNull();
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        UUID conversationId = chatService.getOrCreateConversation(currentUserId, friendUserId);
        return ResponseEntity.ok(new StartChatResponseDto(conversationId));
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<List<ChatMessageResponseDto>> getMessages(@PathVariable UUID conversationId) {
        UUID currentUserId = SecurityUtil.getUserIdOrNull();
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(chatService.getMessages(currentUserId, conversationId));
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<Void> sendMessage(
            @PathVariable UUID conversationId,
            @RequestBody SendChatMessageDto dto
    ) {
        UUID currentUserId = SecurityUtil.getUserIdOrNull();
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        chatService.sendMessage(currentUserId, conversationId, dto.getBody());
        return ResponseEntity.ok().build();
    }
}