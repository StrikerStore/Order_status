package com.shipway.ordertracking.dto;

/**
 * Body for POST fulfill-from-tracking (optional — defaults apply when omitted).
 */
public class BulkFulfillFromTrackingRequest {

    private String accountCode;
    private Integer limit = 500;
    private boolean dryRun;

    public String getAccountCode() {
        return accountCode;
    }

    public void setAccountCode(String accountCode) {
        this.accountCode = accountCode;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}
