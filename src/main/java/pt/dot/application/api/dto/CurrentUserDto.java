package pt.dot.application.api.dto;

import lombok.Getter;

import java.util.UUID;

@Getter
public class CurrentUserDto {

    private final UUID id;
    private final String email;
    private final String displayName;
    private final String avatarUrl;
    private final String role;

    private final String firstName;
    private final String lastName;
    private final Integer age;
    private final String nationality;
    private final String phone;

    public CurrentUserDto(
            UUID id,
            String email,
            String displayName,
            String avatarUrl,
            String role,
            String firstName,
            String lastName,
            Integer age,
            String nationality,
            String phone
    ) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.role = role;
        this.firstName = firstName;
        this.lastName = lastName;
        this.age = age;
        this.nationality = nationality;
        this.phone = phone;
    }

}