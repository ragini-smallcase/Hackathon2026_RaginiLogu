package com.hackathon.service;

import com.hackathon.client.UnityApiClient;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class LinkBankAccountService {

    private static final String COLLECTION = "flowUserCKYCs";
    private static final ObjectId FALLBACK_ID = new ObjectId("69fdd61cff60521fd55cfc82");

    private final UnityApiClient unityApiClient;
    private final MongoTemplate mongoTemplate;

    public LinkBankAccountService(UnityApiClient unityApiClient, MongoTemplate mongoTemplate) {
        this.unityApiClient = unityApiClient;
        this.mongoTemplate = mongoTemplate;
    }

    public String process(String pan, String unityUserId) {
        String flowId = unityApiClient.getFlowId(unityUserId);
        if (flowId == null) {
            return "No flowId found for unityUserId: " + unityUserId;
        }
        return updateDb(pan, flowId);
    }

    public String updateDb(String pan, String flowId) {
        Query panQuery = new Query(Criteria.where("pan").is(pan));
        UpdateResult result = mongoTemplate.updateFirst(panQuery,
                new Update().set("userId", flowId), Document.class, COLLECTION);

        if (result.getMatchedCount() > 0) {
            return "Updated " + COLLECTION + " userId=" + flowId + " for pan=" + pan;
        }

        Query fallbackQuery = new Query(Criteria.where("_id").is(FALLBACK_ID));
        mongoTemplate.updateFirst(fallbackQuery,
                new Update().set("userId", flowId).set("pan", pan),
                Document.class, COLLECTION);
        return "Fallback: updated " + COLLECTION + " " + FALLBACK_ID.toHexString()
                + " userId=" + flowId + ", pan=" + pan;
    }
}
