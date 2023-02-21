package org.cvhau.investing.crawler;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Optional;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventDetail {
    private String detailTitle;
    private String description;
    private EventSource source;

    public Optional<String> getDetailTitle() {
        return Optional.ofNullable(detailTitle);
    }

    public Optional<String> getDescription() {
        return Optional.ofNullable(description);
    }

    public Optional<EventSource> getSource() {
        return Optional.ofNullable(source);
    }
}
