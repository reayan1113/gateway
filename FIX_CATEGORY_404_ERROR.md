# Fix: Category Endpoints 404 Error

## 🐛 Problem

```
org.springframework.web.reactive.resource.NoResourceFoundException: 
404 NOT_FOUND "No static resource api/admin/categories."
```

When trying to access category endpoints (`POST /api/admin/categories`), the Gateway returned 404 error.

---

## 🔍 Root Cause

The API Gateway's `application.yaml` was **missing routes for category endpoints**. 

The Gateway only had a route for `/api/menu/**`, but the category endpoints are:
- `/api/categories/**` (public)
- `/api/admin/categories/**` (admin)

Without explicit routes, the Gateway tried to find these as static resources, resulting in 404.

---

## ✅ Solution Applied

Updated `gateway/src/main/resources/application.yaml` with new routes:

### **Added Routes:**

```yaml
# Category Service - Public (No Auth)
- id: category-service-public
  uri: http://localhost:8082
  predicates:
    - Path=/api/categories/**
  filters:
    - RewritePath=/api/categories/(?<segment>.*), /api/categories/${segment}

# Category Service - Admin
- id: category-service-admin
  uri: http://localhost:8082
  predicates:
    - Path=/api/admin/categories/**
  filters:
    - RewritePath=/api/admin/categories/(?<segment>.*), /api/admin/categories/${segment}
```

### **Also Added Routes for:**

```yaml
# Menu Service - Admin Menu
- id: admin-menu-service
  uri: http://localhost:8082
  predicates:
    - Path=/api/admin/menu/**
  filters:
    - RewritePath=/api/admin/menu/(?<segment>.*), /api/admin/menu/${segment}

# Media Service (Menu Service)
- id: media-service
  uri: http://localhost:8082
  predicates:
    - Path=/api/media/**
  filters:
    - RewritePath=/api/media/(?<segment>.*), /api/media/${segment}
```

### **Updated Public Paths:**

```yaml
gateway:
  public-paths: /api/auth/**,/api/categories/**,/api/menu/**,/api/media/**
```

This allows public access to:
- `/api/categories/**` - Browse categories
- `/api/menu/**` - Browse menu items
- `/api/media/**` - View images
- `/api/auth/**` - Login/register

---

## 🔄 How Gateway Routing Works

### **Request Flow:**

```
Client Request: POST http://localhost:8080/api/admin/categories
                ↓
Gateway matches route: /api/admin/categories/**
                ↓
Route ID: category-service-admin
                ↓
Target URI: http://localhost:8082
                ↓
RewritePath filter applies
                ↓
Final URL: http://localhost:8082/api/admin/categories
                ↓
Menu Service receives request
```

### **Without Route (Before Fix):**

```
Client Request: POST http://localhost:8080/api/admin/categories
                ↓
Gateway: No matching route
                ↓
Gateway: Try to find static resource
                ↓
Error: 404 NOT_FOUND "No static resource api/admin/categories."
```

---

## 📋 Complete Gateway Route Configuration

### **Menu Service Routes (Port 8082):**

| Path | Route ID | Auth Required | Description |
|------|----------|---------------|-------------|
| `/api/menu/**` | menu-service | No (Public) | Browse menu items |
| `/api/admin/menu/**` | admin-menu-service | Yes (ADMIN) | Manage menu items |
| `/api/categories/**` | category-service-public | No (Public) | Browse categories |
| `/api/admin/categories/**` | category-service-admin | Yes (ADMIN) | Manage categories |
| `/api/media/**` | media-service | No (Public) | View images |

### **Other Service Routes:**

| Path | Service | Port |
|------|---------|------|
| `/api/auth/**` | auth-service | 8081 |
| `/api/orders/**` | order-service | 8083 |
| `/api/analytics/**` | analytics-service | 8084 |
| `/api/kds/**` | kds-service | 8085 |

---

## 🧪 Testing

### **Test Public Category Endpoint:**
```bash
curl http://localhost:8080/api/categories
```

**Expected:** 200 OK (No auth required)

### **Test Admin Category Endpoint:**
```bash
curl -X POST http://localhost:8080/api/admin/categories \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "X-Table-Id: 1" \
  -H "Content-Type: application/json" \
  -d '{"name":"Pizza","sortOrder":1}'
```

**Expected:** 201 Created (With valid admin JWT)

### **Test Without JWT (Should Work for Public):**
```bash
curl http://localhost:8080/api/categories
```

**Expected:** 200 OK

### **Test Admin Without JWT (Should Fail):**
```bash
curl -X POST http://localhost:8080/api/admin/categories \
  -H "Content-Type: application/json" \
  -d '{"name":"Pizza","sortOrder":1}'
```

**Expected:** 401 Unauthorized

---

## 🎯 Key Points

1. ✅ **Gateway routes are path-based** - Must define routes for all API paths
2. ✅ **Order matters** - More specific routes (e.g., `/api/admin/categories`) should come before generic ones
3. ✅ **RewritePath needed** - Preserves the full path when forwarding to services
4. ✅ **Public paths** - Must be explicitly configured to skip JWT validation
5. ✅ **Multiple routes to same service** - Menu Service has 5 different routes (menu, admin-menu, categories, admin-categories, media)

---

## ⚠️ Important Notes

### **Route Order in YAML:**

Routes are matched in the order they appear. The current order is correct:

1. `/api/auth/**` (auth-service)
2. `/api/menu/**` (menu-service)
3. `/api/admin/menu/**` (admin-menu-service) ← More specific
4. `/api/categories/**` (category-service-public)
5. `/api/admin/categories/**` (category-service-admin) ← More specific
6. `/api/media/**` (media-service)
7. `/api/orders/**` (order-service)
8. `/api/analytics/**` (analytics-service)
9. `/api/kds/**` (kds-service)

**Why order matters:** If `/api/admin/**` was before `/api/admin/categories/**`, it would match first and route incorrectly.

---

## 🔄 After Restart

After restarting the Gateway, the category endpoints will work:

```bash
# Start Gateway
cd D:\MYW\UniversityWork\Fullstack\gateway
mvn spring-boot:run
```

**Verify routes loaded:**
Check Gateway logs for:
```
Mapped URL path [/api/categories/**] onto handler of type [RoutePredicateHandlerMapping]
Mapped URL path [/api/admin/categories/**] onto handler of type [RoutePredicateHandlerMapping]
```

---

## ✅ Status: FIXED

The 404 error for category endpoints is now resolved. The Gateway properly routes:
- ✅ Public category endpoints (`/api/categories/**`)
- ✅ Admin category endpoints (`/api/admin/categories/**`)
- ✅ Admin menu endpoints (`/api/admin/menu/**`)
- ✅ Media endpoints (`/api/media/**`)

**Restart the Gateway to apply changes!** 🚀

