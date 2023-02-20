package org.cvhau.investing.crawler;

public class Main {
    public static void main(String[] args) throws Exception {
        EventListCrawler eventListCrawler = new EventListCrawler();
        eventListCrawler.crawl();
    }
}
