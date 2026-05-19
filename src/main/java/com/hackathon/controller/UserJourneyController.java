package com.hackathon.controller;

import com.hackathon.model.UserJourneyRequest;
import com.hackathon.model.UserJourneyResponse;
import com.hackathon.service.CkycFixService;
import com.hackathon.service.FlowTokenService;
import com.hackathon.service.LinkBankAccountService;
import com.hackathon.service.UserJourneyService;
import javax.validation.Valid;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
    private final LinkBankAccountService linkBankAccountService;

    @Value("${unity.api.base-url}")
    private String unityBaseUrl;

    @Value("${unity.api.gateway-secret}")
    private String gatewaySecret;

    @Value("${unity.api.auth-token}")
    private String authToken;

    @Value("${unity.api.partner-name:smallcase}")
    private String partnerName;

    @Value("${smartinvesting.api.base-url:http://localhost:3000}")
    private String smartInvestingBaseUrl;

    public UserJourneyController(UserJourneyService userJourneyService, CkycFixService ckycFixService, FlowTokenService flowTokenService, LinkBankAccountService linkBankAccountService) {
        this.userJourneyService = userJourneyService;
        this.ckycFixService = ckycFixService;
        this.flowTokenService = flowTokenService;
        this.linkBankAccountService = linkBankAccountService;
    }

    @SuppressWarnings("unchecked")
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
        body.remove("journeyStatus");
        org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(body, headers);
        try {
            ResponseEntity<Object> response = restTemplate.postForEntity(url, entity, Object.class);

            // If user already exists, reset and recreate
            if (response.getBody() instanceof Map) {
                Map<?, ?> firstBody = (Map<?, ?>) response.getBody();
                if ("referrer_already_mapped".equals(firstBody.get("error"))) {
                    try {
                        Map<?, ?> testMeta = (Map<?, ?>) body.get("testMeta");
                        String lender = testMeta != null ? (String) testMeta.get("lender") : "bajaj_finserv";
                        Map<?, ?> firstData = (Map<?, ?>) firstBody.get("data");
                        String existingUserId = firstData != null ? (String) firstData.get("unityUserId") : null;
                        if (existingUserId != null) {
                            String lid = flowTokenService.getLidFromUnity(existingUserId, lender);
                            if (lid != null) {
                                flowTokenService.resetUser(lid, lender);
                                response = restTemplate.postForEntity(url, entity, Object.class);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[Reset] reset user failed: " + e.getMessage());
                    }
                }
            }

            try {
                String pan = (String) body.get("pan");
                System.out.println("[DEBUG] journeyStatus=" + journeyStatus + " pan=" + pan);
                if ("LINK_BANK_ACCOUNT".equals(journeyStatus)) {
                    String unityUserId = extractUnityUserId(response.getBody());
                    System.out.println("[DEBUG] unityUserId=" + unityUserId);
                    if (pan != null && unityUserId != null) {
                        String dbResult = linkBankAccountService.process(pan, unityUserId);
                        System.out.println("[DEBUG] DB result: " + dbResult);
                    } else {
                        System.out.println("[DEBUG] Skipped: pan=" + pan + " unityUserId=" + unityUserId);
                    }
                } else {
                    if (pan != null) ckycFixService.fixCkycUserId(pan);
                }
            } catch (Exception e) {
                System.err.println("[DEBUG] Exception in post-processing: " + e.getMessage());
                e.printStackTrace();
            }
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

            // Register user in SmartInvesting and attach opaqueId to response
            if (response.getBody() instanceof Map) {
                Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
                Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                String opaqueId = UUID.randomUUID().toString();
                if (data != null) data.put("opaqueId", opaqueId);
                try {
                    Map<String, Object> gwFePayload = new HashMap<>(body);
                    gwFePayload.put("id", opaqueId);
                    org.springframework.web.client.RestTemplate gwFeClient = new org.springframework.web.client.RestTemplate();
                    org.springframework.http.HttpHeaders gwFeHeaders = new org.springframework.http.HttpHeaders();
                    gwFeHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                    gwFeHeaders.set("x-gateway-unity-env", "primary");
                    gwFeClient.postForEntity(smartInvestingBaseUrl + "/las/user",
                            new org.springframework.http.HttpEntity<>(gwFePayload, gwFeHeaders), Object.class);
                } catch (Exception e) {
                    System.err.println("[SmartInvesting] register user failed: " + e.getMessage());
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
