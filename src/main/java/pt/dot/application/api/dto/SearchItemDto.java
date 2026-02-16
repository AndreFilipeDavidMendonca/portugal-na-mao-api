package pt.dot.application.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SearchItemDto {
    private String kind;        // "district" | "poi"
    private Long id;            // só para POI (null para district)
    private String name;        // label
    private Long districtId;    // opcional (POI). Pode vir null
}