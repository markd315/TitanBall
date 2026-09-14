#!/bin/bash
# Bash script to build, tag, and push the Titanball server image to Amazon ECR.
set -e

# Navigate to project root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

AWS_REGION="${AWS_REGION:-us-east-1}"
AWS_ACCOUNT_ID="720291373173"
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
IMAGE_NAME="titanball"

echo "1. Ensuring headless games are disabled in res/game.cfg..."
sed -i.bak 's/^headless\.enabled=.*/headless.enabled=false/' res/game.cfg && rm -f res/game.cfg.bak
echo "   $(grep '^headless\.enabled=' res/game.cfg)"

echo "2. Authenticating Docker with Amazon ECR ($ECR_REGISTRY)..."
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$ECR_REGISTRY"

echo "3. Building Docker image: $IMAGE_NAME..."
docker build -t "$IMAGE_NAME" .

echo "4. Tagging Docker image for ECR ($ECR_REGISTRY/$IMAGE_NAME)..."
docker tag "$IMAGE_NAME" "$ECR_REGISTRY/$IMAGE_NAME"

echo "5. Pushing Docker image to ECR ($ECR_REGISTRY/$IMAGE_NAME)..."
docker push "$ECR_REGISTRY/$IMAGE_NAME"

echo "Backend server image successfully built and pushed to ECR: $ECR_REGISTRY/$IMAGE_NAME"
