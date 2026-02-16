// src/main/java/pt/dot/application/db/repo/proj/PoiLiteView.java
package pt.dot.application.db.repo;

import java.util.UUID;

public interface PoiLiteView {
    Long getId();
    Long getDistrictId();
    UUID getOwnerId();
    String getName();
    String getNamePt();
    String getCategory();
    Double getLat();
    Double getLon();
}