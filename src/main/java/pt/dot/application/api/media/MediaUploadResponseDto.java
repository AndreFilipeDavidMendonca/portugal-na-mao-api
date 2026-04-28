package pt.dot.application.api.media;

public record MediaUploadResponseDto(
        String storageKey,
        String key,
        String url,
        String contentType,
        long sizeBytes
) {}
