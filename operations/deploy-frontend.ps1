# PowerShell script to build and deploy the Titanball frontend to S3
$S3_BUCKET = "s3://public-720291373173-prod/pages/titanball/"
$CF_DISTRIBUTION_ID = "E250EEB1SQKL1Z"

Write-Host "1. Building the web client..." -ForegroundColor Green
Push-Location "client-web"
npm run build
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to build the frontend."
    Pop-Location
    exit 1
}
Pop-Location

Write-Host "2. Preparing files for hosting subpath (home.html)..." -ForegroundColor Green
Copy-Item "client-web/dist/index.html" "client-web/dist/home.html" -Force

Write-Host "3. Uploading assets to S3: $S3_BUCKET" -ForegroundColor Green
# Sync dist folder to S3, excluding index.html and deleting orphaned files
aws s3 sync client-web/dist/ $S3_BUCKET --exclude "index.html" --delete

Write-Host "4. Invalidating CloudFront cache for subpath /pages/titanball/*..." -ForegroundColor Green
aws cloudfront create-invalidation --distribution-id $CF_DISTRIBUTION_ID --paths "/pages/titanball/*"

Write-Host "Deployment completed successfully! Frontend is live at https://blockforger.net/pages/titanball/home.html" -ForegroundColor Green
