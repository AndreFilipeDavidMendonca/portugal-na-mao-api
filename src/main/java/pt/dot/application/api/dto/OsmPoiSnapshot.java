package pt.dot.application.api.dto;

public class OsmPoiSnapshot {

    private Long districtId;   // obrigatório para gravar o POI
    private String osmId;      // ex: "node/12345" ou "way/67890"
    private String name;
    private String category;   // "castle", "palace", "church", "viewpoint", "park", "monument", "ruins", etc.
    private double lat;
    private double lon;

    public OsmPoiSnapshot() {
    }

    public OsmPoiSnapshot(Long districtId,
                          String osmId,
                          String name,
                          String category,
                          double lat,
                          double lon) {
        this.districtId = districtId;
        this.osmId = osmId;
        this.name = name;
        this.category = category;
        this.lat = lat;
        this.lon = lon;
    }

    public Long getDistrictId() {
        return districtId;
    }

    public void setDistrictId(Long districtId) {
        this.districtId = districtId;
    }

    public String getOsmId() {
        return osmId;
    }

    public void setOsmId(String osmId) {
        this.osmId = osmId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    @Override
    public String toString() {
        return "OsmPoiSnapshot{" +
                "districtId=" + districtId +
                ", osmId='" + osmId + '\'' +
                ", name='" + name + '\'' +
                ", category='" + category + '\'' +
                ", lat=" + lat +
                ", lon=" + lon +
                '}';
    }
}