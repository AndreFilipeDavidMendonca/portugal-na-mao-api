// src/main/java/pt/dot/application/api/dto/PoiDto.java
package pt.dot.application.api.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Setter
@Getter
public class PoiDto {

    private Long id;
    private Long districtId;

    // ✅ novo
    private UUID ownerId;

    private String name;
    private String namePt;
    private String category;
    private String subcategory;
    private String description;
    private Double lat;
    private Double lon;

    private String wikipediaUrl;
    private String sipaId;
    private String externalOsmId;
    private String source;

    private String image;
    private List<String> images;

    public PoiDto() {}

    public PoiDto(
            Long id,
            Long districtId,
            UUID ownerId,
            String name,
            String namePt,
            String category,
            String subcategory,
            String description,
            Double lat,
            Double lon,
            String wikipediaUrl,
            String sipaId,
            String externalOsmId,
            String source,
            String image,
            List<String> images
    ) {
        this.id = id;
        this.districtId = districtId;
        this.ownerId = ownerId;
        this.name = name;
        this.namePt = namePt;
        this.category = category;
        this.subcategory = subcategory;
        this.description = description;
        this.lat = lat;
        this.lon = lon;
        this.wikipediaUrl = wikipediaUrl;
        this.sipaId = sipaId;
        this.externalOsmId = externalOsmId;
        this.source = source;
        this.image = image;
        this.images = images;
    }

}