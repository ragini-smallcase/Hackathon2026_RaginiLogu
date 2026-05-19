package com.hackathon.service;

import com.hackathon.client.UnityApiClient;
import com.hackathon.model.UserJourneyRequest;
import com.hackathon.model.UserJourneyResponse;
import org.springframework.stereotype.Service;

@Service
public class UserJourneyService {

    private final UnityApiClient unityApiClient;

    public UserJourneyService(UnityApiClient unityApiClient) {
        this.unityApiClient = unityApiClient;
    }

    public UserJourneyResponse findOrCreateUser(UserJourneyRequest request) {
        try {
            // Step 1: Create (or retrieve existing) Unity user
            String userId = unityApiClient.createOrGetUser(
                    request.getPan(),
                    request.getPhone(),
                    request.getLender(),
                    request.getDob()
            );

            if (userId == null || userId.isBlank()) {
                return UserJourneyResponse.error("Failed to create/retrieve Unity user");
            }

            // Step 2: Find the loan ID at the requested journey status
            String loanId = unityApiClient.getLoanIdAtStatus(userId, request.getLender(), request.getJourneyStatus());

            return UserJourneyResponse.ok(userId, loanId, request.getJourneyStatus(), request.getLender());

        } catch (Exception e) {
            return UserJourneyResponse.error("Error: " + e.getMessage());
        }
    }
}