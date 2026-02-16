// src/main/java/pt/dot/application/db/repo/PoiLiteView.java
package pt.dot.application.db.repo;

import java.util.UUID;

public interface PoiLiteView {

    Long getId();
    UUID getOwnerId();
    String getName();
    String getNamePt();
    String getCategory();
    Double getLat();
    Double getLon();
}