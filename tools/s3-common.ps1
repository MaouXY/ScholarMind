<#
.SYNOPSIS
  RustFS / S3 的最小客户端（纯 PowerShell 实现 AWS SigV4 签名），无需 mc 等外部可执行文件。

.DESCRIPTION
  国内网络常常下载不到 mc（dl.min.io 与 GitHub release 都可能不可达），而本工具链只需要
  「列对象 / 取对象 / 放对象 / 删对象 / 读写桶策略」，因此这里用 .NET 自带加密库实现签名，
  做到零依赖。支持 path-style 寻址（RustFS 默认就是 path-style）。

  对外函数：
    Get-S3ObjectList  - 列出桶内全部对象（自动翻页）
    Get-S3Object      - 下载对象到本地文件
    Put-S3Object      - 上传本地文件为指定对象 key
    Remove-S3Object   - 删除对象
    Get-S3BucketPolicy / Set-S3BucketPolicy - 读写桶访问策略
    Invoke-S3Request  - 通用签名请求（GET/PUT/DELETE + 查询参数）

.EXAMPLE
  . "$PSScriptRoot/s3-common.ps1"
  $objects = Get-S3ObjectList -Endpoint http://127.0.0.1:9990 -AccessKey rustfsadmin -SecretKey rustfsadmin -Bucket scholar-mind
  $objects | Select-Object -First 5
#>

function New-S3Sha256Hex {
    param([byte[]]$Bytes)
    if ($null -eq $Bytes) { $Bytes = [byte[]]::new(0) }
    return [System.Convert]::ToHexString([System.Security.Cryptography.SHA256]::HashData($Bytes)).ToLowerInvariant()
}

function New-S3Hmac {
    param([byte[]]$Key, [string]$Data)
    $hmac = [System.Security.Cryptography.HMACSHA256]::new($Key)
    try { return $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Data)) } finally { $hmac.Dispose() }
}

function ConvertTo-S3Encoded {
    param([string]$Value, [switch]$KeepSlash)
    if ($null -eq $Value) { return '' }
    $encoded = [System.Uri]::EscapeDataString($Value)
    if ($KeepSlash) { $encoded = $encoded -replace '%2F', '/' }
    return $encoded
}

function Invoke-S3Request {
    <#
      .OUTPUTS  @{ StatusCode; ContentBytes; ContentText; Headers }
    #>
    param(
        [Parameter(Mandatory = $true)][string]$Endpoint,
        [Parameter(Mandatory = $true)][string]$AccessKey,
        [Parameter(Mandatory = $true)][string]$SecretKey,
        [string]$Region = 'us-east-1',
        [string]$Bucket = '',
        [string]$Key = '',
        [ValidateSet('GET', 'PUT', 'DELETE', 'HEAD')][string]$Method = 'GET',
        [hashtable]$Query = @{},
        [byte[]]$Body,
        [string]$ContentType = '',
        [string]$Service = 's3'
    )

    $endpointUri = [System.Uri]$Endpoint
    $hostHeader = $endpointUri.Authority

    # ---- canonical URI（path-style：/bucket/key，key 保留斜杠）----
    $canonicalUri = '/'
    if ($Bucket) { $canonicalUri += ConvertTo-S3Encoded -Value $Bucket }
    if ($Key) { $canonicalUri += '/' + (ConvertTo-S3Encoded -Value $Key -KeepSlash) }

    # ---- canonical query（按 key 排序，均需编码）----
    $queryPairs = @()
    foreach ($k in ($Query.Keys | Sort-Object)) {
        $value = if ($null -eq $Query[$k]) { '' } else { [string]$Query[$k] }
        $queryPairs += ('{0}={1}' -f (ConvertTo-S3Encoded -Value $k), (ConvertTo-S3Encoded -Value $value))
    }
    $canonicalQuery = $queryPairs -join '&'

    $payloadBytes = if ($null -eq $Body) { [byte[]]::new(0) } else { $Body }
    $payloadHash = New-S3Sha256Hex -Bytes $payloadBytes

    $amzDate = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
    $dateStamp = (Get-Date).ToUniversalTime().ToString('yyyyMMdd')

    # ---- canonical headers 必须包含 host 与所有 x-amz-* ----
    $headers = [ordered]@{
        'host'                 = $hostHeader
        'x-amz-content-sha256' = $payloadHash
        'x-amz-date'           = $amzDate
    }
    if ($ContentType) { $headers['content-type'] = $ContentType }
    $sortedHeaderNames = @($headers.Keys | Sort-Object)
    $canonicalHeaders = ($sortedHeaderNames | ForEach-Object { '{0}:{1}' -f $_, ([string]$headers[$_]).Trim() }) -join "`n"
    $signedHeaders = $sortedHeaderNames -join ';'

    $canonicalRequest = @(
        $Method
        $canonicalUri
        $canonicalQuery
        ($canonicalHeaders + "`n")
        $signedHeaders
        $payloadHash
    ) -join "`n"

    $credentialScope = "$dateStamp/$Region/$Service/aws4_request"
    $stringToSign = @(
        'AWS4-HMAC-SHA256'
        $amzDate
        $credentialScope
        (New-S3Sha256Hex -Bytes ([System.Text.Encoding]::UTF8.GetBytes($canonicalRequest)))
    ) -join "`n"

    $kDate = New-S3Hmac -Key ([System.Text.Encoding]::UTF8.GetBytes("AWS4$SecretKey")) -Data $dateStamp
    $kRegion = New-S3Hmac -Key $kDate -Data $Region
    $kService = New-S3Hmac -Key $kRegion -Data $Service
    $kSigning = New-S3Hmac -Key $kService -Data 'aws4_request'
    $signature = [System.Convert]::ToHexString((New-S3Hmac -Key $kSigning -Data $stringToSign)).ToLowerInvariant()

    $authorization = 'AWS4-HMAC-SHA256 Credential={0}/{1}, SignedHeaders={2}, Signature={3}' -f $AccessKey, $credentialScope, $signedHeaders, $signature

    $url = '{0}{1}' -f $Endpoint.TrimEnd('/'), $canonicalUri
    if ($canonicalQuery) { $url += "?$canonicalQuery" }

    $request = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), $url)
    try {
        $request.Headers.TryAddWithoutValidation('x-amz-date', $amzDate) | Out-Null
        $request.Headers.TryAddWithoutValidation('x-amz-content-sha256', $payloadHash) | Out-Null
        $request.Headers.TryAddWithoutValidation('Authorization', $authorization) | Out-Null
        # 注意：零字节 body 不能构造 ByteArrayContent（PowerShell 会把空数组绑成 $null），
        # 此时直接不带 Content，HttpClient 会发 Content-Length: 0，签名仍按空 body 计算。
        if ($payloadBytes.Length -gt 0) {
            $request.Content = [System.Net.Http.ByteArrayContent]::new($payloadBytes)
            if ($ContentType) { $request.Content.Headers.TryAddWithoutValidation('Content-Type', $ContentType) | Out-Null }
        }

        $client = [System.Net.Http.HttpClient]::new()
        $client.Timeout = [TimeSpan]::FromSeconds(120)
        try {
            $response = $client.Send($request)
            $contentBytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
            $contentText = [System.Text.Encoding]::UTF8.GetString($contentBytes)
            return [pscustomobject]@{
                StatusCode   = [int]$response.StatusCode
                ContentBytes = $contentBytes
                ContentText  = $contentText
                Headers      = $response.Headers
            }
        } finally { $client.Dispose() }
    } finally { $request.Dispose() }
}

function Get-S3ObjectList {
    param(
        [Parameter(Mandatory = $true)][string]$Endpoint,
        [Parameter(Mandatory = $true)][string]$AccessKey,
        [Parameter(Mandatory = $true)][string]$SecretKey,
        [Parameter(Mandatory = $true)][string]$Bucket,
        [string]$Region = 'us-east-1'
    )
    $objects = New-Object System.Collections.Generic.List[object]
    $token = $null
    while ($true) {
        $query = @{ 'list-type' = '2'; 'max-keys' = '1000' }
        if ($token) { $query['continuation-token'] = $token }
        $response = Invoke-S3Request -Endpoint $Endpoint -AccessKey $AccessKey -SecretKey $SecretKey -Region $Region `
            -Bucket $Bucket -Method GET -Query $query
        if ($response.StatusCode -ne 200) {
            throw ("列举对象失败：HTTP {0} {1}" -f $response.StatusCode, $response.ContentText)
        }
        [xml]$xml = $response.ContentText
        foreach ($item in $xml.ListBucketResult.Contents) {
            if ($null -eq $item) { continue }
            $objects.Add([pscustomobject]@{ Key = [string]$item.Key; Size = [int64]$item.Size })
        }
        if ([string]$xml.ListBucketResult.IsTruncated -eq 'true') { $token = [string]$xml.ListBucketResult.NextContinuationToken; continue }
        break
    }
    return $objects
}

function Get-S3Object {
    param(
        [Parameter(Mandatory = $true)][string]$Endpoint,
        [Parameter(Mandatory = $true)][string]$AccessKey,
        [Parameter(Mandatory = $true)][string]$SecretKey,
        [Parameter(Mandatory = $true)][string]$Bucket,
        [Parameter(Mandatory = $true)][string]$Key,
        [Parameter(Mandatory = $true)][string]$OutFile,
        [string]$Region = 'us-east-1'
    )
    $response = Invoke-S3Request -Endpoint $Endpoint -AccessKey $AccessKey -SecretKey $SecretKey -Region $Region `
        -Bucket $Bucket -Key $Key -Method GET
    if ($response.StatusCode -ne 200) {
        throw ("取对象失败：{0} -> HTTP {1} {2}" -f $Key, $response.StatusCode, $response.ContentText)
    }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $OutFile) | Out-Null
    [System.IO.File]::WriteAllBytes($OutFile, $response.ContentBytes)
    return $response.ContentBytes.Length
}

function Get-S3ContentTypeByExtension {
    param([string]$Path)
    $ext = [System.IO.Path]::GetExtension($Path).ToLowerInvariant()
    switch ($ext) {
        '.txt'  { 'text/plain; charset=utf-8' }
        '.md'   { 'text/markdown; charset=utf-8' }
        '.json' { 'application/json' }
        '.pdf'  { 'application/pdf' }
        '.png'  { 'image/png' }
        '.jpg'  { 'image/jpeg' }
        '.jpeg' { 'image/jpeg' }
        '.gif'  { 'image/gif' }
        '.webp' { 'image/webp' }
        '.svg'  { 'image/svg+xml' }
        '.docx' { 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' }
        '.doc'  { 'application/msword' }
        '.mp3'  { 'audio/mpeg' }
        '.wav'  { 'audio/wav' }
        default { 'application/octet-stream' }
    }
}

function Put-S3Object {
    param(
        [Parameter(Mandatory = $true)][string]$Endpoint,
        [Parameter(Mandatory = $true)][string]$AccessKey,
        [Parameter(Mandatory = $true)][string]$SecretKey,
        [Parameter(Mandatory = $true)][string]$Bucket,
        [Parameter(Mandatory = $true)][string]$Key,
        [Parameter(Mandatory = $true)][string]$InFile,
        [string]$ContentType = '',
        [string]$Region = 'us-east-1'
    )
    if (-not $ContentType) { $ContentType = Get-S3ContentTypeByExtension -Path $InFile }
    $bytes = [System.IO.File]::ReadAllBytes($InFile)
    $response = Invoke-S3Request -Endpoint $Endpoint -AccessKey $AccessKey -SecretKey $SecretKey -Region $Region `
        -Bucket $Bucket -Key $Key -Method PUT -Body $bytes -ContentType $ContentType
    if ($response.StatusCode -notin 200, 201) {
        throw ("上传对象失败：{0} -> HTTP {1} {2}" -f $Key, $response.StatusCode, $response.ContentText)
    }
    return $bytes.Length
}

function Remove-S3Object {
    param(
        [Parameter(Mandatory = $true)][string]$Endpoint,
        [Parameter(Mandatory = $true)][string]$AccessKey,
        [Parameter(Mandatory = $true)][string]$SecretKey,
        [Parameter(Mandatory = $true)][string]$Bucket,
        [Parameter(Mandatory = $true)][string]$Key,
        [string]$Region = 'us-east-1'
    )
    $response = Invoke-S3Request -Endpoint $Endpoint -AccessKey $AccessKey -SecretKey $SecretKey -Region $Region `
        -Bucket $Bucket -Key $Key -Method DELETE
    return $response.StatusCode
}

function Get-S3BucketPolicy {
    param(
        [Parameter(Mandatory = $true)][string]$Endpoint,
        [Parameter(Mandatory = $true)][string]$AccessKey,
        [Parameter(Mandatory = $true)][string]$SecretKey,
        [Parameter(Mandatory = $true)][string]$Bucket,
        [string]$Region = 'us-east-1'
    )
    $response = Invoke-S3Request -Endpoint $Endpoint -AccessKey $AccessKey -SecretKey $SecretKey -Region $Region `
        -Bucket $Bucket -Method GET -Query @{ policy = '' }
    if ($response.StatusCode -eq 200) { return $response.ContentText }
    return $null
}

function Set-S3BucketPolicy {
    param(
        [Parameter(Mandatory = $true)][string]$Endpoint,
        [Parameter(Mandatory = $true)][string]$AccessKey,
        [Parameter(Mandatory = $true)][string]$SecretKey,
        [Parameter(Mandatory = $true)][string]$Bucket,
        [Parameter(Mandatory = $true)][string]$PolicyFile,
        [string]$Region = 'us-east-1'
    )
    $body = [System.IO.File]::ReadAllBytes($PolicyFile)
    $response = Invoke-S3Request -Endpoint $Endpoint -AccessKey $AccessKey -SecretKey $SecretKey -Region $Region `
        -Bucket $Bucket -Method PUT -Query @{ policy = '' } -Body $body -ContentType 'application/json'
    return $response.StatusCode
}

function New-S3Bucket {
    param(
        [Parameter(Mandatory = $true)][string]$Endpoint,
        [Parameter(Mandatory = $true)][string]$AccessKey,
        [Parameter(Mandatory = $true)][string]$SecretKey,
        [Parameter(Mandatory = $true)][string]$Bucket,
        [string]$Region = 'us-east-1'
    )
    $response = Invoke-S3Request -Endpoint $Endpoint -AccessKey $AccessKey -SecretKey $SecretKey -Region $Region `
        -Bucket $Bucket -Method PUT
    # 200 = 创建成功；409 = 已存在（同样可用）
    return $response.StatusCode
}

function Resolve-McPath {
    <#
      .SYNOPSIS 找一个可用的 mc.exe：优先复用本机/其他项目里已经下载好的副本，都找不到再尝试下载。
      .DESCRIPTION 国内网络常常下载不到 mc，而同一台机器上其他项目（例如 CampusRepair 的离线迁移包）
                   往往已经附带一份，因此这里按候选列表依次探测。
    #>
    param(
        [string]$Preferred = '',
        [string[]]$ExtraCandidates = @()
    )
    $candidates = @()
    if ($Preferred) { $candidates += $Preferred }
    $candidates += $ExtraCandidates
    $candidates += @(
        (Join-Path $PSScriptRoot 'mc.exe'),
        (Join-Path $env:TEMP 'mc\mc.exe'),
        'F:\javaWeb\毕设接单\CampusRepair\backup\migration-bundle\mc.exe',
        (Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'CampusRepair\backup\migration-bundle\mc.exe')
    )
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate) -and (Get-Item $candidate).Length -gt 5MB) { return (Get-Item $candidate).FullName }
    }
    Write-Host '      未在本地找到 mc.exe，尝试从网络下载（约 30MB）...' -ForegroundColor Yellow
    $target = Join-Path $env:TEMP 'mc\mc.exe'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
    foreach ($url in @(
        'https://dl.min.io/client/mc/release/windows-amd64/mc.exe',
        'https://github.com/minio/mc/releases/download/RELEASE.2025-08-13T08-35-41Z/mc.windows-amd64.RELEASE.2025-08-13T08-35-41Z.exe'
    )) {
        & curl.exe -s -L -o $target $url --max-time 120
        if ((Test-Path $target) -and (Get-Item $target).Length -gt 5MB) { return $target }
    }
    return $null
}
