package com.hackathon.repository;

import com.hackathon.model.FlowUsersCKYC;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface FlowUsersCKYCRepository extends MongoRepository<FlowUsersCKYC, ObjectId> {

    @Query("{ 'pan': ?0, 'ckycPodDetails.status': ?1 }")
    Optional<FlowUsersCKYC> findByPanAndCkycStatus(String pan, String status);
}
