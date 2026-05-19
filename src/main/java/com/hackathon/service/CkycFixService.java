package com.hackathon.service;

import com.hackathon.model.FlowUsers;
import com.hackathon.model.FlowUsersCKYC;
import com.hackathon.repository.FlowUsersCKYCRepository;
import com.hackathon.repository.FlowUsersRepository;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CkycFixService {

    private final FlowUsersRepository flowUsersRepository;
    private final FlowUsersCKYCRepository flowUsersCKYCRepository;
    private final MongoTemplate mongoTemplate;

    public CkycFixService(FlowUsersRepository flowUsersRepository,
                          FlowUsersCKYCRepository flowUsersCKYCRepository,
                          MongoTemplate mongoTemplate) {
        this.flowUsersRepository = flowUsersRepository;
        this.flowUsersCKYCRepository = flowUsersCKYCRepository;
        this.mongoTemplate = mongoTemplate;
    }

    private static final ObjectId FALLBACK_CKYC_ID = new ObjectId("6a018c62ff605260e7229128");

    /**
     * For the given PAN:
     * 1. Check if a flowUsersCKYC doc with ckycPodDetails.status = "success" exists.
     *    - If yes: replace its userId with _id from the flowUsers doc for gatewaydemo-stag.
     *    - If no: fall back to the doc with _id = FALLBACK_CKYC_ID and replace both
     *             its userId and pan with values from the flowUsers doc.
     */
    public String fixCkycUserId(String pan) {
        Optional<FlowUsers> flowUserOpt = flowUsersRepository.findByPanAndPartner(pan, "gatewaydemo-stag");
        if (!flowUserOpt.isPresent()) {
            return "No flowUsers doc found for PAN: " + pan + " with partner gatewaydemo-stag";
        }
        FlowUsers flowUser = flowUserOpt.get();

        Optional<FlowUsersCKYC> ckycDocOpt = flowUsersCKYCRepository.findByPanAndCkycStatus(pan, "success");

        if (ckycDocOpt.isPresent()) {
            Query query = new Query(Criteria.where("_id").is(ckycDocOpt.get().getId()));
            Update update = new Update().set("userId", flowUser.getId());
            mongoTemplate.updateFirst(query, update, FlowUsersCKYC.class);
            return "Updated flowUsersCKYC.userId to " + flowUser.getId().toHexString();
        } else {
            Query query = new Query(Criteria.where("_id").is(FALLBACK_CKYC_ID));
            Update update = new Update()
                    .set("userId", flowUser.getId())
                    .set("pan", pan);
            mongoTemplate.updateFirst(query, update, FlowUsersCKYC.class);
            return "Fallback: updated flowUsersCKYC " + FALLBACK_CKYC_ID.toHexString()
                    + " — userId=" + flowUser.getId().toHexString() + ", pan=" + pan;
        }
    }
}
