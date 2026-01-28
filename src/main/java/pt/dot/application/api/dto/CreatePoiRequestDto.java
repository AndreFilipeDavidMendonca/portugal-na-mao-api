// src/main/java/pt/dot/application/api/dto/CreatePoiRequestDto.java
package pt.dot.application.api.dto;

import java.util.List;

public class CreatePoiRequestDto {

    private String name;
    private String description;
    private String category;
    private Double lat;
    private Double lon;
    private String image;
    private List<String> images;

    public CreatePoiRequestDto() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }
}