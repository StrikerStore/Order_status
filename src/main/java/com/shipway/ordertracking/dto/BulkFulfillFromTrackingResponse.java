package com.shipway.ordertracking.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BulkFulfillFromTrackingResponse {

    private boolean dryRun;
    private List<FulfillAttemptResult> results = new ArrayList<>();
    private Map<String, Integer> summary = new HashMap<>();

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public List<FulfillAttemptResult> getResults() {
        return results;
    }

    public void setResults(List<FulfillAttemptResult> results) {
        this.results = results;
    }

    public Map<String, Integer> getSummary() {
        return summary;
    }

    public void setSummary(Map<String, Integer> summary) {
        this.summary = summary;
    }
}
