#!/usr/bin/env python3
import sys
from pymongo import MongoClient
from bson import ObjectId

FALLBACK_ID = ObjectId("69fdd61cff60521fd55cfc82")
DB_NAME = "las-statging-1"
COLLECTION = "flowUserCKYCs"
URI = (
    "mongodb://logu-sanjeev:HKD62JqIUj@"
    "las-stag-cluster.cluster-cza6pp7puf7n.ap-south-1.docdb.amazonaws.com:28100/"
    "?ssl_ca_certs=rds-combined-ca-bundle.pem&readPreference=primaryPreferred"
    "&replicaSet=rs0&tls=true"
    "&tlsCAFile=/Users/logusanjeev/Downloads/global-bundle.pem"
)

def main():
    if len(sys.argv) != 3:
        print("Usage: update_mongo.py <pan> <flowId>", file=sys.stderr)
        sys.exit(1)

    pan, flow_id = sys.argv[1], sys.argv[2]
    client = MongoClient(URI)
    col = client[DB_NAME][COLLECTION]

    result = col.update_one({"pan": pan}, {"$set": {"userId": flow_id}})
    if result.matched_count > 0:
        print(f"Updated {COLLECTION} userId={flow_id} for pan={pan}")
    else:
        col.update_one(
            {"_id": FALLBACK_ID},
            {"$set": {"userId": flow_id, "pan": pan}}
        )
        print(f"Fallback: updated {COLLECTION} {FALLBACK_ID} userId={flow_id}, pan={pan}")

    client.close()

if __name__ == "__main__":
    main()
