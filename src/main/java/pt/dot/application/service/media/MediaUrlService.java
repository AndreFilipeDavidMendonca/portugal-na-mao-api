package pt.dot.application.service.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MediaUrlService {

    private final String baseUrl;

    public MediaUrlService(@Value("${ptdot.media.base-url}") String baseUrl) {
        this.baseUrl = stripTrailingSlash(baseUrl);
    }

    public String resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return null;

        if (storageKey.startsWith("http://") || storageKey.startsWith("https://")) {
            return storageKey;
        }

        return baseUrl + "/" + stripLeadingSlash(storageKey);
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String stripLeadingSlash(String value) {
        if (value == null || value.isBlank()) return "";
        return value.startsWith("/") ? value.substring(1) : value;
    }
}