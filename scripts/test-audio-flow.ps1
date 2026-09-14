param(
    [Parameter(Mandatory = $true)][string]$AudioPath,
    [string]$BaseUrl = 'http://localhost:8081',
    [Guid]$UserId = [Guid]::NewGuid(),
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

$body = @{
    userId = $UserId.ToString()
    title = 'Audio flow verification'
    startedAt = [DateTimeOffset]::Now.ToString('o')
    timezone = 'Asia/Kolkata'
    source = 'MANUAL_TEST'
} | ConvertTo-Json
$session = Invoke-RestMethod -Method Post "$base/api/v1/memory/sessions" -ContentType application/json -Body $body
Write-Host "Session: $($session.id) | User: $UserId"
$sessionUrl = "$base/api/v1/memory/sessions/$($session.id)"
$upload = & curl.exe --silent --show-error --fail-with-body -X POST "$sessionUrl/audio" -F "file=@$audioFile"
if ($LASTEXITCODE -ne 0) { throw "Audio upload failed: $upload" }

$result = Invoke-RestMethod -Method Post "$sessionUrl/process" -TimeoutSec 240
$chunks = @(Invoke-RestMethod "$sessionUrl/transcript-chunks")
if ($chunks.Count -eq 0) { throw 'Processing returned no transcript chunks.' }
$allMemories = @(Invoke-RestMethod "$base/api/v1/memory/memories?userId=$UserId&limit=500")
$memories = @($allMemories | Where-Object { $_.sessionId -eq $session.id })
Write-Host "Saved $($chunks.Count) transcript chunks and $($memories.Count) memories."
if ($memories.Count -eq 0) { throw 'No memories were extracted. Try a recording with a clear fact, decision, or promise.' }
$answer = Invoke-RestMethod -Method Post "$base/api/v1/memory/ask" -ContentType application/json -TimeoutSec 180 -Body (@{
    userId = $UserId.ToString(); question = $Question; topK = 5
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
[pscustomobject]@{ SessionId=$session.id; UserId=$UserId; Transcript=$result.transcription.transcriptText; Answer=$answer } | ConvertTo-Json -Depth 12
