package com.hackathon.controller;

import com.hackathon.model.UserJourneyRequest;
import com.hackathon.model.UserJourneyResponse;
import com.hackathon.service.UserJourneyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class UserJourneyController {

    private final UserJourneyService userJourneyService;

    public UserJourneyController(UserJourneyService userJourneyService) {
        this.userJourneyService = userJourneyService;
    }

    @PostMapping("/user-journey")
    public ResponseEntity<UserJourneyResponse> getUserAtJourneyStep(@Valid @RequestBody UserJourneyRequest request) {
        UserJourneyResponse response = userJourneyService.findOrCreateUser(request);
        return response.isSuccess()
                ? ResponseEntity.ok(response)
                : ResponseEntity.badRequest().body(response);
    }
}