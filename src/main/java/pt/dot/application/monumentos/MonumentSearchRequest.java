package pt.dot.application.monumentos;

import lombok.Data;

@Data
public class MonumentSearchRequest {
    private String name;
    private String district;
    private String concelho;
    private String freguesia;
}
