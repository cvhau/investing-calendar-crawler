package org.cvhau.investing.crawler;

import java.lang.reflect.Array;
import java.net.URLEncoder;
import java.nio.charset.Charset;

public class Main {
    public static void main(String[] args) throws Exception {
        EventCrawler eventCrawler = new EventCrawler();
        eventCrawler.crawl();
    }
}
