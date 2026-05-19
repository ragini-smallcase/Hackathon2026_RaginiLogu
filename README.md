# LAMF User Journey Finder

An internal tool to retrieve the Unity User ID at a specific LAMF loan journey step. Enter a PAN, phone number, lender, and desired journey status — the tool creates/fetches the Unity user and returns the user ID and loan ID at that step.

---

## Prerequisites

- Java 17+
- Maven 3.8+

---

## Setup

1. **Clone the repo**
   ```bash
   git clone https://github.com/ragini-smallcase/Hackathon2026_RaginiLogu.git
   cd Hackathon2026_RaginiLogu
   ```

2. **Set environment variables** for the Unity API credentials:
   ```bash
   export GATEWAY_SECRET=your_gateway_secret
   export GATEWAY_AUTH_TOKEN=your_auth_token
   ```

---

## Running the app

```bash
mvn spring-boot:run
```

Then open your browser at:
```
http://localhost:8080
```

---

## Using the UI

Fill in the form fields:

| Field | Required | Description |
|---|---|---|
| PAN | Yes | 10-character PAN (e.g. `ABCDE1234F`) |
| Phone Number | Yes | 10-digit mobile number |
| Lender | Yes | Select from Bajaj Finserv, DSP, SIB |
| Journey Status | Yes | The loan step you want the user to be at |
| Transaction ID | No | Optional transaction reference |
| Holdings | Yes | JSON array of MF holdings (pre-filled with sample data) |

Click **Find User ID** — the result card will show the Unity User ID and Loan ID (LID) for that journey step.

---

## Journey Statuses

| Status | Description |
|---|---|
| `CONFIRM_OFFER` | User confirming the loan offer |
| `FETCH_CKYC` | Fetching CKYC data |
| `LINK_BANK_ACCOUNT` | Bank account linkage |
| `IMPORT_HOLDINGS` | Holdings import |
| `PLEDGE_FUNDS` | MF pledge step |
| `SIGN_AGREEMENT` | Loan agreement signing |
| `VIDEO_KYC` | Video KYC verification |
| `SUBMITTED` | Application submitted |

---

## Building a JAR

```bash
mvn clean package
java -DGATEWAY_SECRET=your_secret -DGATEWAY_AUTH_TOKEN=your_token -jar target/Hackathon2026_RaginiLogu-1.0.0.jar
```