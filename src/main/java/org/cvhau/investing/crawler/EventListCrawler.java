package org.cvhau.investing.crawler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EventListCrawler {
    public static final String INVESTING_BASE_URL = "https://www.investing.com";
    public static final String DATA_BASE_URL = "https://www.investing.com/economic-calendar/Service/getCalendarFilteredData";

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final EventDetailCrawler eventDetailCrawler;
    private final AtomicBoolean crawled;
    private final Set<String> crawledNames;

    public EventListCrawler() {
        this(new EventDetailCrawler());
    }

    public EventListCrawler(EventDetailCrawler eventDetailCrawler) {
        this.eventDetailCrawler = eventDetailCrawler;
        crawled = new AtomicBoolean(false);
        crawledNames = new TreeSet<>();
    }

    public void setToCrawled(Event event) {
        this.crawledNames.add(String.format("%s-%s-%s", event.getId(), event.getCountry(), event.getTitle()));
    }

    public boolean hasCrawled(Event event) {
        return this.crawledNames.contains(String.format("%s-%s-%s", event.getId(), event.getCountry(), event.getTitle()));
    }

    public void crawl(LocalDate date) throws IOException, InterruptedException {
        System.out.println();
        System.out.println("======================================");
        System.out.printf("Crawling data on date: %s\n", date.format(DATE_TIME_FORMATTER));

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DATA_BASE_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36")
                .POST(HttpRequest.BodyPublishers.ofString(requestPayload(date)))
                .build();

        if (crawled.get()) {
            Thread.sleep(8000);
        } else {
            crawled.set(true);
        }

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        //
        // Json decode response content
        //

        String bodyContent = response.body();

        ObjectMapper objectMapper = new ObjectMapper();

        InvestingCalendarJson json = objectMapper.readValue(bodyContent, InvestingCalendarJson.class);

        //
        // Parse events HTML data
        //

        String eventsHtmlData = json.getData();

        String regex = "<tr[^>]*>.*?</tr>";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(eventsHtmlData);

        List<String> eventRowsHtml = new ArrayList<>();

        while (matcher.find()) {
            String eventRowHtml = matcher.group();
            if (eventRowHtml.contains("eventRowId")) {
                eventRowsHtml.add(eventRowHtml);
            }
        }

        for(String e: eventRowsHtml) {
            Event event = new Event();

            event.setId(extractEventRowId(e));
            event.setAttrId(extractEventAttrId(e));
            event.setCountry(extractEventCountry(e));
            event.setImpact(extractEventImpact(e));
            event.setTitle(extractEventTitle(e));

            if (event.getImpact() < 3) {
                continue;
            }

            if (hasCrawled(event)) {
                continue;
            }

            if (event.getAttrId().isEmpty()) {
                event.setHoliday(checkEventIsHoliday(e));
            }

            Optional<String> eventDetailUrl = extractEventDetailUrl(e);
            eventDetailUrl.ifPresent(url -> {
                try {
                    Thread.sleep(8000);
                    event.setDetail(eventDetailCrawler.crawl(url));
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });

            System.out.println("======================================");
            System.out.printf("Source ID: %s\n", event.getSourceId());
            System.out.printf("Country: %s\n", event.getCountry().orElse(""));
            System.out.printf("Title: %s\n", event.getTitle());
            System.out.printf("Impact: %s\n", event.getImpact());
            System.out.printf("Holiday: %s\n", event.isHoliday());
            System.out.printf("Detail URL: %s\n", eventDetailUrl.orElse(""));
            event.getDetail().ifPresent(eventDetail -> {
                System.out.printf("Detail Title: %s\n", eventDetail.getDetailTitle().orElse(""));
                System.out.printf("Description: %s\n", eventDetail.getDescription().orElse(""));
                EventReporter eventReporter = eventDetail.getSource().orElse(new EventReporter("", ""));
                System.out.printf("Source name: %s\n", eventReporter.getName());
                System.out.printf("Source URL: %s\n", eventReporter.getUrl());
            });

            writeDataToCSV(event);

            setToCrawled(event);
        }
    }

    private String extractEventRowId(String eventRowHtml) {
        String regex = "<tr[^>]* id=\"eventRowId_([0-9]+)\"[^>]*>";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(eventRowHtml);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return "";
    }

    private String extractEventAttrId(String eventRowHtml) {
        String regex = "<tr[^>]* event_attr_ID=\"([0-9]+)\"[^>]*>";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(eventRowHtml);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private String extractEventCountry(String eventRowHtml) {
        String regex = "<span[^>]* title=\"([^\"]+)\"[^>]*>";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(eventRowHtml);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private int extractEventImpact(String eventRowHtml) {
        String regex = "(grayFullBullishIcon)";
        Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(eventRowHtml);
        int impact = 0;

        while (matcher.find()) {
            impact ++;
        }

        return impact;
    }

    private String extractEventTitle(String eventRowHtml) {
        String regex = "<td[^>]* class=\"[^\"]*event\"[^>]*>(.+?)</td>";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(eventRowHtml);
        String eventName = "";

        List<String> months = months();
        List<String> yearQuarters = yearQuarters();

        if (matcher.find()) {
            eventName = matcher.group(1);
            eventName = eventName.replaceAll("<[^>]*>", "");
            for (String month : months) {
                eventName = eventName.replace(String.format("(%s)", month), "");
            }
            for (String yearQuarter: yearQuarters) {
                eventName = eventName.replace(String.format("(%s)", yearQuarter), "");
            }
            eventName = eventName.replace("&nbsp;", "");
            eventName = eventName.trim();
        }

        return eventName;
    }

    private boolean checkEventIsHoliday(String eventRowHtml) {
        String regex = "holiday";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL|Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(eventRowHtml);
        return matcher.find();
    }

    private Optional<String> extractEventDetailUrl(String eventRowHtml) {
        String regex = "<a.+?href=\"([^\"]+)\"";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(eventRowHtml);
        String eventDetailUrl = null;

        if (matcher.find()) {
            String eventDetailUri = matcher.group(1);
            eventDetailUrl = String.format("%s/%s", INVESTING_BASE_URL, eventDetailUri.replaceFirst("^/", ""));
        }

        return Optional.ofNullable(eventDetailUrl);
    }

    private String requestPayload(LocalDate date) {
        List<Integer> countries = Arrays.asList(
                95,86,29,25,54,114,145,47,34,174,163,32,70,6,232,27,
                37,122,15,78,113,107,55,24,121,59,89,72,71,22,17,74,
                51,39,93,106,14,48,66,33,23,10,119,35,92,102,57,94,
                204,97,68,96,103,111,42,109,188,7,139,247,105,82,172,
                21,43,20,60,87,44,193,148,125,45,53,38,170,100,56,80,
                52,238,36,90,112,110,11,26,162,9,12,46,85,41,202,63,
                123,61,143,4,5,180,168,138,178,84,75
        );

        Map<String, Object> formData = new LinkedHashMap<>();

        formData.put("country", countries);
        formData.put("timeZone", 55);
        formData.put("timeFilter", "timeRemain");

//        formData.put("currentTab", "today");
        String formattedDate = date.format(DATE_TIME_FORMATTER);
        formData.put("currentTab", "custom");
        formData.put("dateFrom", formattedDate);
        formData.put("dateTo", formattedDate);

        formData.put("submitFilters", 1);
        formData.put("limit_from", 0);

        StringBuilder payload = new StringBuilder();

        formData.forEach((key, value) -> {
            if (value instanceof Collection<?>) {
                String fieldName = key + "[]";
                ((Collection<?>) value).forEach(v -> {
                    payload.append(String.format("%s=%s&", URLEncoder.encode(fieldName, Charset.defaultCharset()), v));
                });
            } else {
                payload.append(String.format("%s=%s&", URLEncoder.encode(key, Charset.defaultCharset()), value));
            }
        });

        payload.replace(payload.length() - 1, payload.length(), "");

        return payload.toString();
    }

    private List<String> months() {
        return List.of("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec");
    }

    private List<String> yearQuarters() {
        return List.of("Q1", "Q2", "Q3", "Q4");
    }

    private void writeDataToCSV(Event event) throws IOException {
        String filenameHighStars = "/home/cvhau/Documents/investing_events.csv";
        String filename = "/home/cvhau/Documents/investing_events_all.csv";
        String dataLine = buildCsvLine(event);

        writeDataToCSV(filename, dataLine);

        if (event.getImpact() > 2) {
            writeDataToCSV(filenameHighStars, dataLine);
        }
    }

    private @NonNull String buildCsvLine(@NonNull Event event) {
        StringBuilder line = new StringBuilder();
        line.append('"').append(event.getSourceId()).append('"').append(',');
        line.append('"').append(event.getImpact()).append('"').append(',');
        line.append('"').append(event.getCountry().orElse("")).append('"').append(',');
        line.append('"').append(event.getTitle()).append('"').append(',');

        EventDetail eventDetail = event.getDetail().orElse(new EventDetail("", "", null));
        line.append('"').append(eventDetail.getDetailTitle().orElse("")).append('"').append(',');
        line.append('"').append(eventDetail.getDescription().orElse("").replaceAll("\n", "<br/>").replace("\"", "\"\"")).append('"').append('\n');

        return line.toString();
    }

    private void writeDataToCSV(@NonNull String filename, @NonNull String dataLine) throws IOException {
        try(FileOutputStream fos = new FileOutputStream(filename, true)) {
            fos.write(dataLine.getBytes());
            fos.flush();
        }
    }
}
