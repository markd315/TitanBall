# Titanball Pilot Light Stack Deployment & Verification Guide

This guide walks you through the steps to build the server Docker image, deploy the self-contained ECS Fargate CloudFormation stack directly, and verify that the "pilot light" warming and reaper systems are running.

---

## Prerequisites

Before running the commands, ensure you have:
1. Installed the [AWS CLI](https://aws.amazon.com/cli/).
2. Configured your credentials: `aws configure`.

---

## 1. Build and Push the Docker Image

The ECS Task Definition fetches the Titanball server image from Amazon ECR. 

Run the following commands in the root of the `TitanBall/` directory:

```bash
# 1. Log in to your Amazon ECR Registry (Replace region with your AWS region, e.g., us-east-1)
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 720291373173.dkr.ecr.us-east-1.amazonaws.com

# 2. Create the ECR repository if it doesn't already exist
aws ecr create-repository --repository-name titanball --region us-east-1 || true

# 3. Build the Docker image
docker build -t titanball:latest .

# 4. Tag the image for ECR
docker tag titanball:latest 720291373173.dkr.ecr.us-east-1.amazonaws.com/titanball:latest

# 5. Push the image to ECR
docker push 720291373173.dkr.ecr.us-east-1.amazonaws.com/titanball:latest
```

---

## 2. Deploy the CloudFormation Stack

The stack is self-contained (building a VPC, public subnet, security groups, ECS Cluster, Fargate 2-container Task Definition, ECS Service, and Pilot Light Lambda):

```bash
aws cloudformation deploy \
  --template-file operations/pilot-light-stack.yaml \
  --stack-name titanball-pilot-light \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides \
      CloudFrontDistributionId=E250EEB1SQKL1Z \
      ECRImageUri=720291373173.dkr.ecr.us-east-1.amazonaws.com/titanball:latest \
      DatabaseRootPassword=yoursecurepassword \
      ExpirationHours=4
```

---

## 3. Zero-Base-Cost Routing Flow

1. The client opens `home.html` and executes `warmServer()` (`POST https://blockforger.net/pages/titanball/api/warm`).
2. CloudFront forwards `/pages/titanball/api/warm` to the Lambda Function URL.
3. The Lambda ensures the ECS cluster and `TitanballService` exist, scales `desiredCount` to `1` (or creates the service with 1 task if missing), and refreshes the `ExpirationTime` tag to `now + 4 hours`.
4. The Lambda polls for the Fargate task's ENI Public IP and updates the Route 53 A record `titanball-server.blockforger.net` -> `<Public IP>`.
5. Subsequent API calls (`/pages/titanball/api/*`) and WebSocket traffic (`/pages/titanball/game`) route through CloudFront directly to `titanball-server.blockforger.net:8080`.
6. An EventBridge rule scans every 10 minutes. If the server has been idle past its `ExpirationTime`, `TitanballService` scales back to `0`.

---

## 4. Manual Verification

### A. Testing the Warm API Endpoint
```bash
# Test through blockforger.net:
curl -X POST https://blockforger.net/pages/titanball/api/warm

# Or test the Lambda Function URL directly:
curl -X POST <LambdaFunctionUrl>
```

**Expected Response (when warm):**
```json
{
  "status": "running",
  "message": "ECS service running and DNS mapped",
  "expirationTime": "2026-08-23T22:30:00.000000+00:00",
  "serverUrl": "https://blockforger.net",
  "wsUrl": "wss://blockforger.net",
  "publicIp": "54.210.16.152"
}
```

### B. Auto-Shutdown
After 4 hours without warming pings, the EventBridge reaper sets `TitanballService` desired count to `0`.
