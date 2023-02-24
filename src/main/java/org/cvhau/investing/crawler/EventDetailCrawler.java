package org.cvhau.investing.crawler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EventDetailCrawler {
    public EventDetail crawl(String eventDetailUrl) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(eventDetailUrl))
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String bodyContent = response.body();
//        System.out.println(bodyContent);

        EventDetail eventDetail = new EventDetail();

        extractDetailTitle(bodyContent).ifPresent(eventDetail::setDetailTitle);
        extractDescription(bodyContent).ifPresent(eventDetail::setDescription);
        extractSource(bodyContent).ifPresent(eventDetail::setSource);

        return eventDetail;
    }

    private Optional<String> extractDetailTitle(String eventDetailHtml) {
        String regex = "<h1.*?>(.*?)</h1>";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(eventDetailHtml);
        String detailTitle = null;

        if (matcher.find()) {
            detailTitle = matcher.group(1);
            detailTitle = detailTitle.replaceAll("<[^>]*>", "");
            detailTitle = detailTitle.strip();
        }

        return Optional.ofNullable(detailTitle);
    }

    private Optional<String> extractDescription(String eventDetailHtml) {
        String regex = "<div +id=\"overViewBox\"[^>]*?>\\n?<div +class=\"left\"[^>]*?>(.*?)</div>";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(eventDetailHtml);
        String description = null;

        if (matcher.find()) {
            description = matcher.group(1);
            description = description.replaceAll("<br\\s?/?>\\s*", "\n");
            description = description.replaceAll("<[^>]*>", "");
            description = description.replace("&#039;", "'");
            description = description.strip();
        }

        return Optional.ofNullable(description);
    }

    private Optional<EventReporter> extractSource(String eventDetailHtml) {
        String regex = "Source:.*?<a.*?href=\"([^\"]+)\".+?title=\"([^\"]+)\"";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(eventDetailHtml);
        EventReporter source = null;

        if (matcher.find()) {
            source = new EventReporter();
            source.setUrl(matcher.group(1));
            source.setName(matcher.group(2).strip());
        }

        return Optional.ofNullable(source);
    }
}
