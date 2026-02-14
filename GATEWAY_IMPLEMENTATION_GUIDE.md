# API Gateway Implementation - Complete Guide

## 📋 Table of Contents
1. [Overview](#overview)
2. [What Was Implemented](#what-was-implemented)
3. [Architecture](#architecture)
4. [Configuration](#configuration)
5. [Java Classes](#java-classes)
6. [How Downstream Services Consume Headers](#how-downstream-services-consume-headers)
7. [Testing Guide](#testing-guide)
8. [Error Handling](#error-handling)

---

## 🎯 Overview

This API Gateway serves as the **SINGLE ENTRY POINT** for all traffic (external and internal) in the restaurant management microservices system. It enforces JWT-based authentication, role-based authorization, and injects standardized headers for downstream services.

### Key Features
- ✅ JWT validation for all protected routes
- ✅ Mandatory identity context (userId, tableId, role)
- ✅ Automatic header injection
- ✅ Correlation ID generation for request tracing
- ✅ Role-based path authorization
- ✅ Centralized error handling
- ✅ Request/response logging

---

## 🚀 What Was Implemented

### 1. **Dependencies Added** (pom.xml)
```xml
- Spring Cloud Gateway (Reactive)
- JJWT (JWT validation)
- Spring Boot Validation
- Lombok
```

### 2. **Configuration** (application.yaml)
- Server port: 8080
- JWT secret (matching auth-service)
- Route definitions for all microservices
- CORS configuration for frontend portals
- Public paths configuration

### 3. **Package Structure**
```
com.example.api_gateway/
├── config/               (Future: custom configurations)
├── dto/
│   └── ErrorResponse.java
├── exception/
│   ├── GlobalErrorHandler.java
│   ├── JwtAuthenticationException.java
│   ├── MissingClaimException.java
│   └── InsufficientPermissionsException.java
├── filter/
│   ├── CorrelationIdFilter.java (Order 1)
│   ├── LoggingFilter.java (Order 2)
│   ├── JwtAuthenticationFilter.java (Order 3)
│   ├── HeaderInjectionFilter.java (Order 4)
│   └── RoleAuthorizationFilter.java (Order 5)
└── security/
    └── JwtValidator.java
```

### 4. **Global Filters (Ordered Execution)**
1. **CorrelationIdFilter**: Generates or propagates X-Correlation-Id
2. **LoggingFilter**: Logs requests and responses with timing
3. **JwtAuthenticationFilter**: Validates JWT, extracts claims, validates tableId
4. **HeaderInjectionFilter**: Injects required headers to downstream
5. **RoleAuthorizationFilter**: Enforces role-based access control

---

## 🏗️ Architecture

### Request Flow
```
Client Request
    ↓
[1] CorrelationIdFilter → Generate/Extract Correlation ID
    ↓
[2] LoggingFilter → Log request details
    ↓
[3] JwtAuthenticationFilter → Validate JWT, extract userId/role, validate tableId
    ↓
[4] HeaderInjectionFilter → Inject headers (X-User-Id, X-Table-Id, X-Role, etc.)
    ↓
[5] RoleAuthorizationFilter → Check role-based access
    ↓
Route to Downstream Service
    ↓
[Response] LoggingFilter → Log response status and duration
    ↓
Client Response
```

### TableId Source Decision
**tableId** must be provided by the client in **EITHER**:
- **X-Table-Id header** (Priority 1)
- **tableId query parameter** (Priority 2)

This is required because JWT only contains userId and role. The frontend tracks the table assignment and sends it with every request.

---

## ⚙️ Configuration

### application.yaml
```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes:
        # Auth Service (Public)
        - id: auth-service
          uri: http://localhost:8081
          predicates:
            - Path=/api/auth/**

        # Menu Service
        - id: menu-service
          uri: http://localhost:8082
          predicates:
            - Path=/api/menu/**

        # Order Service
        - id: order-service
          uri: http://localhost:8083
          predicates:
            - Path=/api/orders/**

        # Analytics Service
        - id: analytics-service
          uri: http://localhost:8084
          predicates:
            - Path=/api/analytics/**

        # KDS Service
        - id: kds-service
          uri: http://localhost:8085
          predicates:
            - Path=/api/kds/**

      # CORS Configuration
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "http://localhost:3000"  # Customer Portal
              - "http://localhost:3001"  # Admin Portal
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - PATCH
              - OPTIONS
            allowedHeaders:
              - "*"
            exposedHeaders:
              - X-Correlation-Id
              - X-Service-Name
            allowCredentials: true
            maxAge: 3600

# JWT Configuration
jwt:
  secret: ue8yLJTAALbJn67rrh4lEGPLgl634dLEEIvRjVbemRFmgei9LggUKZjs/aSh9rvWGRBrze0Af5At6ywPsdO9+g==

# Public paths that don't require JWT (comma-separated)
gateway:
  public-paths: /api/auth/**
  # To add multiple paths, use comma separation:
  # public-paths: /api/auth/**,/api/health/**,/api/public/**
```

---

## 📦 Java Classes

### 1. JwtValidator.java
**Purpose**: Validates JWT tokens and extracts claims

**Key Methods**:
- `validateAndExtractClaims(String token)`: Validates JWT and returns claims
- `extractUserId(Claims claims)`: Extracts userId from subject
- `extractRole(Claims claims)`: Extracts role integer
- `getRoleName(Integer role)`: Converts role integer to string (CUSTOMER, ADMIN, KITCHEN)

### 2. JwtAuthenticationFilter.java
**Purpose**: Main authentication filter (Order 3)

**Key Logic**:
- Skips validation for public paths (`/api/auth/**`)
- Extracts JWT from `Authorization: Bearer <token>` header
- Validates JWT signature and expiration
- Extracts userId and role from JWT
- Extracts tableId from `X-Table-Id` header or `tableId` query param
- Stores userId, tableId, role in exchange attributes
- Throws exceptions for missing/invalid tokens or tableId

### 3. HeaderInjectionFilter.java
**Purpose**: Injects headers for downstream services (Order 4)

**Injected Headers**:
- `X-User-Id`: Long userId from JWT
- `X-Table-Id`: Long tableId from request
- `X-Role`: String role name (CUSTOMER, ADMIN, KITCHEN)
- `X-Service-Name`: "gateway"
- `X-Correlation-Id`: UUID for request tracing

### 4. RoleAuthorizationFilter.java
**Purpose**: Enforces role-based path access (Order 5)

**Access Rules**:
```java
/api/admin/**      → ADMIN only
/api/kds/**        → ADMIN, KITCHEN
/api/analytics/**  → ADMIN only
/api/orders/**     → All authenticated users
/api/menu/**       → All authenticated users
```

### 5. GlobalErrorHandler.java
**Purpose**: Centralizes error handling for the gateway

**Error Mappings**:
- `JwtAuthenticationException` → 401 Unauthorized
- `MissingClaimException` → 400 Bad Request
- `InsufficientPermissionsException` → 403 Forbidden
- All other exceptions → 500 Internal Server Error

**Error Response Format**:
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Token has expired",
  "path": "/api/orders",
  "timestamp": "2025-12-14T18:30:00",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

## 🔌 How Downstream Services Consume Headers

### Required Changes in Downstream Services

**Example from order-service/OrderController.java**:

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Table-Id") Long tableId,
            @RequestHeader("X-Role") String role,
            @RequestHeader("X-Correlation-Id") String correlationId,
            @RequestBody CreateOrderRequest request) {
        
        // userId and tableId are guaranteed to be present
        // Services can trust these headers blindly
        request.setUserId(userId);
        request.setTableId(tableId);
        
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.ok(response);
    }
}
```

### Header Contract (What Services Receive)

| Header            | Type   | Required | Description                          |
|-------------------|--------|----------|--------------------------------------|
| X-User-Id         | Long   | YES      | User ID from JWT                     |
| X-Table-Id        | Long   | YES      | Table ID from client request         |
| X-Role            | String | YES      | Role name (CUSTOMER/ADMIN/KITCHEN)   |
| X-Service-Name    | String | YES      | Always "gateway"                     |
| X-Correlation-Id  | String | YES      | UUID for request tracing             |
| Authorization     | String | YES      | Original JWT token (if needed)       |

### Important Notes for Services

1. **NEVER validate JWT in services** - Gateway already did this
2. **TRUST the headers** - They are injected by the gateway
3. **X-User-Id and X-Table-Id are NEVER null** - Gateway guarantees this
4. **Use X-Correlation-Id for logging** - Helps trace requests across services
5. **No direct service-to-service calls** - All traffic goes through gateway

---

## 🧪 Testing Guide

### 1. Start the Gateway
```bash
cd D:\MYW\UniversityWork\Fullstack\gateway
mvn spring-boot:run
```

Gateway will start on port **8080**

### 2. Test Public Endpoint (No JWT Required)
```bash
# Login to get JWT token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123"
  }'

# Response:
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
  "userId": 1,
  "role": 1
}
```

### 3. Test Protected Endpoint (JWT Required)
```bash
# Call menu service through gateway
curl -X GET "http://localhost:8080/api/menu/items?tableId=5" \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..." \
  -H "X-Table-Id: 5"

# Or use query parameter for tableId
curl -X GET "http://localhost:8080/api/menu/items?tableId=5" \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..."
```

### 4. Test Missing JWT (Should return 401)
```bash
curl -X GET "http://localhost:8080/api/orders" \
  -H "X-Table-Id: 5"

# Response:
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Missing or invalid Authorization header",
  "path": "/api/orders",
  "timestamp": "2025-12-14T18:30:00",
  "correlationId": "..."
}
```

### 5. Test Missing TableId (Should return 400)
```bash
curl -X GET "http://localhost:8080/api/orders" \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..."

# Response:
{
  "status": 400,
  "error": "Bad Request",
  "message": "X-Table-Id header or tableId query parameter is required",
  "path": "/api/orders",
  "timestamp": "2025-12-14T18:30:00",
  "correlationId": "..."
}
```

### 6. Test Role-Based Access (Admin Only Route)
```bash
# Try to access admin route with CUSTOMER role (should return 403)
curl -X GET "http://localhost:8080/api/admin/users" \
  -H "Authorization: Bearer <customer_token>" \
  -H "X-Table-Id: 5"

# Response:
{
  "status": 403,
  "error": "Forbidden",
  "message": "Role CUSTOMER is not authorized to access this resource",
  "path": "/api/admin/users",
  "timestamp": "2025-12-14T18:30:00",
  "correlationId": "..."
}
```

### 7. Verify Headers Injected to Downstream
Enable debug logging in downstream service to see injected headers:
```yaml
logging:
  level:
    org.springframework.web: DEBUG
```

You should see:
```
X-User-Id: 1
X-Table-Id: 5
X-Role: CUSTOMER
X-Service-Name: gateway
X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440000
```

### 8. Test Correlation ID Tracing
```bash
# Send request with existing correlation ID
curl -X GET "http://localhost:8080/api/orders?tableId=5" \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..." \
  -H "X-Correlation-Id: my-custom-correlation-id"

# Check gateway logs - should use your correlation ID
# Check downstream service logs - should propagate same correlation ID
```

---

## 🛡️ Error Handling

### HTTP Status Codes

| Status | Error Type                      | Cause                                    |
|--------|---------------------------------|------------------------------------------|
| 400    | Bad Request                     | Missing required header (tableId)        |
| 401    | Unauthorized                    | Missing/invalid/expired JWT              |
| 403    | Forbidden                       | Insufficient permissions for path        |
| 500    | Internal Server Error           | Unexpected error                         |

### Common Errors and Solutions

#### 1. "Missing or invalid Authorization header"
**Cause**: No JWT token provided or incorrect format
**Solution**: Send JWT as `Authorization: Bearer <token>`

#### 2. "Token has expired"
**Cause**: JWT access token expired (15 min by default)
**Solution**: Use refresh token to get new access token

#### 3. "X-Table-Id header or tableId query parameter is required"
**Cause**: tableId not provided in request
**Solution**: Add `X-Table-Id: <tableId>` header or `?tableId=<tableId>` query param

#### 4. "JWT subject (userId) is missing"
**Cause**: JWT generated without userId
**Solution**: Check auth-service JWT generation logic

#### 5. "Role <ROLE> is not authorized to access this resource"
**Cause**: User role doesn't have access to this path
**Solution**: Login with appropriate role or contact admin

---

## 📊 Monitoring and Debugging

### Check Gateway Logs
```bash
# Gateway logs show:
# - Incoming requests with correlation ID
# - JWT validation results
# - Header injection
# - Response status and duration

# Example log output:
[550e8400...] GET /api/orders - Started
JWT validated successfully for userId: 1
TableId extracted: 5
Injected headers - X-User-Id: 1, X-Table-Id: 5, X-Role: CUSTOMER
Role CUSTOMER authorized for path /api/orders
[550e8400...] GET /api/orders - Completed with status 200 in 125ms
```

### Enable Debug Logging
Add to `application.yaml`:
```yaml
logging:
  level:
    org.springframework.cloud.gateway: DEBUG
    com.example.api_gateway: DEBUG
```

---

## 🔧 Future Enhancements

1. **Service Discovery**: Replace hardcoded URLs with Eureka/Consul
2. **Rate Limiting**: Add Redis-based rate limiting per user/IP
3. **Circuit Breaker**: Implement Resilience4j for fault tolerance
4. **Metrics**: Add Prometheus metrics for monitoring
5. **API Documentation**: Integrate Swagger/OpenAPI at gateway level
6. **Request Validation**: Add request size limits and validation

---

## 📝 Summary

### What Gateway Does
✅ Validates JWT on every protected request  
✅ Extracts userId and role from JWT  
✅ Validates tableId from client request  
✅ Injects standardized headers to downstream services  
✅ Enforces role-based path authorization  
✅ Generates correlation IDs for request tracing  
✅ Logs all requests and responses  
✅ Handles errors consistently  

### What Downstream Services Do
✅ Trust headers from gateway blindly  
✅ No JWT validation (gateway did it)  
✅ Use X-User-Id, X-Table-Id, X-Role for business logic  
✅ Use X-Correlation-Id for logging  
✅ NEVER accept direct requests (only through gateway)  

### Critical Rules
🚨 ALL traffic MUST go through gateway  
🚨 JWT is REQUIRED for all routes except /api/auth/**  
🚨 tableId is MANDATORY for all protected routes  
🚨 userId and tableId are NEVER null in downstream services  
🚨 NO direct service-to-service communication  

---

## 🎉 Gateway is Ready!

The API Gateway is fully implemented and operational. Start the gateway on port 8080 and route all client requests through it.

**Next Steps**:
1. Start all backend services (auth-service, menu-service, order-service, etc.)
2. Start the API Gateway
3. Update frontend to call gateway at http://localhost:8080 instead of direct service URLs
4. Test end-to-end flow with real JWT tokens

