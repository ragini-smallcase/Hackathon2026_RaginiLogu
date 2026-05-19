package com.hackathon.model;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class UserJourneyRequest {

    @NotBlank(message = "PAN is required")
    @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]{1}", message = "Invalid PAN format")
    private String pan;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "\\d{10}", message = "Phone must be 10 digits")
    private String phone;

    @NotBlank(message = "Lender is required")
    private String lender;

    @NotBlank(message = "Journey status is required")
    private String journeyStatus;

    private String dob;

    public String getPan() { return pan; }
    public void setPan(String pan) { this.pan = pan; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getLender() { return lender; }
    public void setLender(String lender) { this.lender = lender; }

    public String getJourneyStatus() { return journeyStatus; }
    public void setJourneyStatus(String journeyStatus) { this.journeyStatus = journeyStatus; }

    public String getDob() { return dob; }
    public void setDob(String dob) { this.dob = dob; }
}