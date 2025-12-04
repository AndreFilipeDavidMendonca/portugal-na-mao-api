// src/main/java/pt/dot/application/db/entity/District.java
package pt.dot.application.db.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "district")
public class District {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 10, unique = true)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "name_pt", length = 100)
    private String namePt;

    @Column
    private Integer population;

    @Column(name = "founded_year")
    private Integer foundedYear;

    @Column
    private Double lat;

    @Column
    private Double lon;

    @Column(columnDefinition = "text")
    private String description;

    // 🔥 NOVOS CAMPOS DE METADATA

    @Column(name = "inhabited_since", length = 255)
    private String inhabitedSince;

    @Column(name = "history", columnDefinition = "text")
    private String history;

    @Column(name = "municipalities_count")
    private Integer municipalitiesCount;

    @Column(name = "parishes_count")
    private Integer parishesCount;

    // 🔥 NOVO: ficheiros (imagens / vídeos) associados ao distrito
    @ElementCollection
    @CollectionTable(
            name = "district_files",
            joinColumns = @JoinColumn(name = "district_id")
    )
    @Column(name = "file_url", columnDefinition = "text")
    private List<String> files = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "district_sources",
            joinColumns = @JoinColumn(name = "district_id")
    )
    @Column(name = "source", columnDefinition = "text")
    private List<String> sources = new ArrayList<>();

    // ===== getters & setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNamePt() { return namePt; }
    public void setNamePt(String namePt) { this.namePt = namePt; }

    public Integer getPopulation() { return population; }
    public void setPopulation(Integer population) { this.population = population; }

    public Integer getFoundedYear() { return foundedYear; }
    public void setFoundedYear(Integer foundedYear) { this.foundedYear = foundedYear; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLon() { return lon; }
    public void setLon(Double lon) { this.lon = lon; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getInhabitedSince() { return inhabitedSince; }
    public void setInhabitedSince(String inhabitedSince) { this.inhabitedSince = inhabitedSince; }

    public String getHistory() { return history; }
    public void setHistory(String history) { this.history = history; }

    public Integer getMunicipalitiesCount() { return municipalitiesCount; }
    public void setMunicipalitiesCount(Integer municipalitiesCount) { this.municipalitiesCount = municipalitiesCount; }

    public Integer getParishesCount() { return parishesCount; }
    public void setParishesCount(Integer parishesCount) { this.parishesCount = parishesCount; }

    public List<String> getFiles() { return files; }
    public void setFiles(List<String> files) { this.files = files; }

    public List<String> getSources() { return files; }
    public void setSources(List<String> files) { this.files = files; }


}