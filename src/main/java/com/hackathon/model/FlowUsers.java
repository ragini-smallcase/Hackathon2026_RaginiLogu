package com.hackathon.model;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "flowUsers")
public class FlowUsers {

    @Id
    private ObjectId id;
    private String pan;
    private String partner;

    public ObjectId getId() { return id; }
    public String getPan() { return pan; }
    public String getPartner() { return partner; }
}
