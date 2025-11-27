// src/main/java/pt/dot/application/service/CreatePoiFromOsmRequest.java
package pt.dot.application.service;

public class CreatePoiFromOsmRequest {

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
    private String osmId;   // ID original OSM
    private String source;  // "osm", "manual", "sipa", "mix"

    public CreatePoiFromOsmRequest() {
    }

    // getters & setters

    public Long getDistrictId() {
        return districtId;
    }

    public void setDistrictId(Long districtId) {
        this.districtId = districtId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNamePt() {
        return namePt;
    }

    public void setNamePt(String namePt) {
        this.namePt = namePt;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubcategory() {
        return subcategory;
    }

    public void setSubcategory(String subcategory) {
        this.subcategory = subcategory;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon;
    }

    public String getWikipediaUrl() {
        return wikipediaUrl;
    }

    public void setWikipediaUrl(String wikipediaUrl) {
        this.wikipediaUrl = wikipediaUrl;
    }

    public String getSipaId() {
        return sipaId;
    }

    public void setSipaId(String sipaId) {
        this.sipaId = sipaId;
    }

    public String getOsmId() {
        return osmId;
    }

    public void setOsmId(String osmId) {
        this.osmId = osmId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}