// src/main/java/pt/dot/application/db/entity/Poi.java
package pt.dot.application.db.entity;

import jakarta.persistence.*;
import pt.dot.application.db.converter.StringListConverter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "poi")
public class Poi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ✅ DONO (para POIs comerciais)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id", nullable = true)
    private District district;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "name_pt", length = 255)
    private String namePt;

    @Column(nullable = false, length = 100)
    private String category;

    @Column(length = 100)
    private String subcategory;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "wikipedia_url", columnDefinition = "text")
    private String wikipediaUrl;

    @Column(name = "sipa_id", length = 100)
    private String sipaId;

    @Column(name = "external_osm_id", length = 100)
    private String externalOsmId;

    private Double lat;
    private Double lon;

    @Column(length = 50)
    private String source;

    @Column(columnDefinition = "text")
    private String architect;

    @Column(name = "year_text", length = 100)
    private String yearText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "image", columnDefinition = "text")
    private String image;

    @Column(name = "images", columnDefinition = "text")
    @Convert(converter = StringListConverter.class)
    private List<String> images = new ArrayList<>();

    @PreUpdate
    public void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }

    // ---------- getters/setters ----------

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AppUser getOwner() { return owner; }
    public void setOwner(AppUser owner) { this.owner = owner; }

    public District getDistrict() { return district; }
    public void setDistrict(District district) { this.district = district; }

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

    public String getWikipediaUrl() { return wikipediaUrl; }
    public void setWikipediaUrl(String wikipediaUrl) { this.wikipediaUrl = wikipediaUrl; }

    public String getSipaId() { return sipaId; }
    public void setSipaId(String sipaId) { this.sipaId = sipaId; }

    public String getExternalOsmId() { return externalOsmId; }
    public void setExternalOsmId(String externalOsmId) { this.externalOsmId = externalOsmId; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getArchitect() { return architect; }
    public void setArchitect(String architect) { this.architect = architect; }

    public String getYearText() { return yearText; }
    public void setYearText(String yearText) { this.yearText = yearText; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) {
        this.images = images != null ? images : new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Poi)) return false;
        Poi poi = (Poi) o;
        return Objects.equals(id, poi.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}