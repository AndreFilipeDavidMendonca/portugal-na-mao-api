package pt.dot.application.api.dto.friendship;

import java.util.UUID;

public record FriendDto(
        UUID friendshipId,
        UUID id,
        String displayName,
        String email,
        String avatarUrl,
        boolean hasUnreadMessages,
        long unreadMessagesCount
) {}