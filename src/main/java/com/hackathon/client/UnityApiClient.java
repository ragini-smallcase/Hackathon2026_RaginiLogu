package com.hackathon.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
public class UnityApiClient {

    private final RestTemplate restTemplate;

    @Value("${unity.api.base-url}")
    private String baseUrl;

    @Value("${unity.api.gateway-secret}")
    private String gatewaySecret;

    @Value("${unity.api.auth-token}")
    private String authToken;

    @Value("${unity.api.partner-name:smallcase}")
    private String partnerName;

    public UnityApiClient() {
        this.restTemplate = new RestTemplate();
    }

    public String createOrGetUser(String pan, String phone, String lender, String dob) {
        String url = baseUrl + "/backend/" + partnerName + "/v1/user";

        HttpHeaders headers = buildHeaders();
        Map<String, Object> body = Map.of(
                "pan", pan,
                "dob", dob != null ? dob : "06-12-1996",
                "lender", lender,
                "authContact", Map.of(
                        "number", phone,
                        "countryCode", "+91"
                )
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
                if (data != null) {
                    return (String) data.get("unityUserId");
                }
            }
        } catch (Exception e) {
            // If user already exists, Unity may return 409 with existing userId
            throw new RuntimeException("Unity API call failed: " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public String getLoanIdAtStatus(String userId, String lender, String journeyStatus) {
        String url = baseUrl + "/backend/" + partnerName + "/v1/user/" + userId;

        HttpHeaders headers = buildHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
                if (data != null) {
                    List<Map<?, ?>> loans = (List<Map<?, ?>>) data.get("loans");
                    if (loans != null) {
                        for (Map<?, ?> loan : loans) {
                            Object loanStatus = loan.get("loanStatus");
                            if (loanStatus instanceof Map<?, ?> statusMap) {
                                String status = (String) statusMap.get("journeyStatus");
                                if (journeyStatus.equals(status)
                                        && lender.equals(loan.get("lender"))) {
                                    return (String) loan.get("lid");
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // loan lookup is best-effort
        }
        return null;
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-gateway-authtoken", authToken);
        headers.set("x-gateway-secret", gatewaySecret);
        return headers;
    }
}