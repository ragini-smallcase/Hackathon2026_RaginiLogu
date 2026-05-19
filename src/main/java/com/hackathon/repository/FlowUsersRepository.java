package com.hackathon.repository;

import com.hackathon.model.FlowUsers;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface FlowUsersRepository extends MongoRepository<FlowUsers, ObjectId> {

    Optional<FlowUsers> findByPanAndPartner(String pan, String partner);
}
