package com.hackathon.service;

import com.hackathon.client.UnityApiClient;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class LinkBankAccountService {

    private static final Logger log = LoggerFactory.getLogger(LinkBankAccountService.class);
    private static final String COLLECTION = "flowUserCKYCs";
    private static final ObjectId FALLBACK_ID = new ObjectId("69fdd61cff60521fd55cfc82");

    private final UnityApiClient unityApiClient;
    private final MongoTemplate mongoTemplate;

    public LinkBankAccountService(UnityApiClient unityApiClient, MongoTemplate mongoTemplate) {
        this.unityApiClient = unityApiClient;
        this.mongoTemplate = mongoTemplate;
    }

    public String process(String pan, String unityUserId) {
        log.info("[LINK_BANK_ACCOUNT] process called: pan={}, unityUserId={}", pan, unityUserId);
        String flowId = unityApiClient.getFlowId(unityUserId);
        log.info("[LINK_BANK_ACCOUNT] getFlowId result: flowId={}", flowId);
        if (flowId == null) {
            log.warn("[LINK_BANK_ACCOUNT] No flowId found, falling back to unityUserId as userId");
            return updateDb(pan, unityUserId);
        }
        return updateDb(pan, flowId);
    }

    public String updateDb(String pan, String flowId) {
        ObjectId flowObjectId = new ObjectId(flowId);

        Query panQuery = new Query(Criteria.where("pan").is(pan));
        UpdateResult result = mongoTemplate.updateFirst(panQuery,
                new Update().set("userId", flowObjectId), Document.class, COLLECTION);

        if (result.getMatchedCount() > 0) {
            log.info("[LINK_BANK_ACCOUNT] Updated {} userId=ObjectId({}) for pan={}", COLLECTION, flowId, pan);
            updateFlowLoanJourneyStatus(flowObjectId);
            return "Updated " + COLLECTION + " userId=" + flowId + " for pan=" + pan;
        }

        Query fallbackQuery = new Query(Criteria.where("_id").is(FALLBACK_ID));
        mongoTemplate.updateFirst(fallbackQuery,
                new Update().set("userId", flowObjectId).set("pan", pan),
                Document.class, COLLECTION);
        log.info("[LINK_BANK_ACCOUNT] Fallback: updated {} {} userId=ObjectId({}), pan={}", COLLECTION, FALLBACK_ID.toHexString(), flowId, pan);
        updateFlowLoanJourneyStatus(flowObjectId);
        return "Fallback: updated " + COLLECTION + " " + FALLBACK_ID.toHexString()
                + " userId=" + flowId + ", pan=" + pan;
    }

    private void updateFlowLoanJourneyStatus(ObjectId flowObjectId) {
        try {
            Query loanQuery = new Query(Criteria.where("userId").is(flowObjectId));
            loanQuery.with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "_id"));
            loanQuery.limit(1);
            UpdateResult loanResult = mongoTemplate.updateFirst(loanQuery,
                    new Update().set("journeyStatus", "LINK_BANK_ACCOUNT"),
                    Document.class, "flowLoans");
            log.info("[LINK_BANK_ACCOUNT] flowLoans update: matched={}, modified={}", loanResult.getMatchedCount(), loanResult.getModifiedCount());
        } catch (Exception e) {
            log.error("[LINK_BANK_ACCOUNT] Failed to update flowLoans journeyStatus: {}", e.getMessage());
        }
    }
}
