package pt.dot.application.api.dto.auth;

import lombok.Getter;

@Getter
public class UpdateCurrentUserRequestDto {

    private final String displayName;
    private final String avatarUrl;

    private final String firstName;
    private final String lastName;
    private final Integer age;
    private final String nationality;
    private final String phone;

    public UpdateCurrentUserRequestDto(
            String displayName,
            String avatarUrl,
            String firstName,
            String lastName,
            Integer age,
            String nationality,
            String phone
    ) {
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
        this.firstName = firstName;
        this.lastName = lastName;
        this.age = age;
        this.nationality = nationality;
        this.phone = phone;
    }
}