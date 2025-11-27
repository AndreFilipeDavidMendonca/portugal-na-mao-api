// src/main/java/pt/dot/application/api/dto/DistrictDto.java
package pt.dot.application.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DistrictDto {

    private Long id;
    private String code;
    private String name;
    private String namePt;
    private Integer population;
    private Integer foundedYear;
    private Double lat;
    private Double lon;
    private String description;

    // Novos campos
    private String inhabitedSince;
    private String history;
    private Integer municipalitiesCount;
    private Integer parishesCount;
}