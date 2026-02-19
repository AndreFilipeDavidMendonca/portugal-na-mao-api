// src/main/java/pt/dot/application/db/entity/Poi.java
package pt.dot.application.db.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import pt.dot.application.db.converter.StringListConverter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Entity
@Table(name = "poi")
public class Poi {

    @Setter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    private District district;

    @Setter
    @Column(nullable = false)
    private String name;

    @Setter
    @Column(name = "name_pt")
    private String namePt;

    @Setter
    @Column(nullable = false)
    private String category;

    @Setter
    @Column(length = 100)
    private String subcategory;

    @Setter
    @Column(columnDefinition = "text")
    private String description;

    @Setter
    @Column(name = "wikipedia_url", columnDefinition = "text")
    private String wikipediaUrl;

    @Setter
    @Column(name = "sipa_id", length = 100)
    private String sipaId;

    @Column(name = "external_osm_id", length = 100)
    private String externalOsmId;

    @Setter
    private Double lat;
    @Setter
    private Double lon;

    @Setter
    @Column(length = 50)
    private String source;

    @Setter
    @Column(columnDefinition = "text")
    private String architect;

    @Setter
    @Column(name = "year_text", length = 100)
    private String yearText;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(
            mappedBy = "poi",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("position ASC")
    private List<PoiImage> images = new ArrayList<>();

    @PreUpdate
    public void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }

    public void addImage(PoiImage img) {
        img.setPoi(this);
        this.images.add(img);
    }

    public void clearImages() {
        this.images.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Poi poi)) return false;
        return Objects.equals(id, poi.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}