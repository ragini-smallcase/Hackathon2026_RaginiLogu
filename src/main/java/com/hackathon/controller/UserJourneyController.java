package com.hackathon.controller;

import com.hackathon.model.UserJourneyRequest;
import com.hackathon.model.UserJourneyResponse;
import com.hackathon.service.CkycFixService;
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

    @Value("${unity.api.base-url}")
    private String unityBaseUrl;

    @Value("${unity.api.gateway-secret}")
    private String gatewaySecret;

    @Value("${unity.api.auth-token}")
    private String authToken;

    @Value("${unity.api.partner-name:smallcase}")
    private String partnerName;

    public UserJourneyController(UserJourneyService userJourneyService, CkycFixService ckycFixService) {
        this.userJourneyService = userJourneyService;
        this.ckycFixService = ckycFixService;
    }

    @PostMapping("/proxy/unity/create-user")
    public ResponseEntity<Object> proxyUnityCreateUser(@RequestBody Map<String, Object> body) {
        String url = unityBaseUrl + "/backend/gatewaydemo-stag/v1/user";
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("x-gateway-secret", gatewaySecret);
        headers.set("x-gateway-authtoken", authToken);
        headers.set("X-Forwarded-For", "43.204.178.93");
        org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(body, headers);
        try {
            ResponseEntity<Object> response = restTemplate.postForEntity(url, entity, Object.class);
            String pan = (String) body.get("pan");
            if (pan != null) {
                ckycFixService.fixCkycUserId(pan);
            }
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    @PostMapping("/user-journey")
    public ResponseEntity<UserJourneyResponse> getUserAtJourneyStep(@Valid @RequestBody UserJourneyRequest request) {
        UserJourneyResponse response = userJourneyService.findOrCreateUser(request);
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
