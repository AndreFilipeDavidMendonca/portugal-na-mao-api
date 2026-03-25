package pt.dot.application.api.dto.auth;

public record AuthResponseDto(String token, CurrentUserDto user) {}