package org.cvhau.investing.crawler;

import lombok.Data;

import java.util.Optional;

@Data
public class Event {
    private String id;
    private String attrId;
    private String country;
    private String title;
    private int impact;
    private boolean holiday;
    private EventDetail detail;

    public Optional<String> getAttrId() {
        return Optional.ofNullable(attrId);
    }

    public Optional<String> getCountry() {
        return Optional.ofNullable(country);
    }

    public Optional<EventDetail> getDetail() {
        return Optional.ofNullable(detail);
    }

    public String getSourceId() {
        StringBuilder sourceId = new StringBuilder(id);
        if (attrId != null && !attrId.isEmpty()) {
            sourceId.append('_').append(attrId);
        }
        return sourceId.toString();
    }
}
