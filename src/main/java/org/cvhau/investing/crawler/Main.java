package org.cvhau.investing.crawler;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;

public class Main {
    public static void main(String[] args) throws Exception {
        EventListCrawler eventListCrawler = new EventListCrawler();
        LocalDate date = LocalDate.of(2022, 1, 1);
        LocalDate endDate = LocalDate.now().plusMonths(6);
        while (date.isBefore(endDate)) {
            eventListCrawler.crawl(date);
            date = date.plusDays(1);
        }
    }
}
