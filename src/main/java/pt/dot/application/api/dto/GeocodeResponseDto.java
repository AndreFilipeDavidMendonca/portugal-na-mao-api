package pt.dot.application.api.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class GeocodeResponseDto {

    private Double lat;
    private Double lon;
    private String displayName;
    private String provider;
    private Double confidence;

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
}