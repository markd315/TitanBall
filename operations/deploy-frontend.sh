#!/bin/bash
# Bash script to build and deploy the Titanball frontend to S3
set -e

S3_BUCKET="s3://public-720291373173-prod/pages/titanball/"
CF_DISTRIBUTION_ID="E250EEB1SQKL1Z"

echo "1. Building the web client..."
cd client-web
npm run build
cd ..

echo "2. Preparing files for hosting subpath (home.html)..."
cp client-web/dist/index.html client-web/dist/home.html

echo "3. Uploading assets to S3: $S3_BUCKET"
# Sync dist folder to S3, excluding index.html and deleting orphaned files
aws s3 sync client-web/dist/ "$S3_BUCKET" --exclude "index.html" --delete

echo "4. Invalidating CloudFront cache for subpath /pages/titanball/*..."
aws cloudfront create-invalidation --distribution-id "$CF_DISTRIBUTION_ID" --paths "/pages/titanball/*"

echo "Deployment completed successfully! Frontend is live at https://blockforger.net/pages/titanball/home.html"
