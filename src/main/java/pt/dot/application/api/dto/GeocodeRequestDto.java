package pt.dot.application.api.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class GeocodeRequestDto {

    private String street;       // rua / avenida
    private String houseNumber;  // número / porta
    private String postalCode;   // 1000-001
    private String city;         // localidade
    private String district;     // distrito (opcional)
    private String country;      // default: Portugal

    public GeocodeRequestDto() {}

}