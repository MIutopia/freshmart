# FreshMart end-to-end smoke test (writes test data into the local dev database)
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File work/e2e-smoke.ps1
# NOTE: keep this file ASCII-only; Windows PowerShell reads .ps1 as ANSI/GBK otherwise.

$base = 'http://127.0.0.1:8080'
$password = 'password'
$script:passed = 0
$script:failed = 0

function Report([string]$name, [bool]$ok, [string]$detail) {
    if ($ok) {
        $script:passed++
        Write-Host ("PASS  " + $name) -ForegroundColor Green
    } else {
        $script:failed++
        Write-Host ("FAIL  " + $name + "  => " + $detail) -ForegroundColor Red
    }
}

function Invoke-Step([string]$name, [scriptblock]$action) {
    try {
        $value = & $action
        Report $name $true ''
        return $value
    } catch {
        $status = ''
        try { $status = $_.Exception.Response.StatusCode.value__ } catch { }
        Report $name $false ($status.ToString() + ' ' + $_.Exception.Message)
        return $null
    }
}

# 反向用例：断言请求必须被拒绝（例如停用仍有在架商品的分类）
function Invoke-ExpectFailure([string]$name, [scriptblock]$action) {
    try {
        & $action | Out-Null
        Report $name $false 'expected rejection but request succeeded'
    } catch {
        Report $name $true ''
    }
}

function Login([string]$loginName) {
    $body = @{ loginName = $loginName; password = $password } | ConvertTo-Json
    return (Invoke-RestMethod "$base/api/auth/login" -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 20).accessToken
}

function ApiGet([string]$path, [string]$token) {
    return Invoke-RestMethod "$base$path" -Headers @{ Authorization = "Bearer $token" } -TimeoutSec 20
}

function ApiSend([string]$method, [string]$path, [string]$token, $body) {
    $headers = @{ Authorization = "Bearer $token"; 'Idempotency-Key' = [guid]::NewGuid().ToString() }
    if ($null -eq $body) {
        return Invoke-RestMethod "$base$path" -Method $method -Headers $headers -TimeoutSec 20
    }
    $json = $body | ConvertTo-Json -Depth 8
    return Invoke-RestMethod "$base$path" -Method $method -Headers $headers -ContentType 'application/json' -Body $json -TimeoutSec 20
}

function Today([int]$offsetDays) {
    return (Get-Date).AddDays($offsetDays).ToString('yyyy-MM-dd')
}

# 用 .NET HttpClient 上传，兼容 Windows PowerShell 5.1（其 Invoke-RestMethod 没有 -Form 参数）
function UploadMedia([string]$filePath, [string]$token) {
    Add-Type -AssemblyName System.Net.Http -ErrorAction SilentlyContinue
    $client = New-Object System.Net.Http.HttpClient
    $client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $token)
    $content = New-Object System.Net.Http.MultipartFormDataContent
    $bytes = [IO.File]::ReadAllBytes($filePath)
    $fileContent = [System.Net.Http.ByteArrayContent]::new($bytes)
    $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('image/png')
    $content.Add($fileContent, 'file', [IO.Path]::GetFileName($filePath))
    $response = $client.PostAsync("$base/api/media/upload", $content).Result
    $text = $response.Content.ReadAsStringAsync().Result
    $client.Dispose()
    if (-not $response.IsSuccessStatusCode) {
        throw ('upload failed: ' + [int]$response.StatusCode + ' ' + $text)
    }
    return ($text | ConvertFrom-Json)
}

Write-Host '===== 0. health and login =====' -ForegroundColor Cyan
$health = Invoke-Step 'health endpoint' { (Invoke-RestMethod "$base/actuator/health" -TimeoutSec 10).status }
if ($health -ne 'UP') {
    Write-Host 'backend not ready, abort.' -ForegroundColor Red
    exit 1
}

$adminToken = Invoke-Step 'login admin-test-01' { Login 'admin-test-01' }
$merchantToken = Invoke-Step 'login merchant-test-01' { Login 'merchant-test-01' }
$consumerToken = Invoke-Step 'login consumer-test-01' { Login 'consumer-test-01' }
$riderToken = Invoke-Step 'login rider-test-01' { Login 'rider-test-01' }
if (-not $adminToken -or -not $merchantToken -or -not $consumerToken -or -not $riderToken) {
    Write-Host 'login failed, abort.' -ForegroundColor Red
    exit 1
}

$riderUserId = (ApiGet '/api/auth/me' $riderToken).userId
Write-Host ("riderUserId = " + $riderUserId) -ForegroundColor DarkGray

Write-Host '===== 1. platform config (admin) =====' -ForegroundColor Cyan
$stamp = Get-Date -Format 'HHmmss'
$category = Invoke-Step 'create category' { ApiSend 'POST' '/api/admin/categories' $adminToken @{ name = "E2E-Category-$stamp"; sortOrder = 1 } }
$categoryRenamed = Invoke-Step 'update category' { ApiSend 'PUT' ("/api/admin/categories/" + $category.id) $adminToken @{ name = "E2E-Category-$stamp-v2"; sortOrder = 3; status = 'ACTIVE' } }
Report 'category rename persisted' ($categoryRenamed.name -eq "E2E-Category-$stamp-v2") 'name not updated'
Invoke-Step 'deactivate category without products' { ApiSend 'PUT' ("/api/admin/categories/" + $category.id) $adminToken @{ name = "E2E-Category-$stamp-v2"; sortOrder = 3; status = 'INACTIVE' } } | Out-Null
Invoke-Step 'reactivate category' { ApiSend 'PUT' ("/api/admin/categories/" + $category.id) $adminToken @{ name = "E2E-Category-$stamp-v2"; sortOrder = 3; status = 'ACTIVE' } } | Out-Null
$zone = Invoke-Step 'create delivery zone' { ApiSend 'POST' '/api/admin/delivery-zones' $adminToken @{ name = "E2E-Zone-$stamp"; areaCode = "E2E-$stamp" } }
$categories = Invoke-Step 'list categories' { ApiGet '/api/admin/categories' $adminToken }
$zones = Invoke-Step 'list delivery zones' { ApiGet '/api/admin/delivery-zones' $adminToken }
$rules = Invoke-Step 'list platform rules' { ApiGet '/api/admin/platform-rules' $adminToken }
$dashboard = Invoke-Step 'admin dashboard' { ApiGet '/api/admin/dashboard' $adminToken }
Write-Host ("categoryId=" + $category.id + " deliveryZoneId=" + $zone.id + " rules=" + $rules.Count + " zones=" + $zones.Count) -ForegroundColor DarkGray

Write-Host '===== 2. merchant setup =====' -ForegroundColor Cyan
$warehouse = Invoke-Step 'create warehouse' { ApiSend 'POST' '/api/merchant/catalog/warehouses' $merchantToken @{ deliveryZoneId = $zone.id; name = "E2E-Warehouse-$stamp"; code = "E2E-WH-$stamp"; address = 'E2E address 1' } }
$warehouses = Invoke-Step 'list merchant warehouses' { ApiGet '/api/merchant/warehouses' $merchantToken }
$allow = Invoke-Step 'allow warehouse category' { ApiSend 'POST' '/api/merchant/catalog/warehouse-categories' $merchantToken @{ warehouseId = $warehouse.id; categoryId = $category.id } }
$rule = Invoke-Step 'create warehouse rule' { ApiSend 'POST' '/api/merchant/catalog/warehouse-rules' $merchantToken @{ warehouseId = $warehouse.id; categoryId = $category.id; priority = 1 } }
$product = Invoke-Step 'create product' { ApiSend 'POST' '/api/merchant/catalog/products' $merchantToken @{ categoryId = $category.id; name = "E2E-Tomato-$stamp"; description = 'E2E product'; marketPricePerKg = 10.00; merchantPricePerKg = 9.50 } }
$publish = Invoke-Step 'publish product' { ApiSend 'PUT' ("/api/merchant/catalog/products/" + $product.id + "/publish") $merchantToken $null }
$batch = Invoke-Step 'create batch' { ApiSend 'POST' '/api/merchant/catalog/batches' $merchantToken @{ productId = $product.id; warehouseId = $warehouse.id; batchNo = "E2E-B-$stamp"; availableGrams = 20000; expiresOn = (Today 10) } }
$merchantProducts = Invoke-Step 'list merchant products' { ApiGet '/api/merchant/catalog/products' $merchantToken }
$merchantDashboard = Invoke-Step 'merchant dashboard' { ApiGet ("/api/merchant/dashboard?from=" + (Today -30) + "&to=" + (Today 0)) $merchantToken }
Write-Host ("warehouseId=" + $warehouse.id + " productId=" + $product.id + " batchId=" + $batch.id) -ForegroundColor DarkGray

Write-Host '===== 3. consumer checkout =====' -ForegroundColor Cyan
$evidenceUrl = $null
# 现场生成一张 1x1 PNG 作为上传源，脚本自包含、不依赖仓库里的图片
$uploadSource = Join-Path $env:TEMP 'freshmart-e2e-proof.png'
[IO.File]::WriteAllBytes($uploadSource, [Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='))
$uploaded = Invoke-Step 'upload payment proof' { UploadMedia $uploadSource $consumerToken }
if ($uploaded) { $evidenceUrl = $uploaded.url }
if (-not $evidenceUrl) { Report 'upload payment proof' $false 'upload returned no url' }

$publicProducts = Invoke-Step 'public product list' { ApiGet '/api/catalog/products' $consumerToken }
$hit = @($publicProducts | Where-Object { $_.id -eq $product.id }).Count
Report 'product visible in public catalog' ($hit -gt 0) 'new product not listed'

$trade = Invoke-Step 'create trade' {
    ApiSend 'POST' '/api/trades' $consumerToken @{
        deliveryZoneId = $zone.id
        addressSnapshot = @{ contactName = 'E2E Receiver'; phone = '13800000000'; detail = 'E2E building 1-101' }
        lines = @(@{ productId = $product.id; weightGrams = 1000 })
        pointsToRedeem = 0
    }
}
Write-Host ("tradeNo=" + $trade.tradeNo + " payable=" + $trade.payableAmount) -ForegroundColor DarkGray

$prepay = Invoke-Step 'prepay' { ApiSend 'POST' ("/api/payments/" + $trade.tradeNo + "/prepay") $consumerToken $null }
$proof = Invoke-Step 'submit payment proof' { ApiSend 'POST' ("/api/payments/" + $trade.tradeNo + "/proof") $consumerToken @{ proofUrl = $evidenceUrl; remarkText = $trade.tradeNo } }
$confirmed = Invoke-Step 'confirm qr payment (finance)' { ApiSend 'POST' ("/api/admin/payments/" + $trade.tradeNo + "/confirm-personal-wechat-qr") $adminToken $null }

$orders = Invoke-Step 'consumer order list' { ApiGet '/api/orders' $consumerToken }
$newOrder = $orders | Where-Object { $trade.orderNos -contains $_.orderNo } | Select-Object -First 1
Report 'new order created' ($null -ne $newOrder) 'no order matched trade orderNos'
if ($newOrder) {
    Write-Host ("orderId=" + $newOrder.id + " status=" + $newOrder.status) -ForegroundColor DarkGray
    Invoke-Step 'order detail' { ApiGet ("/api/orders/" + $newOrder.id) $consumerToken } | Out-Null
    Invoke-Step 'order receipt' { ApiGet ("/api/orders/" + $newOrder.id + "/receipt") $consumerToken } | Out-Null
    Invoke-Step 'order traceability' { ApiGet ("/api/orders/" + $newOrder.id + "/traceability") $consumerToken } | Out-Null
    Invoke-Step 'merchant order list' { ApiGet '/api/orders' $merchantToken } | Out-Null
}

Write-Host '===== 4. delivery fulfillment =====' -ForegroundColor Cyan
$dispatchTasks = Invoke-Step 'admin delivery task list' { ApiGet '/api/admin/delivery-tasks' $adminToken }
$target = $null
if ($newOrder) { $target = $dispatchTasks | Where-Object { $_.orderId -eq $newOrder.id } | Select-Object -First 1 }
if (-not $target) { $target = $dispatchTasks | Where-Object { $_.status -eq 'WAITING_ASSIGNMENT' } | Select-Object -First 1 }
Report 'delivery task available' ($null -ne $target) 'no delivery task found'

if ($target) {
    Write-Host ("taskId=" + $target.id + " status=" + $target.status) -ForegroundColor DarkGray
    Invoke-Step 'assign delivery task' { ApiSend 'POST' ("/api/admin/delivery-tasks/" + $target.id + "/assign") $adminToken @{ riderUserId = $riderUserId } } | Out-Null
    $riderTasks = Invoke-Step 'rider task list' { ApiGet '/api/delivery/tasks' $riderToken }
    $assigned = @($riderTasks | Where-Object { $_.id -eq $target.id -and $_.riderUserId -eq $riderUserId }).Count
    Report 'task shows riderUserId after assign' ($assigned -gt 0) 'riderUserId missing in rider task list'
    Invoke-Step 'rider accept' { ApiSend 'PUT' ("/api/delivery/tasks/" + $target.id + "/accept") $riderToken $null } | Out-Null
    Invoke-Step 'rider pick' { ApiSend 'PUT' ("/api/delivery/tasks/" + $target.id + "/pick") $riderToken $null } | Out-Null
    Invoke-Step 'rider deliver' { ApiSend 'PUT' ("/api/delivery/tasks/" + $target.id + "/deliver") $riderToken @{ proofUrl = $evidenceUrl } } | Out-Null
    Invoke-Step 'rider performance' { ApiGet ("/api/delivery/performance?from=" + (Today -30) + "&to=" + (Today 0)) $riderToken } | Out-Null
}

Write-Host '===== 5. after-sale refund =====' -ForegroundColor Cyan
$deliveredOrder = $newOrder
if ($deliveredOrder) {
    $refund = Invoke-Step 'apply refund' {
        ApiSend 'POST' '/api/refunds' $consumerToken @{
            orderId = $deliveredOrder.id
            issueType = 'QUALITY'
            description = 'E2E smoke: quality issue description'
            evidenceImages = @($evidenceUrl)
        }
    }
    if ($refund) {
        Write-Host ("refundNo=" + $refund.refundNo + " status=" + $refund.status) -ForegroundColor DarkGray
        $myRefunds = Invoke-Step 'consumer refund list' { ApiGet '/api/refunds' $consumerToken }
        $adminRefunds = Invoke-Step 'admin refund list' { ApiGet '/api/admin/refunds' $adminToken }
        Report 'refund appears in consumer list' (@($myRefunds | Where-Object { $_.refundNo -eq $refund.refundNo }).Count -gt 0) 'refund not found'
        Report 'refund appears in admin list' (@($adminRefunds | Where-Object { $_.refundNo -eq $refund.refundNo }).Count -gt 0) 'refund not found in admin list'
        Invoke-Step 'refund ai suggestion' { ApiSend 'POST' ("/api/admin/refunds/" + $refund.refundNo + "/ai-review-suggestion") $adminToken $null } | Out-Null
        Invoke-Step 'review refund' { ApiSend 'PUT' ("/api/admin/refunds/" + $refund.id + "/review") $adminToken @{ approved = $true; reviewNote = 'E2E review approved' } } | Out-Null
    }
}

# 此时该分类下已有在架商品，停用必须被拒绝
Invoke-ExpectFailure 'deactivate category with active products is rejected' {
    ApiSend 'PUT' ("/api/admin/categories/" + $category.id) $adminToken @{ name = "E2E-Category-$stamp-v2"; sortOrder = 3; status = 'INACTIVE' }
}

Write-Host '===== 6. settlement and operations =====' -ForegroundColor Cyan
Invoke-Step 'generate commissions' { ApiSend 'POST' '/api/admin/commissions/generate' $adminToken $null } | Out-Null
$settlements = Invoke-Step 'list commissions' { ApiGet '/api/admin/commissions' $adminToken }
$pending = $settlements | Where-Object { $_.status -ne 'SETTLED' -and $_.status -ne 'REVERSED' } | Select-Object -First 1
if ($pending) {
    Invoke-Step 'confirm settlement' { ApiSend 'PUT' ("/api/admin/commissions/" + $pending.id + "/confirm") $adminToken @{ note = 'E2E settlement confirmed' } } | Out-Null
} else {
    Write-Host 'SKIP  confirm settlement (no pending settlement)' -ForegroundColor DarkYellow
}
Invoke-Step 'platform costs' { ApiGet ("/api/admin/platform-costs?from=" + (Today -30) + "&to=" + (Today 0)) $adminToken } | Out-Null
Invoke-Step 'reconciliation list' { ApiGet '/api/admin/payments/reconciliation-differences?status=UNHANDLED' $adminToken } | Out-Null
Invoke-Step 'batch promotion candidates' { ApiGet '/api/admin/marketing/batch-promotions/candidates' $adminToken } | Out-Null
Invoke-Step 'merchant settlements' { ApiGet '/api/merchant/settlements' $merchantToken } | Out-Null

Write-Host '===== 7. notification and inbox =====' -ForegroundColor Cyan
Invoke-Step 'notification preference get' { ApiGet '/api/notification-preferences' $consumerToken } | Out-Null
Invoke-Step 'notification preference update' { ApiSend 'PUT' '/api/notification-preferences' $consumerToken @{ seasonalCardEnabled = $true } } | Out-Null
Invoke-Step 'inbox list' { ApiGet '/api/inbox/messages' $consumerToken } | Out-Null
$card = Invoke-Step 'create holiday card task' {
    ApiSend 'POST' '/api/admin/notification/holiday-card-tasks' $adminToken @{
        taskKey = "e2e-$stamp"
        holidayKey = 'MID_AUTUMN'
        greeting = 'E2E greeting'
        scheduledAt = (Get-Date).AddHours(1).ToString('yyyy-MM-ddTHH:mm:ss')
    }
}
Invoke-Step 'holiday card task list' { ApiGet '/api/admin/notification/holiday-card-tasks' $adminToken } | Out-Null
if ($card -and $card.id) {
    Invoke-Step 'run holiday card task' { ApiSend 'POST' ("/api/admin/notification/holiday-card-tasks/" + $card.id + "/run") $adminToken $null } | Out-Null
}

Write-Host '===== 8. AI assistant and logout =====' -ForegroundColor Cyan
Invoke-Step 'ai assistant' { ApiSend 'POST' '/api/ai/assistant/messages' $consumerToken @{ message = 'recommend a vegetable for salad' } } | Out-Null
Invoke-Step 'logout consumer' { ApiSend 'POST' '/api/auth/logout' $consumerToken $null } | Out-Null

Write-Host ''
Write-Host '================ summary ================' -ForegroundColor Cyan
Write-Host ("passed: " + $script:passed) -ForegroundColor Green
if ($script:failed -gt 0) {
    Write-Host ("failed: " + $script:failed) -ForegroundColor Red
} else {
    Write-Host 'failed: 0' -ForegroundColor Green
}
exit $script:failed
