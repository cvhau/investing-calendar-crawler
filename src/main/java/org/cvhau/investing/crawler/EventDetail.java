package org.cvhau.investing.crawler;

import lombok.Data;

@Data
public class EventDetail {
    private String detailTitle;
    private String description;
    private EventSource source;
}
