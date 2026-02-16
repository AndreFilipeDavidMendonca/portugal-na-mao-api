// src/main/java/pt/dot/application/api/dto/PoiLiteResponseDto.java
package pt.dot.application.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class PoiLiteResponseDto {

    private final List<PoiLiteDto> pois;
    private final Map<String, Long> countsByCategory;
}