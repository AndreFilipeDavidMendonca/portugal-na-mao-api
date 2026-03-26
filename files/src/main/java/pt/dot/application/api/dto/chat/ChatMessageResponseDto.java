package pt.dot.application.api.dto.chat;

import pt.dot.application.db.enums.ChatMessageType;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponseDto(
        UUID id,
        UUID senderId,
        String senderDisplayName,
        ChatMessageType type,
        String body,
        Long poiId,
        String poiName,
        String poiImage,
        Instant createdAt
) {}
