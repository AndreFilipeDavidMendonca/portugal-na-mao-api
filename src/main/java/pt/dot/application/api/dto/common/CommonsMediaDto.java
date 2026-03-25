package pt.dot.application.api.dto.common;

public record CommonsMediaDto(
        String url,
        String title,
        Integer width,
        Integer height
) {}