package pt.dot.monumentosproxy.monumentos;

import lombok.Data;

@Data
public class MonumentSearchRequest {
    private String name;
    private String district;
    private String concelho;
    private String freguesia;
}
