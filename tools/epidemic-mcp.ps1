param(
    [Parameter(Mandatory=$true)][string]$Tool,
    [Parameter(Mandatory=$true)][string]$ArgumentsJson,
    [string]$OutputFile
)
$ErrorActionPreference = 'Stop'
$epidemicToken = $env:EPIDEMIC_SOUND_API_KEY
if (-not $epidemicToken) { $epidemicToken = [Environment]::GetEnvironmentVariable('EPIDEMIC_SOUND_API_KEY', 'User') }
if (-not $epidemicToken) { throw 'EPIDEMIC_SOUND_API_KEY is not set.' }
$epidemicEndpoint = 'https://www.epidemicsound.com/a/mcp-service/mcp'
$epidemicHeaders = @{ Authorization = ('Bearer ' + $epidemicToken); Accept = 'application/json, text/event-stream' }
function Invoke-EpidemicRpc($payload) {
    $body = $payload | ConvertTo-Json -Depth 30 -Compress
    $response = Invoke-WebRequest -UseBasicParsing -Uri $epidemicEndpoint -Method Post -Headers $epidemicHeaders -ContentType 'application/json' -Body $body -TimeoutSec 45
    if ($response.Headers['Mcp-Session-Id']) { $epidemicHeaders['Mcp-Session-Id'] = $response.Headers['Mcp-Session-Id'] }
    if ($response.Content.TrimStart().StartsWith('{')) { return ($response.Content | ConvertFrom-Json) }
    foreach ($line in ($response.Content -split "`n")) {
        if ($line.StartsWith('data: {')) { return ($line.Substring(6) | ConvertFrom-Json) }
    }
    throw 'MCP response did not contain a JSON result.'
}
$null = Invoke-EpidemicRpc @{jsonrpc='2.0';id=1;method='initialize';params=@{protocolVersion='2024-11-05';capabilities=@{};clientInfo=@{name='aurorion-audio';version='1.0'}}}
$null = Invoke-WebRequest -UseBasicParsing -Uri $epidemicEndpoint -Method Post -Headers $epidemicHeaders -ContentType 'application/json' -Body '{"jsonrpc":"2.0","method":"notifications/initialized"}' -TimeoutSec 45
$result = Invoke-EpidemicRpc @{jsonrpc='2.0';id=2;method='tools/call';params=@{name=$Tool;arguments=($ArgumentsJson | ConvertFrom-Json)}}
if ($result.error -or $result.result.isError) { throw ($result | ConvertTo-Json -Depth 30) }
$text = $result.result.content | Where-Object { $_.type -eq 'text' } | Select-Object -ExpandProperty text
if ($OutputFile) { [IO.File]::WriteAllText($OutputFile, ($text -join "`n")) } else { $text }
