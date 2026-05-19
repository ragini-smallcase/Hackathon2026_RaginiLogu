package com.hackathon.controller;

import com.hackathon.model.UserJourneyRequest;
import com.hackathon.model.UserJourneyResponse;
import com.hackathon.service.CkycFixService;
import com.hackathon.service.LinkBankAccountService;
import com.hackathon.service.UserJourneyService;
import javax.validation.Valid;
import java.util.Collections;
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
    private final LinkBankAccountService linkBankAccountService;

    @Value("${unity.api.base-url}")
    private String unityBaseUrl;

    @Value("${unity.api.gateway-secret}")
    private String gatewaySecret;

    @Value("${unity.api.auth-token}")
    private String authToken;

    public UserJourneyController(UserJourneyService userJourneyService,
                                 CkycFixService ckycFixService,
                                 LinkBankAccountService linkBankAccountService) {
        this.userJourneyService = userJourneyService;
        this.ckycFixService = ckycFixService;
        this.linkBankAccountService = linkBankAccountService;
    }

    @PostMapping("/proxy/unity/create-user")
    public ResponseEntity<Object> proxyUnityCreateUser(
            @RequestBody Map<String, Object> body,
            @RequestParam(required = false) String journeyStatus) {

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

            try {
                if ("LINK_BANK_ACCOUNT".equals(journeyStatus)) {
                    String unityUserId = extractUnityUserId(response.getBody());
                    if (pan != null && unityUserId != null) {
                        linkBankAccountService.process(pan, unityUserId);
                    }
                } else {
                    if (pan != null) ckycFixService.fixCkycUserId(pan);
                }
            } catch (Exception ignored) {}

            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }

    @PostMapping("/user-journey")
    public ResponseEntity<UserJourneyResponse> getUserAtJourneyStep(@Valid @RequestBody UserJourneyRequest request) {
        UserJourneyResponse response = userJourneyService.findOrCreateUser(request);
        if (response.isSuccess() && "CONFIRM_OFFER".equals(request.getJourneyStatus())) {
            try { ckycFixService.fixCkycUserId(request.getPan()); } catch (Exception ignored) {}
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

    @PostMapping("/debug/link-bank-account")
    public ResponseEntity<Map<String, String>> debugLinkBankAccount(@RequestParam String pan, @RequestParam String flowId) {
        String result = linkBankAccountService.updateDb(pan, flowId);
        return ResponseEntity.ok(Collections.singletonMap("result", result));
    }

    private String extractUnityUserId(Object responseBody) {
        if (responseBody instanceof Map) {
            Object data = ((Map<?, ?>) responseBody).get("data");
            if (data instanceof Map) {
                Object uid = ((Map<?, ?>) data).get("unityUserId");
                if (uid instanceof String) return (String) uid;
            }
        }
        return null;
    }
}
