// src/main/java/pt/dot/application/api/dto/PoiLiteDto.java
package pt.dot.application.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class PoiLiteDto {

    private final Long id;

    // opcional para futuro
    private final Long districtId;

    private final UUID ownerId;
    private final String name;
    private final String namePt;
    private final String category;
    private final Double lat;
    private final Double lon;
}