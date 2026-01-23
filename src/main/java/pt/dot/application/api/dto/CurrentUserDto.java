package pt.dot.application.api.dto;

import java.util.UUID;

public class CurrentUserDto {

    private UUID id;
    private String email;
    private String displayName;
    private String avatarUrl;
    private String role;

    private String firstName;
    private String lastName;
    private Integer age;
    private String nationality;
    private String phone;

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

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getRole() { return role; }

    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public Integer getAge() { return age; }
    public String getNationality() { return nationality; }
    public String getPhone() { return phone; }
}