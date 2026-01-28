package pt.dot.application.api.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class RegisterRequestDto {

    private String firstName;
    private String lastName;
    private Integer age;
    private String nationality;
    private String email;
    private String phone;
    private String password;

    private String role;

    public RegisterRequestDto() {}

}