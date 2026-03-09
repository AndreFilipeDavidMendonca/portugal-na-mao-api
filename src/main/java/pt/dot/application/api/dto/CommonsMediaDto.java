package pt.dot.application.api.dto;

public record CommonsMediaDto(
        String url,
        String title,
        Integer width,
        Integer height
) {}