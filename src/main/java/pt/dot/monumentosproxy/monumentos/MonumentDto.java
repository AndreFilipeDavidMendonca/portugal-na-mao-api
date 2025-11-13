package pt.dot.monumentosproxy.monumentos;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

// MonumentDto.java
@Data
@Builder
public class MonumentDto {

    private String id;
    private String slug;

    private String originalName;
    private String normalizedName;

    private String locality;
    private String district;
    private String concelho;
    private String freguesia;

    private Double lat;
    private Double lon;

    private String shortDescription;
    private String fullDescriptionHtml;

    private String heritageCategory;
    private String propertyType;
    private String protectionStatus;

    private List<String> imageUrls;

    private String sourceUrl;

    // 🔥 NOVO: atributos “livres” vindos das tabelas do SIPA
    private Map<String, String> extraAttributes;
}