// src/main/java/pt/dot/application/api/dto/PoiLiteDto.java
package pt.dot.application.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class PoiLiteDto {
    private Long id;
    private Long districtId;
    private UUID ownerId;
    private String name;
    private String namePt;
    private String category;
    private Double lat;
    private Double lon;
}