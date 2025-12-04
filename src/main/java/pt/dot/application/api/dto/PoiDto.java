// src/main/java/pt/dot/application/api/dto/PoiDto.java
package pt.dot.application.api.dto;

import java.util.List;

public class PoiDto {

    private Long id;
    private Long districtId;
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

    // fotos
    private String image;          // imagem principal
    private List<String> images;   // galeria


    public PoiDto(Long id,
                  Long districtId,
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
                  List<String> images) {

        this.id = id;
        this.districtId = districtId;
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

    // GETTERS & SETTERS

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDistrictId() { return districtId; }
    public void setDistrictId(Long districtId) { this.districtId = districtId; }

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

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }
}