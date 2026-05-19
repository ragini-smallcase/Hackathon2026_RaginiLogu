package com.hackathon.model;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "flowUsersCKYC")
public class FlowUsersCKYC {

    @Id
    private ObjectId id;
    private String pan;
    private CkycPodDetails ckycPodDetails;
    private Object userId;

    public ObjectId getId() { return id; }
    public String getPan() { return pan; }
    public CkycPodDetails getCkycPodDetails() { return ckycPodDetails; }
    public Object getUserId() { return userId; }

    public static class CkycPodDetails {
        private String status;
        public String getStatus() { return status; }
    }
}
