param(
    [Parameter(Mandatory = $true)][string]$AudioPath,
    [Parameter(Mandatory = $true)][string]$Username,
    [string]$BaseUrl = 'http://localhost:8081',
    [string]$Question = 'What was discussed in this recording?'
)

$ErrorActionPreference = 'Stop'
trap {
    $message = $_.ErrorDetails.Message
    if (-not $message -and $_.Exception.Response) {
        $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
        try { $message = $reader.ReadToEnd() } finally { $reader.Dispose() }
    }
    if (-not $message) { $message = $_.Exception.Message }
    [Console]::Error.WriteLine($message)
    exit 1
}
$audioFile = (Resolve-Path -LiteralPath $AudioPath).Path
$base = $BaseUrl.TrimEnd('/')
$health = Invoke-RestMethod "$base/api/v1/health"
if (-not $health.transcriptionApiKeyConfigured -or -not $health.chatApiKeyConfigured) {
    throw 'Configure chat and transcription keys in application.yml before running this test.'
}

$securePassword = Read-Host "Password for $Username" -AsSecureString
$password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword))
$login = Invoke-RestMethod -Method Post "$base/api/v1/auth/login" -ContentType application/json -Body (@{
    username = $Username; password = $password
} | ConvertTo-Json)
$password = $null
$headers = @{ Authorization = "Bearer $($login.token)" }

$body = @{
    title = 'Audio flow verification'
    startedAt = [DateTimeOffset]::Now.ToString('o')
    timezone = 'Asia/Kolkata'
    source = 'MANUAL_TEST'
} | ConvertTo-Json
$session = Invoke-RestMethod -Method Post "$base/api/v1/memory/sessions" -Headers $headers -ContentType application/json -Body $body
Write-Host "Session: $($session.id) | User: $($login.userId)"
$sessionUrl = "$base/api/v1/memory/sessions/$($session.id)"
$upload = & curl.exe --silent --show-error --fail-with-body -X POST "$sessionUrl/audio" `
    -H "Authorization: Bearer $($login.token)" -F "file=@$audioFile"
if ($LASTEXITCODE -ne 0) { throw "Audio upload failed: $upload" }

Invoke-RestMethod -Method Post "$sessionUrl/process" -Headers $headers | Out-Null
$deadline = (Get-Date).AddMinutes(10)
do {
    Start-Sleep -Seconds 4
    $detail = Invoke-RestMethod "$sessionUrl/detail" -Headers $headers
    Write-Host "Status: $($detail.session.status)"
} while ($detail.session.status -notin 'COMPLETED', 'FAILED' -and (Get-Date) -lt $deadline)
if ($detail.session.status -ne 'COMPLETED') { throw "Processing did not complete: $($detail.session.errorMessage)" }

$chunks = @(Invoke-RestMethod "$sessionUrl/transcript-chunks" -Headers $headers)
if ($chunks.Count -eq 0) { throw 'Processing returned no transcript chunks.' }
$allMemories = @(Invoke-RestMethod "$base/api/v1/memory/memories?limit=500" -Headers $headers)
$memories = @($allMemories | Where-Object { $_.sessionId -eq $session.id })
Write-Host "Saved $($chunks.Count) transcript chunks and $($memories.Count) memories."
if ($memories.Count -eq 0) { throw 'No memories were extracted. Try a recording with a clear fact, decision, or promise.' }
$answer = Invoke-RestMethod -Method Post "$base/api/v1/memory/ask" -Headers $headers -ContentType application/json -TimeoutSec 180 -Body (@{
    question = $Question; topK = 5
} | ConvertTo-Json)
if (-not $answer.sources) { throw 'Recall returned no memory sources.' }
foreach ($source in $answer.sources) {
    if (-not $source.evidence) { throw 'A recalled memory has no transcript evidence.' }
    foreach ($evidence in $source.evidence) {
        if ($source.sessionId -eq $session.id -and $evidence.transcriptChunkId -notin $chunks.id) {
            throw 'A recalled memory points to an unknown transcript chunk.'
        }
    }
}
$usage = Invoke-RestMethod "$base/api/v1/usage" -Headers $headers
[pscustomobject]@{ SessionId=$session.id; UserId=$login.userId; Summary=$detail.session.summary; Answer=$answer; Usage=$usage } | ConvertTo-Json -Depth 12
