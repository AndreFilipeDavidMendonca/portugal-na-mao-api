// src/main/java/pt/dot/application/api/dto/DistrictUpdateRequest.java
package pt.dot.application.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class DistrictUpdateRequest {
    private String name;
    private String namePt;

    private Integer population;
    private String description;
    private String history;
    private String inhabitedSince;

    private Integer municipalitiesCount;
    private Integer parishesCount;

    private List<String> files;
    private List<String> sources;  // opcional
}