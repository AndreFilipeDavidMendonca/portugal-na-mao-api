package pt.dot.application.api.dto.chat;

public record SharePoiRequestDto(
        Long poiId,
        String body
) {}