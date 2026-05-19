package com.hackathon.controller;

import com.hackathon.model.UserJourneyRequest;
import com.hackathon.model.UserJourneyResponse;
import com.hackathon.service.CkycFixService;
import com.hackathon.service.FlowTokenService;
import com.hackathon.service.UserJourneyService;
import javax.validation.Valid;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class UserJourneyController {

    private final UserJourneyService userJourneyService;
    private final CkycFixService ckycFixService;
    private final FlowTokenService flowTokenService;

    @Value("${unity.api.base-url}")
    private String unityBaseUrl;

    @Value("${unity.api.gateway-secret}")
    private String gatewaySecret;

    @Value("${unity.api.auth-token}")
    private String authToken;

    @Value("${unity.api.partner-name:smallcase}")
    private String partnerName;

    public UserJourneyController(UserJourneyService userJourneyService, CkycFixService ckycFixService, FlowTokenService flowTokenService) {
        this.userJourneyService = userJourneyService;
        this.ckycFixService = ckycFixService;
        this.flowTokenService = flowTokenService;
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/proxy/unity/create-user")
    public ResponseEntity<Object> proxyUnityCreateUser(@RequestBody Map<String, Object> body) {
        String url = unityBaseUrl + "/backend/gatewaydemo-stag/v1/user";
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("x-gateway-secret", gatewaySecret);
        headers.set("x-gateway-authtoken", authToken);
        headers.set("X-Forwarded-For", "43.204.178.93");
        String journeyStatus = (String) body.remove("journeyStatus");
        org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(body, headers);
        try {
            ResponseEntity<Object> response = restTemplate.postForEntity(url, entity, Object.class);
            try {
                String pan = (String) body.get("pan");
                if (pan != null) ckycFixService.fixCkycUserId(pan);
            } catch (Exception ignored) {}
            if ("FETCH_CKYC".equals(journeyStatus) && response.getBody() instanceof Map) {
                try {
                    Map<?, ?> responseBody = (Map<?, ?>) response.getBody();
                    Map<?, ?> data = (Map<?, ?>) responseBody.get("data");
                    String unityUserId = (String) data.get("unityUserId");
                    Map<?, ?> testMeta = (Map<?, ?>) body.get("testMeta");
                    String lender = testMeta != null ? (String) testMeta.get("lender") : "bajaj_finserv";
                    if (unityUserId != null) {
                        flowTokenService.moveToCkyc(unityUserId, lender);
                    }
                } catch (Exception e) {
                    System.err.println("[FETCH_CKYC] moveToCkyc failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    if (e.getCause() != null) System.err.println("[FETCH_CKYC] caused by: " + e.getCause().getMessage());
                }
            }

            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            String errorBody = e.getResponseBodyAsString();
            if (errorBody == null || errorBody.trim().isEmpty()) {
                errorBody = "{\"message\":\"Unity API error: " + e.getStatusCode() + " " + e.getStatusText() + "\"}";
            }
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(errorBody);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body("{\"message\":\"Internal error: " + e.getMessage() + "\"}");
        }
    }

    @PostMapping("/user-journey")
    public ResponseEntity<UserJourneyResponse> getUserAtJourneyStep(@Valid @RequestBody UserJourneyRequest request) {
        UserJourneyResponse response = userJourneyService.findOrCreateUser(request);
        if (response.isSuccess() && "CONFIRM_OFFER".equals(request.getJourneyStatus())) {
            ckycFixService.fixCkycUserId(request.getPan());
        }
        return response.isSuccess()
                ? ResponseEntity.ok(response)
                : ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/fix-ckyc-user-id")
    public ResponseEntity<Map<String, String>> fixCkycUserId(@RequestParam String pan) {
        String result = ckycFixService.fixCkycUserId(pan);
        return ResponseEntity.ok(Collections.singletonMap("result", result));
    }
}
