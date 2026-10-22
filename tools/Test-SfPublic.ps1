param([string]$BookId = '388081', [string]$Device = '910D166A-736E-3231-8B21-8D12DFD75F16', [string[]]$ProbeRoutes = @())
$ErrorActionPreference = 'Stop'
# Anonymous protocol smoke test. No account, password or user cookies are read.
function Get-SfSign([string]$nonce, [long]$timestamp) {
    $repeat = $nonce * 4
    $lengths = @(13,16,36,36)
    $mix = ''
    for ($i=0; $i -lt 4; $i++) { $mix += $repeat.Substring(([int][char]$repeat[$i+1] % 36), $lengths[$i]) }
    $auth = [string]$timestamp + 'lPQDb9AKO7$LjkPG' + $device + $nonce
    $value = ''
    for ($i=0; $i -lt 101; $i++) { $value += [char](([int][char]$mix[$i] + [int][char]$auth[$i]) -shr 1) }
    $reorder = $value.Substring(65) + $value.Substring(0,13) + $value.Substring(29,36) + $value.Substring(13,16)
    $normal = ''
    foreach ($c in $reorder.ToCharArray()) {
        $n = [int]$c
        if ($n -lt 48) { if (($n+19) -gt 57 -and ($n+19) -lt 65) { $normal += '9' } else { $normal += [char]($n+19) } }
        elseif (($n -gt 57 -and $n -lt 65) -or ($n -gt 90 -and $n -lt 97)) { $normal += [char]($n+19) }
        else { $normal += $c }
    }
    [Convert]::ToHexString([System.Security.Cryptography.MD5]::HashData([Text.Encoding]::UTF8.GetBytes($normal)))
}
function Get-SfPublic([string]$path) {
    for ($attempt=0; $attempt -lt 3; $attempt++) {
        if ($attempt -gt 0) { Start-Sleep -Seconds $attempt }
        $nonce=[guid]::NewGuid().ToString().ToUpperInvariant()
        $timestamp=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        $headers=@{
            'Accept'='application/vnd.sfacg.api+json;version=1'
            'Authorization'='Basic YW5kcm9pZHVzZXI6MWEjJDUxLXl0Njk7KkFjdkBxeHE='
            'User-Agent'="boluobao/5.2.16(android;35)/OPPO/$($device.ToLowerInvariant())/OPPO"
            'sfsecurity'="nonce=$nonce&timestamp=$timestamp&devicetoken=$device&sign=$(Get-SfSign $nonce $timestamp)"
        }
        $response=Invoke-WebRequest "https://api.sfacg.com/$path" -Headers $headers -SkipHeaderValidation -TimeoutSec 25 -SkipHttpErrorCheck
        $payload=$response.Content | ConvertFrom-Json
        if ($response.StatusCode -eq 429 -or $payload.status.httpCode -eq 429 -or $payload.status.errorCode -eq 1148) { throw 'Platform rate limit or verification: probe stopped' }
        if ($payload.status.httpCode -eq 417) { continue }
        if ($payload.status.httpCode -ne 200) { throw "SF returned HTTP $($response.StatusCode), status $($payload.status.httpCode), code $($payload.status.errorCode)" }
        $script:LastAttempts = $attempt + 1
        return ,$payload.data
    }
    throw 'SF signature rejected after three attempts; no more requests sent'
}
if ($ProbeRoutes.Count -gt 0) {
    New-Item -ItemType Directory -Path 'build' -Force | Out-Null
    $probeIndex = 0
    foreach ($route in $ProbeRoutes) {
        if ($probeIndex -gt 0) { Start-Sleep -Seconds 2 }
        if ($route -notmatch '^(noveltypes(?:\?|$)|novels/|novel/|users/\d+/(novels|dynamics)|chaps/0/\d+/tsukkomis|Chaps/|ranks/|search/)') { throw 'Probe accepts only known public read routes' }
        $data = Get-SfPublic $route
        $probeIndex++
        ConvertTo-Json -InputObject $data -Depth 30 | Set-Content "build/sf-probe-$probeIndex.json" -Encoding utf8
        [pscustomobject]@{ route=$route; attempts=$script:LastAttempts; count=@($data).Count; keys=($data | Select-Object -First 1).PSObject.Properties.Name -join ',' } | ConvertTo-Json -Compress
    }
    return
}
$meta=Get-SfPublic "novels/${BookId}?expand=bigNovelCover"
$dirs=Get-SfPublic "novels/$BookId/dirs?expand=originNeedFireMoney"
$chapters=@($dirs.volumeList | ForEach-Object { $_.chapterList })
$free=$chapters | Where-Object { -not $_.isVip -and -not $_.needFireMoney -and -not $_.originNeedFireMoney } | Select-Object -First 1
if (-not $free) { throw 'No free chapter available for anonymous test' }
$chapter=Get-SfPublic "Chaps/$($free.chapId)?expand=content%2Cexpand.content"
$raw=[string]$chapter.content + [string]$chapter.expand.content
if ([string]::IsNullOrWhiteSpace($raw)) { throw 'Empty public chapter' }
[pscustomobject]@{ bookId=$BookId; title=$meta.novelName; chapters=$chapters.Count; freeChapterId=$free.chapId; rawLength=$raw.Length; isVip=$chapter.isVip; encrypted=$chapter.isContentEncrypted; result='PASS public catalog and chapter'; signatureVector=(Get-SfSign '00112233-4455-6677-8899-AABBCCDDEEFF' 1728000000000) } | ConvertTo-Json
