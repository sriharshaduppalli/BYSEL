# Highest-value GCP keep-warm + alerts for BYSEL.
# Safe to re-run. Does not raise Cloud Run max instances.
#
#   powershell -File deploy/setup-gcp-keepwarm.ps1

$ErrorActionPreference = "Stop"
$Project = "project-c72c9386-c1bd-4dba-9db"
$Region = "europe-west1"
$ServiceUrl = "https://bysel-services-67anayhlkq-ew.a.run.app"
$AlertEmail = "bysel.trader@gmail.com"

gcloud services enable `
  cloudscheduler.googleapis.com `
  clouderrorreporting.googleapis.com `
  --project=$Project

$existingWarmup = $null
try { $existingWarmup = gcloud scheduler jobs describe bysel-warmup --location=$Region --project=$Project 2>$null } catch { }
if (-not $existingWarmup) {
  gcloud scheduler jobs create http bysel-warmup `
    --project=$Project `
    --location=$Region `
    --schedule="*/12 * * * *" `
    --time-zone="Asia/Kolkata" `
    --uri="$ServiceUrl/warmup" `
    --http-method=GET `
    --attempt-deadline=60s `
    --description="Ping /warmup so quotes, DB, and (after next deploy) Scanner stay warm"
} else {
  Write-Host "bysel-warmup already exists"
}

$existingScanner = $null
try { $existingScanner = gcloud scheduler jobs describe bysel-scanner-warm --location=$Region --project=$Project 2>$null } catch { }
if (-not $existingScanner) {
  gcloud scheduler jobs create http bysel-scanner-warm `
    --project=$Project `
    --location=$Region `
    --schedule="*/10 9-16 * * 1-5" `
    --time-zone="Asia/Kolkata" `
    --uri="$ServiceUrl/market/scanner?mode=long_term&limit=30" `
    --http-method=GET `
    --attempt-deadline=180s `
    --description="Pre-warm BYSEL Score scanner cache during NSE hours"
} else {
  Write-Host "bysel-scanner-warm already exists"
}

$token = gcloud auth print-access-token
$headers = @{
  Authorization = "Bearer $token"
  "Content-Type" = "application/json"
}
$channelsUrl = "https://monitoring.googleapis.com/v3/projects/$Project/notificationChannels"
$channels = Invoke-RestMethod -Method GET -Uri $channelsUrl -Headers $headers
$channel = $channels.notificationChannels | Where-Object {
  $_.type -eq "email" -and $_.labels.email_address -eq $AlertEmail
} | Select-Object -First 1
if (-not $channel) {
  $channel = Invoke-RestMethod -Method POST -Uri $channelsUrl -Headers $headers -Body (@{
    type = "email"
    displayName = "BYSEL operator"
    labels = @{ email_address = $AlertEmail }
  } | ConvertTo-Json)
}

$policiesUrl = "https://monitoring.googleapis.com/v3/projects/$Project/alertPolicies"
$policies = Invoke-RestMethod -Method GET -Uri $policiesUrl -Headers $headers
$already = $policies.alertPolicies | Where-Object { $_.displayName -eq "BYSEL Cloud Run 5xx" }
if (-not $already) {
  $policy = @{
    displayName = "BYSEL Cloud Run 5xx"
    combiner = "OR"
    conditions = @(
      @{
        displayName = "bysel-services 5xx in 5 minutes"
        conditionThreshold = @{
          filter = 'resource.type="cloud_run_revision" AND resource.labels.service_name="bysel-services" AND metric.type="run.googleapis.com/request_count" AND metric.labels.response_code_class="5xx"'
          aggregations = @(
            @{
              alignmentPeriod = "300s"
              perSeriesAligner = "ALIGN_DELTA"
              crossSeriesReducer = "REDUCE_SUM"
              groupByFields = @("resource.label.service_name")
            }
          )
          comparison = "COMPARISON_GT"
          thresholdValue = 3
          duration = "0s"
          trigger = @{ count = 1 }
        }
      }
    )
    notificationChannels = @($channel.name)
    alertStrategy = @{ autoClose = "1800s" }
    documentation = @{
      content = "Cloud Run bysel-services returned multiple 5xx responses. Check /warmup, Cloud SQL, and Cloud Logging for scanner. / auth. errors. Do not raise max instances above 1."
      mimeType = "text/markdown"
    }
  }
  Invoke-RestMethod -Method POST -Uri $policiesUrl -Headers $headers -Body ($policy | ConvertTo-Json -Depth 12) | Out-Null
  Write-Host "Created alert BYSEL Cloud Run 5xx -> $AlertEmail"
} else {
  Write-Host "Alert BYSEL Cloud Run 5xx already exists"
}

Write-Host "Done. Scheduler + 5xx email alert are on project $Project"
