// src/main/java/pt/dot/application/api/dto/CreatePoiRequestDto.java
package pt.dot.application.api.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class CreatePoiRequestDto {

    private String name;
    private String description;
    private String category;
    private Double lat;
    private Double lon;
    private String image;
    private List<String> images;

    public CreatePoiRequestDto() {}

}