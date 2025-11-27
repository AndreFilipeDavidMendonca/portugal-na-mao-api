package pt.dot.application.api.dto;

import java.util.List;

public class DistrictDetailDto {

    private DistrictDto district;
    private List<PoiDto> pois;

    public DistrictDetailDto() {}

    public DistrictDetailDto(DistrictDto district, List<PoiDto> pois) {
        this.district = district;
        this.pois = pois;
    }

    public DistrictDto getDistrict() {
        return district;
    }

    public void setDistrict(DistrictDto district) {
        this.district = district;
    }

    public List<PoiDto> getPois() {
        return pois;
    }

    public void setPois(List<PoiDto> pois) {
        this.pois = pois;
    }
}