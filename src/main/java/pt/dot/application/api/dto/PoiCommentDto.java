package pt.dot.application.api.dto;

public record PoiCommentDto(
        long id,
        long poiId,
        String authorName,
        String body,
        String createdAt,
        String updatedAt,
        boolean canDelete
) {}