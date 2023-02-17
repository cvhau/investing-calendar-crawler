package org.cvhau.investing.crawler;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.List;

@Data
public class InvestingCalendarJson {

    /**
     * Html content data
     */
    private String data;

    private List<String> pids;
    private String timeframe;
    private String dateFrom;
    private String dateTo;
    private JsonNode params;
    private int rows_num;
    private long last_time_scope;
    private boolean bind_scroll_handler;
    private String parseDataBy;
}
