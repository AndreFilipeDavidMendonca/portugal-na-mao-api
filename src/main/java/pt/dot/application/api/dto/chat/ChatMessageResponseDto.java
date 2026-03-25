package pt.dot.application.api.dto.chat;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponseDto(
        UUID id,
        UUID senderId,
        String senderDisplayName,
        String body,
        Instant createdAt
) {}