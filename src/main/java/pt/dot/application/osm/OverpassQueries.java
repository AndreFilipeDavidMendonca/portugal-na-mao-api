// src/main/java/pt/dot/application/osm/OverpassQueries.java
package pt.dot.application.osm;

public class OverpassQueries {

    /**
     * poly = "minLat,minLon,maxLat,maxLon"
     * Ex: (bounding box de um distrito).
     */
    public static String buildCulturalPointsQuery(String bbox) {
        // no Overpass bbox é (sul, oeste, norte, este) = (minLat, minLon, maxLat, maxLon)
        return """
                [out:json][timeout:40];
                (
                  /* Palácios */
                  nwr[historic=palace](%s);
                  nwr[building=palace](%s);
                  nwr[castle_type=palace](%s);

                  /* Castelos e subtipos */
                  nwr[historic=castle](%s);
                  nwr[building=castle](%s);
                  nwr[castle_type~"^(castle|fortress)$"]( %s);

                  /* Ruínas (genéricas) + de castelo */
                  nwr[historic=ruins](%s);
                  nwr[ruins=castle](%s);

                  /* Monumentos */
                  nwr[historic=monument](%s);
                );
                out center;
                """.formatted(
                bbox, bbox, bbox,
                bbox, bbox, bbox,
                bbox, bbox,
                bbox
        );
    }

    public static String buildChurchPointsQuery(String bbox) {
        return """
                [out:json][timeout:40];
                (
                  nwr[building=church](%s);
                  nwr[building=cathedral](%s);
                  nwr[building=chapel](%s);

                  nwr[historic=church](%s);
                  nwr[historic=chapel](%s);

                  nwr[amenity=place_of_worship](%s);
                );
                out center;
                """.formatted(bbox, bbox, bbox, bbox, bbox, bbox);
    }

    public static String buildNaturePointsQuery(String bbox) {
        return """
                [out:json][timeout:40];
                (
                  nwr[tourism=viewpoint](%s);

                  nwr[leisure=park](%s);
                  nwr[leisure=garden](%s);
                  nwr[leisure=recreation_ground](%s);
                );
                out center;
                """.formatted(bbox, bbox, bbox, bbox);
    }
}