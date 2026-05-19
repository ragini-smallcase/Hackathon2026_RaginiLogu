package com.hackathon.model;

public class UserJourneyResponse {

    private boolean success;
    private String userId;
    private String loanId;
    private String journeyStatus;
    private String lender;
    private String message;

    public UserJourneyResponse() {}

    public static UserJourneyResponse ok(String userId, String loanId, String journeyStatus, String lender) {
        UserJourneyResponse r = new UserJourneyResponse();
        r.success = true;
        r.userId = userId;
        r.loanId = loanId;
        r.journeyStatus = journeyStatus;
        r.lender = lender;
        return r;
    }

    public static UserJourneyResponse error(String message) {
        UserJourneyResponse r = new UserJourneyResponse();
        r.success = false;
        r.message = message;
        return r;
    }

    public boolean isSuccess() { return success; }
    public String getUserId() { return userId; }
    public String getLoanId() { return loanId; }
    public String getJourneyStatus() { return journeyStatus; }
    public String getLender() { return lender; }
    public String getMessage() { return message; }
}