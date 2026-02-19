// src/main/java/pt/dot/application/db/entity/PoiImage.java
package pt.dot.application.db.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "poi_image")
public class PoiImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "poi_id", nullable = false)
    private Poi poi;

    @Column(nullable = false)
    private Integer position;

    @Lob
    @Column(nullable = false, columnDefinition = "text")
    private String data; // base64
}