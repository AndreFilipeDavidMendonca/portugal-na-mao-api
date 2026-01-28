package pt.dot.application.api.dto;

public class GeocodeResponseDto {

    private Double lat;
    private Double lon;
    private String displayName;
    private String provider;
    private Double confidence;

    public GeocodeResponseDto() {}

    public GeocodeResponseDto(Double lat,
                              Double lon,
                              String displayName,
                              String provider,
                              Double confidence) {
        this.lat = lat;
        this.lon = lon;
        this.displayName = displayName;
        this.provider = provider;
        this.confidence = confidence;
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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }
}