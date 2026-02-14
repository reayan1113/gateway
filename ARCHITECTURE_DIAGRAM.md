# API Gateway Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENT REQUEST                                  │
│                                                                              │
│   POST http://localhost:8080/api/orders                                     │
│   Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...                              │
│   X-Table-Id: 5                                                              │
│   Content-Type: application/json                                             │
│   Body: {...}                                                                │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         API GATEWAY (Port 8080)                              │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  FILTER CHAIN (Ordered Execution)                                      │ │
│  │                                                                         │ │
│  │  [1] CorrelationIdFilter                                               │ │
│  │      • Generate UUID if X-Correlation-Id missing                       │ │
│  │      • Store in exchange attributes                                    │ │
│  │      • Add to response headers                                         │ │
│  │      Result: correlationId = "550e8400..."                             │ │
│  │                                                                         │ │
│  │  [2] LoggingFilter                                                     │ │
│  │      • Log: [550e8400...] POST /api/orders - Started                   │ │
│  │      • Track start time                                                │ │
│  │      • On response: log status and duration                            │ │
│  │                                                                         │ │
│  │  [3] JwtAuthenticationFilter                                           │ │
│  │      • Check if path is public (/api/auth/**)                          │ │
│  │      • If public → skip JWT validation                                 │ │
│  │      • If protected:                                                   │ │
│  │        - Extract JWT from "Authorization: Bearer <token>"              │ │
│  │        - Validate signature (HS512) and expiration                     │ │
│  │        - Extract userId from JWT subject                               │ │
│  │        - Extract role from JWT claims                                  │ │
│  │        - Extract tableId from X-Table-Id header or query param         │ │
│  │        - Validate all claims are present                               │ │
│  │      • Store in exchange attributes:                                   │ │
│  │        - userId = 1                                                    │ │
│  │        - tableId = 5                                                   │ │
│  │        - role = 1                                                      │ │
│  │        - roleName = "CUSTOMER"                                         │ │
│  │      • Throw exception if validation fails                             │ │
│  │                                                                         │ │
│  │  [4] HeaderInjectionFilter                                             │ │
│  │      • Read from exchange attributes:                                  │ │
│  │        - userId, tableId, roleName, correlationId                      │ │
│  │      • Inject headers into downstream request:                         │ │
│  │        - X-User-Id: 1                                                  │ │
│  │        - X-Table-Id: 5                                                 │ │
│  │        - X-Role: CUSTOMER                                              │ │
│  │        - X-Service-Name: gateway                                       │ │
│  │        - X-Correlation-Id: 550e8400...                                 │ │
│  │      • Keep original Authorization header                              │ │
│  │                                                                         │ │
│  │  [5] RoleAuthorizationFilter                                           │ │
│  │      • Check path against role rules:                                  │ │
│  │        - /api/admin/** → ADMIN only                                    │ │
│  │        - /api/kds/** → ADMIN, KITCHEN                                  │ │
│  │        - /api/analytics/** → ADMIN only                                │ │
│  │        - /api/orders/** → All authenticated                            │ │
│  │      • If role not allowed → throw InsufficientPermissionsException    │ │
│  │      • Result: CUSTOMER allowed for /api/orders/**                     │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │  ROUTE RESOLVER                                                        │ │
│  │                                                                         │ │
│  │  Path: /api/orders                                                     │ │
│  │  Match: /api/orders/**                                                 │ │
│  │  Route ID: order-service                                               │ │
│  │  Target URI: http://localhost:8083                                     │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      DOWNSTREAM REQUEST                                      │
│                                                                              │
│   POST http://localhost:8083/api/orders                                     │
│   Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...                              │
│   X-User-Id: 1                                                               │
│   X-Table-Id: 5                                                              │
│   X-Role: CUSTOMER                                                           │
│   X-Service-Name: gateway                                                    │
│   X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440000                     │
│   Content-Type: application/json                                             │
│   Body: {...}                                                                │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ORDER SERVICE (Port 8083)                                 │
│                                                                              │
│  @PostMapping("/api/orders")                                                 │
│  public ResponseEntity<OrderResponse> createOrder(                           │
│      @RequestHeader("X-User-Id") Long userId,          // 1                  │
│      @RequestHeader("X-Table-Id") Long tableId,        // 5                  │
│      @RequestHeader("X-Role") String role,             // CUSTOMER           │
│      @RequestHeader("X-Correlation-Id") String correlationId,                │
│      @RequestBody CreateOrderRequest request) {                              │
│                                                                              │
│      // No JWT validation needed!                                            │
│      // Gateway already validated everything                                 │
│      // userId and tableId are guaranteed non-null                           │
│                                                                              │
│      request.setUserId(userId);                                              │
│      request.setTableId(tableId);                                            │
│                                                                              │
│      OrderResponse response = orderService.createOrder(request);             │
│      return ResponseEntity.ok(response);                                     │
│  }                                                                            │
│                                                                              │
│  Response: { "orderId": 123, "status": "PENDING", ... }                      │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         RESPONSE TO CLIENT                                   │
│                                                                              │
│   HTTP 200 OK                                                                │
│   X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440000                     │
│   Content-Type: application/json                                             │
│                                                                              │
│   { "orderId": 123, "status": "PENDING", ... }                               │
│                                                                              │
│   Gateway Log: [550e8400...] POST /api/orders - Completed with status 200   │
│                in 125ms                                                      │
└─────────────────────────────────────────────────────────────────────────────┘


═══════════════════════════════════════════════════════════════════════════════
                              ERROR SCENARIOS
═══════════════════════════════════════════════════════════════════════════════

SCENARIO 1: Missing JWT
─────────────────────────
Client Request:
  POST http://localhost:8080/api/orders
  X-Table-Id: 5
  (No Authorization header)

Gateway Response:
  HTTP 401 Unauthorized
  {
    "status": 401,
    "error": "Unauthorized",
    "message": "Missing or invalid Authorization header",
    "path": "/api/orders",
    "timestamp": "2025-12-14T18:30:00",
    "correlationId": "550e8400..."
  }

─────────────────────────

SCENARIO 2: Missing tableId
─────────────────────────
Client Request:
  POST http://localhost:8080/api/orders
  Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
  (No X-Table-Id header or query param)

Gateway Response:
  HTTP 400 Bad Request
  {
    "status": 400,
    "error": "Bad Request",
    "message": "X-Table-Id header or tableId query parameter is required",
    "path": "/api/orders",
    "timestamp": "2025-12-14T18:30:00",
    "correlationId": "550e8400..."
  }

─────────────────────────

SCENARIO 3: Insufficient Permissions
─────────────────────────
Client Request:
  GET http://localhost:8080/api/admin/users
  Authorization: Bearer <customer-jwt>
  X-Table-Id: 5

Gateway Response:
  HTTP 403 Forbidden
  {
    "status": 403,
    "error": "Forbidden",
    "message": "Role CUSTOMER is not authorized to access this resource",
    "path": "/api/admin/users",
    "timestamp": "2025-12-14T18:30:00",
    "correlationId": "550e8400..."
  }

═══════════════════════════════════════════════════════════════════════════════


═══════════════════════════════════════════════════════════════════════════════
                           SERVICE ROUTING TABLE
═══════════════════════════════════════════════════════════════════════════════

┌─────────────────────┬──────────┬──────────────────────┬─────────────────────┐
│ Service             │ Port     │ Gateway Route        │ Public/Protected    │
├─────────────────────┼──────────┼──────────────────────┼─────────────────────┤
│ Auth Service        │ 8081     │ /api/auth/**         │ Public              │
│ Menu Service        │ 8082     │ /api/menu/**         │ Protected           │
│ Order Service       │ 8083     │ /api/orders/**       │ Protected           │
│ Analytics Service   │ 8084     │ /api/analytics/**    │ Protected (ADMIN)   │
│ KDS Service         │ 8085     │ /api/kds/**          │ Protected (ADMIN,   │
│                     │          │                      │  KITCHEN)           │
└─────────────────────┴──────────┴──────────────────────┴─────────────────────┘

═══════════════════════════════════════════════════════════════════════════════


═══════════════════════════════════════════════════════════════════════════════
                         ROLE-BASED ACCESS CONTROL
═══════════════════════════════════════════════════════════════════════════════

┌──────────────────────┬─────────────────────────────────────────────────────┐
│ Path Pattern         │ Allowed Roles                                       │
├──────────────────────┼─────────────────────────────────────────────────────┤
│ /api/auth/**         │ PUBLIC (no authentication)                          │
│ /api/menu/**         │ CUSTOMER, ADMIN, KITCHEN (all authenticated users) │
│ /api/orders/**       │ CUSTOMER, ADMIN, KITCHEN (all authenticated users) │
│ /api/admin/**        │ ADMIN only                                          │
│ /api/kds/**          │ ADMIN, KITCHEN                                      │
│ /api/analytics/**    │ ADMIN only                                          │
└──────────────────────┴─────────────────────────────────────────────────────┘

═══════════════════════════════════════════════════════════════════════════════


═══════════════════════════════════════════════════════════════════════════════
                             JWT STRUCTURE
═══════════════════════════════════════════════════════════════════════════════

Header:
{
  "alg": "HS512",
  "typ": "JWT"
}

Payload (Claims):
{
  "sub": "1",                    // userId (subject)
  "role": 1,                     // role (1=CUSTOMER, 2=ADMIN, 3=KITCHEN)
  "iat": 1702572600,             // issued at
  "exp": 1702573500              // expires (15 min later)
}

Signature:
HMACSHA512(
  base64UrlEncode(header) + "." + base64UrlEncode(payload),
  secret
)

Secret: ue8yLJTAALbJn67rrh4lEGPLgl634dLEEIvRjVbemRFmgei9LggUKZjs/aSh9rvWGRBrze0Af5At6ywPsdO9+g==

═══════════════════════════════════════════════════════════════════════════════
```

