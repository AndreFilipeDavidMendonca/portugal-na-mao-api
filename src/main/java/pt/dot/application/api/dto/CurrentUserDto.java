package pt.dot.application.api.dto;

import java.util.UUID;

public class CurrentUserDto {

    private UUID id;
    private String email;
    private String displayName;
    private String avatarUrl;
    private String role; // "ADMIN" | "USER"

    public CurrentUserDto() {
    }

    public CurrentUserDto(UUID id,
                          String email,
                          String displayName,
                          String avatarUrl,
                          String role) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.role = role;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}