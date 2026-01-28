// src/main/java/pt/dot/application/api/dto/PoiDto.java
package pt.dot.application.api.dto;

import java.util.List;
import java.util.UUID;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDistrictId() { return districtId; }
    public void setDistrictId(Long districtId) { this.districtId = districtId; }

    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNamePt() { return namePt; }
    public void setNamePt(String namePt) { this.namePt = namePt; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSubcategory() { return subcategory; }
    public void setSubcategory(String subcategory) { this.subcategory = subcategory; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }

    public String getWikipediaUrl() { return wikipediaUrl; }
    public void setWikipediaUrl(String wikipediaUrl) { this.wikipediaUrl = wikipediaUrl; }

    public String getSipaId() { return sipaId; }
    public void setSipaId(String sipaId) { this.sipaId = sipaId; }

    public String getExternalOsmId() { return externalOsmId; }
    public void setExternalOsmId(String externalOsmId) { this.externalOsmId = externalOsmId; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }
}