# Titanball Pilot Light Stack Deployment & Verification Guide

This guide walks you through the steps to build the server Docker image, deploy the self-contained CloudFormation stack directly, and verify that the "pilot light" warming and reaper systems are running.

---

## Prerequisites

Before running the commands, ensure you have:
1. Installed the [AWS CLI](https://aws.amazon.com/cli/).
2. Configured your credentials: `aws configure`.

---

## 1. Build and Push the Docker Image

The ECS Task Definition and EC2 Compose setup fetch the Titanball server image from Amazon ECR. 

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

Since the stack is fully self-contained (automatically building a clean VPC, subnets, and internet gateway), you do not need to specify any load balancer or network IDs as parameters.

Run this command from the `operations/` directory:

```bash
aws cloudformation deploy    --template-file operations/pilot-light-stack.yaml    --stack-name titanball-pilot-light    --capabilities CAPABILITY_IAM    --parameter-overrides       DeploymentType=ECS        CloudFrontDistributionId=E250EEB1SQKL1Z        ECRImageUri=720291373173.dkr.ecr.us-east-1.amazonaws.com/titanball:latest        DatabaseRootPassword=yoursecurepassword
```


hot refresh
```
 aws cloudformation delete-stack --stack-name titanball-pilot-light; sleep 250; aws cloudformation deploy    --template-file operations/pilot-light-stack.yaml    --stack-name titanball-pilot-light    --capabilities CAPABILITY_IAM    --parameter-overrides       DeploymentType=ECS        CloudFrontDistributionId=E250EEB1SQKL1Z        ECRImageUri=720291373173.dkr.ecr.us-east-1.amazonaws.com/titanball:latest        DatabaseRootPassword=yoursecurepassword
```

> [NOTE]
> If you prefer to deploy using **EC2** instead of ECS, change `DeploymentType=ECS` to `DeploymentType=EC2` in the parameter overrides.

---

## 3. Dynamic IP Mapping (ALB-Free Routing)

No Load Balancers (ALBs) are needed! Here is how the zero-base-cost routing works:
1. The client opens `home.html` and sends a `POST` request to `https://blockforger.net/pages/titanball/api/warm` (which maps via CloudFront to the Lambda Function URL).
2. The Lambda function starts the Fargate task or EC2 instance, polls until the network interface is created, and fetches the **public IP address** of the running instance.
3. The Lambda returns the public IP in its response:
   ```json
   {
     "status": "running",
     "serverUrl": "http://54.210.16.152:8080",
     "wsUrl": "ws://54.210.16.152:8080"
   }
   ```
4. The Web Client dynamically updates its connection endpoints. All subsequent API queries go to `http://54.210.16.152:8080/pages/titanball/api/*` and game WebSocket frames route to `ws://54.210.16.152:8080/pages/titanball/game` directly!

---

## 4. Manual Verification

### A. Testing the Warm API Endpoint
You can trigger the warming mechanism manually by executing a `POST` request to the `/pages/titanball/api/warm` path or directly to the Lambda Function URL (available in the CloudFormation stack outputs):

```bash
# Test through blockforger.net once DNS/CloudFront is active:
curl -X POST https://blockforger.net/pages/titanball/api/warm

# Or test the Lambda Function URL directly:
curl -X POST <LambdaFunctionUrl>
```

**Expected Response:**
```json
{
  "status": "starting",
  "message": "ECS service starting",
  "expirationTime": "2026-08-06T19:13:27.000000+00:00",
  "serverUrl": "http://54.210.16.152:8080",
  "wsUrl": "ws://54.210.16.152:8080"
}
```

### B. Verification of Scaling Up
* **ECS Mode**: Navigate to the ECS Console, select `TitanballCluster`, and verify that the `TitanballService` is scaling up its Desired Count to `1`.
* **Auto-Shutdown**: Within 2 hours of inactivity, the EventBridge reaper will scale the ECS Service desired count back to `0`, offloading all compute costs.
