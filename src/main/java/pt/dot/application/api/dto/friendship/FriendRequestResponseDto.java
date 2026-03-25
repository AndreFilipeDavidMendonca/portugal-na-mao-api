package pt.dot.application.api.dto.friendship;

import java.time.Instant;
import java.util.UUID;

public record FriendRequestResponseDto(
        UUID id,
        String requesterEmail,
        String requesterDisplayName,
        Instant createdAt
) {}