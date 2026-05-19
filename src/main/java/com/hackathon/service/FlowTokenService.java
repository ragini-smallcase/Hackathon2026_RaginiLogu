package com.hackathon.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class FlowTokenService {

    @Value("${unity.api.base-url}")
    private String unityBaseUrl;

    @Value("${unity.api.auth-token}")
    private String gatewayAuthToken;

    @Value("${unity.api.gateway-secret}")
    private String gatewaySecret;

    @Value("${unity.api.partner-name:gatewaydemo-stag}")
    private String partnerName;

    @Value("${flow.api.bfin-base-url:https://bfin-flow-stag.credit.smallcase.com}")
    private String bfinBaseUrl;

    @Value("${flow.api.holdings-summary-cookie}")
    private String holdingsSummaryCookie;

    private static final String WHITELISTED_IP = "43.204.178.93";

    private final RestTemplate restTemplate = new RestTemplate(
            new HttpComponentsClientHttpRequestFactory(HttpClientBuilder.create().build()));

    // Step 1: Disable auth on partner so OTP is not needed
    public void disableAuthOnPartner() {
        String url = unityBaseUrl + "/internal/v1/partner/" + partnerName;

        Map<String, Object> flags = new HashMap<>();
        flags.put("isActive", true);
        flags.put("isGuestAllowed", true);
        flags.put("authRequired", false);
        flags.put("isIPWhitelistingEnabled", true);

        Map<String, Object> body = new HashMap<>();
        body.put("flags", flags);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-gateway-secret", gatewaySecret);
        headers.set("x-gateway-authtoken", gatewayAuthToken);
        headers.set("X-Forwarded-For", WHITELISTED_IP);
        restTemplate.exchange(url, HttpMethod.PATCH, new HttpEntity<>(body, headers), Object.class);
    }

    // Step 2: Create interaction and return interactionId
    @SuppressWarnings("unchecked")
    public String createInteraction(String unityUserId, String lender) {
        String url = unityBaseUrl + "/backend/" + partnerName + "/v1/interaction";

        Map<String, Object> config = new HashMap<>();
        config.put("lender", "eimpl");
        config.put("userId", unityUserId);
        config.put("productType", "lamf");
        config.put("assetType", "MUTUALFUND");

        Map<String, Object> body = new HashMap<>();
        body.put("intent", "LOAN_APPLICATION");
        body.put("config", config);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-gateway-secret", gatewaySecret);
        headers.set("x-gateway-authtoken", gatewayAuthToken);
        headers.set("X-Forwarded-For", WHITELISTED_IP);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        return (String) data.get("interactionId");
    }

    // Step 3: Build JWT signed with gateway secret
    public String buildJwtToken(String interactionId) {
        Algorithm algorithm = Algorithm.HMAC256(gatewaySecret);
        return JWT.create()
                .withClaim("interactionId", interactionId)
                .withIssuer(partnerName)
                .withExpiresAt(new Date(1799981514L * 1000))
                .sign(algorithm);
    }

    // Step 4: Init the interaction session, returns the redirect URL containing the token
    @SuppressWarnings("unchecked")
    public String initInteraction(String jwtToken) {
        String url = unityBaseUrl + "/client/" + partnerName + "/v1/interaction/init";

        Map<String, Object> webviewData = new HashMap<>();
        webviewData.put("os", "ios");
        webviewData.put("osVersion", "18.1.0");
        webviewData.put("sdk", "react-native");
        webviewData.put("sdkVersion", "4.1.5");

        Map<String, Object> body = new HashMap<>();
        body.put("webviewExperimentData", webviewData);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-gateway-interaction", jwtToken);
        headers.set("x-gateway-sdk-version", "v0.0.1");
        headers.set("x-gateway-sdk-type", "ios");

        ResponseEntity<Map> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        return (String) data.get("url");
    }

    // Step 5: Extract the token param from the URL
    public String extractTokenFromUrl(String url) {
        String tokenParam = "token=";
        int start = url.indexOf(tokenParam) + tokenParam.length();
        int end = url.indexOf("\"", start);
        if (end == -1) end = url.indexOf("&", start);
        return end == -1 ? url.substring(start) : url.substring(start, end);
    }

    // Step 6: Exchange extracted token for the final flow authToken
    @SuppressWarnings("unchecked")
    public String exchangeTokenForFlowAuth(String extractedToken, String lender) {
        String url = bfinBaseUrl + "/" + lender + "/lamf/journey/exchange/token";

        Map<String, Object> opener = new HashMap<>();
        opener.put("type", "origin");
        opener.put("value", "https://stag.smartinvesting.io");

        Map<String, Object> body = new HashMap<>();
        body.put("token", extractedToken);
        body.put("opener", opener);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Cookie", "x-sc-las-auth=" + holdingsSummaryCookie);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        return (String) data.get("authToken");
    }

    // Full token generation flow
    public String generateFlowToken(String unityUserId, String lender) {
        disableAuthOnPartner();
        String interactionId = createInteraction(unityUserId, lender);
        String jwtToken      = buildJwtToken(interactionId);
        String interactionUrl = initInteraction(jwtToken);
        String extractedToken = extractTokenFromUrl(interactionUrl);
        return exchangeTokenForFlowAuth(extractedToken, lender);
    }

    // Move journey to FETCH_CKYC step
    public void moveToCkyc(String unityUserId, String lender) {
        String flowToken = generateFlowToken(unityUserId, lender);
        String url = bfinBaseUrl + "/" + lender + "/lamf/journey/moveStep/ckyc";

        HttpHeaders headers = new HttpHeaders();
        headers.set("x-sc-las-auth", flowToken);
        restTemplate.postForEntity(url, new HttpEntity<>(headers), Object.class);
    }
}
