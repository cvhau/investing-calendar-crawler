package org.cvhau.investing.crawler;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EventListCrawler {
    public static final String INVESTING_BASE_URL = "https://www.investing.com";
    public static final String DATA_BASE_URL = "https://www.investing.com/economic-calendar/Service/getCalendarFilteredData";

    private final EventDetailCrawler eventDetailCrawler;

    public EventListCrawler() {
        this(new EventDetailCrawler());
    }

    public EventListCrawler(EventDetailCrawler eventDetailCrawler) {
        this.eventDetailCrawler = eventDetailCrawler;
    }

    public void crawl() throws IOException, InterruptedException {
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
                .POST(HttpRequest.BodyPublishers.ofString(requestPayload()))
                .build();

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

        eventRowsHtml.forEach(e -> {
            String eventRowId = extractEventRowId(e);
            String eventAttrId = extractEventAttrId(e);
            String eventCountry = extractEventCountry(e);
            String eventName = extractEventName(e);
            boolean isHoliday = false;

            if (eventAttrId.isEmpty()) {
                isHoliday = checkEventIsHoliday(e);
            }

            String eventDetailUrl = extractEventDetailUrl(e);

            EventDetail eventDetail = null;

            if (!eventDetailUrl.isEmpty()) {
                try {
                    Thread.sleep(7000);
                    eventDetail = eventDetailCrawler.crawl(eventDetailUrl);
                } catch (Exception exception) {
                    exception.printStackTrace();
                }
            }

            String eventId = eventRowId;
            if (!eventAttrId.isEmpty()) {
                eventId += "_" + eventAttrId;
            }

            System.out.println("======================================");
            System.out.println(eventCountry);
            System.out.println(eventId);
            System.out.println(eventName);
            System.out.printf("Holiday: %s\n", isHoliday);
            System.out.println(eventDetailUrl);
            if (eventDetail != null) {
                System.out.printf("Detail Title: %s\n", eventDetail.getDetailTitle());
                System.out.printf("Description: %s\n", eventDetail.getDescription());
                System.out.printf("Source name: %s\n", eventDetail.getSource().getName());
                System.out.printf("Source URL: %s\n", eventDetail.getSource().getUrl());
            }
        });
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

        return "";
    }

    private String extractEventCountry(String eventRowHtml) {
        String regex = "<span[^>]* title=\"([^\"]+)\"[^>]*>";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(eventRowHtml);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return "";
    }

    private String extractEventName(String eventRowHtml) {
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

    private String extractEventDetailUrl(String eventRowHtml) {
        String regex = "<a.+?href=\"([^\"]+)\"";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(eventRowHtml);
        String eventDetailUrl = "";

        if (matcher.find()) {
            String eventDetailUri = matcher.group(1);
            eventDetailUrl = String.format("%s/%s", INVESTING_BASE_URL, eventDetailUri.replaceFirst("^/", ""));
        }

        return eventDetailUrl;
    }

    private String requestPayload() {
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
        formData.put("currentTab", "custom");
        formData.put("dateFrom", "2023-02-20");
        formData.put("dateTo", "2023-02-20");

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
}
