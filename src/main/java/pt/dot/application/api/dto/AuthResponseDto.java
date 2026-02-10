package pt.dot.application.api.dto;

public record AuthResponseDto(String token, CurrentUserDto user) {}