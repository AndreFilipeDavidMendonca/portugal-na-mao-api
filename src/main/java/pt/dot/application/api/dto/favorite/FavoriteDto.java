package pt.dot.application.api.dto.favorite;

import java.time.Instant;

public record FavoriteDto(
        Long poiId,
        String name,
        String image,
        Instant createdAt
) {}