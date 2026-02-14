# API Gateway - Quick Start

## 🚀 Start the Gateway

```bash
cd D:\MYW\UniversityWork\Fullstack\gateway
mvn spring-boot:run
```

Gateway runs on **http://localhost:8080**

---

## 📋 Service Ports

| Service          | Port | Gateway Route       |
|------------------|------|---------------------|
| API Gateway      | 8080 | -                   |
| Auth Service     | 8081 | /api/auth/**        |
| Menu Service     | 8082 | /api/menu/**        |
| Order Service    | 8083 | /api/orders/**      |
| Analytics Service| 8084 | /api/analytics/**   |
| KDS Service      | 8085 | /api/kds/**         |

---

## 🔑 Quick Test Flow

### 1. Login (Get JWT)
```bash
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}

# Save the accessToken from response
```

### 2. Call Protected Endpoint
```bash
GET http://localhost:8080/api/orders?tableId=5
Authorization: Bearer <your-jwt-token>
X-Table-Id: 5
```

---

## 📦 Required Headers for Protected Routes

| Header            | Required | Example                        |
|-------------------|----------|--------------------------------|
| Authorization     | YES      | Bearer eyJhbGciOiJIUzUxMiJ9... |
| X-Table-Id        | YES      | 5                              |

**OR** use query parameter: `?tableId=5`

---

## 🎯 Public Routes (No JWT Required)

- `/api/auth/login`
- `/api/auth/register`
- `/api/auth/refresh`
- `/api/auth/oauth/**`

---

## 🛡️ Role-Based Access

| Path              | Allowed Roles          |
|-------------------|------------------------|
| /api/admin/**     | ADMIN                  |
| /api/kds/**       | ADMIN, KITCHEN         |
| /api/analytics/** | ADMIN                  |
| /api/orders/**    | All authenticated      |
| /api/menu/**      | All authenticated      |

---

## 🔍 Headers Injected to Downstream Services

Your services will automatically receive:

```
X-User-Id: 1
X-Table-Id: 5
X-Role: CUSTOMER
X-Service-Name: gateway
X-Correlation-Id: 550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer <original-token>
```

---

## ❌ Common Errors

### 401 Unauthorized
**Cause**: Missing or invalid JWT  
**Fix**: Include valid JWT in Authorization header

### 400 Bad Request
**Cause**: Missing tableId  
**Fix**: Add X-Table-Id header or tableId query param

### 403 Forbidden
**Cause**: Insufficient role permissions  
**Fix**: Login with appropriate role

---

## 📝 Downstream Service Code Example

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    @GetMapping
    public ResponseEntity<List<Order>> getOrders(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Table-Id") Long tableId,
            @RequestHeader("X-Role") String role) {
        
        // No need to validate JWT - gateway already did
        // userId and tableId are guaranteed to be non-null
        
        List<Order> orders = orderService.findByUserIdAndTableId(userId, tableId);
        return ResponseEntity.ok(orders);
    }
}
```

---

## 🔧 Build & Deploy

### Build JAR
```bash
mvn clean package
```

### Run JAR
```bash
java -jar target/api-gateway-0.0.1-SNAPSHOT.jar
```

### Docker (Future)
```dockerfile
FROM eclipse-temurin:17-jre
COPY target/api-gateway-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

---

## 📊 Key Metrics to Monitor

- Request count by route
- JWT validation success/failure rate
- Average response time
- Error rate by status code (401, 403, 500)
- Correlation ID usage for tracing

---

## ✅ Checklist Before Production

- [ ] Change JWT secret to a strong, unique value
- [ ] Configure proper CORS origins (not localhost)
- [ ] Add rate limiting
- [ ] Implement circuit breakers
- [ ] Set up monitoring and alerts
- [ ] Enable HTTPS/TLS
- [ ] Configure proper logging levels
- [ ] Add health check endpoint
- [ ] Set up service discovery (Eureka/Consul)
- [ ] Test with load testing tools

---

**For detailed documentation, see [GATEWAY_IMPLEMENTATION_GUIDE.md](GATEWAY_IMPLEMENTATION_GUIDE.md)**

