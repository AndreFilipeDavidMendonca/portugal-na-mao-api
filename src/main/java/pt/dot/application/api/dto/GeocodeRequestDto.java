package pt.dot.application.api.dto;

public class GeocodeRequestDto {

    private String street;       // rua / avenida
    private String houseNumber;  // número / porta
    private String postalCode;   // 1000-001
    private String city;         // localidade
    private String district;     // distrito (opcional)
    private String country;      // default: Portugal

    public GeocodeRequestDto() {}

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }

    public String getHouseNumber() { return houseNumber; }
    public void setHouseNumber(String houseNumber) { this.houseNumber = houseNumber; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
}