# API Gateway - Restaurant Management System

Spring Cloud Gateway for routing and JWT authentication in a microservices architecture.

## Tech Stack
- Java 17
- Spring Boot 3.x
- Spring Cloud Gateway
- Docker (Multi-stage build)
- Azure Container Apps

---

## 🚀 Azure Deployment

### Prerequisites
- Azure CLI installed
- Azure subscription
- GitHub repository

### Step 1: Run Setup Script

```powershell
.\setup-azure.ps1
```

This creates:
- Azure Resource Group
- Azure Container Registry (ACR)
- Container Apps Environment
- Container App
- Service Principal for GitHub Actions

### Step 2: Configure GitHub Secrets

Go to: **GitHub Repo → Settings → Secrets and variables → Actions**

Add these **5 secrets** (values from setup script output):

| Secret Name | Example Value |
|-------------|---------------|
| `AZURE_CREDENTIALS` | `{...JSON from script...}` |
| `ACR_NAME` | `acrrestaurantgateway01` |
| `RESOURCE_GROUP` | `rg-restaurant-gateway` |
| `CONTAINER_APP_NAME` | `api-gateway` |
| `IMAGE_NAME` | `api-gateway` |

### Step 3: Push to GitHub

```bash
git add .
git commit -m "Deploy to Azure"
git push origin main
```

GitHub Actions automatically:
1. Builds Docker image (with Maven inside)
2. Pushes to Azure Container Registry
3. Deploys to Azure Container Apps

### Step 4: Verify Deployment

```powershell
# Get app URL
az containerapp show `
  --name api-gateway `
  --resource-group rg-restaurant-gateway `
  --query properties.configuration.ingress.fqdn -o tsv

# Test health endpoint
curl https://<your-app-url>/actuator/health
```

---

## 💻 Local Development

### Run with Maven
```powershell
$env:JWT_SECRET = "test-secret-key"
mvn spring-boot:run
```

### Run with Docker
```bash
docker build -t api-gateway .
docker run -p 8080:8080 -e JWT_SECRET=test-secret api-gateway
```

### Test
```bash
curl http://localhost:8080/actuator/health
```

---

## 📋 Architecture

### Routes
- `/api/auth/**` → Authentication Service (public)
- `/api/menu/**` → Menu Service
- `/api/orders/**` → Order Service  
- `/api/analytics/**` → Analytics Service
- `/api/kds/**` → Kitchen Display Service

### Security
- JWT validation on protected routes
- Role-based authorization filters
- Public paths: `/api/auth/**`, `/api/categories/**`, `/actuator/**`

### Monitoring
- Health: `/actuator/health`
- Metrics: `/actuator/metrics`
- Liveness: `/actuator/health/liveness`
- Readiness: `/actuator/health/readiness`

---

## 🔧 Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `JWT_SECRET` | JWT signing secret | Yes |
| `AUTH_SERVICE_URL` | Authentication service URL | Yes |
| `MENU_SERVICE_URL` | Menu service URL | Yes |
| `ORDER_SERVICE_URL` | Order service URL | Yes |
| `ANALYTICS_SERVICE_URL` | Analytics service URL | No |
| `KDS_SERVICE_URL` | KDS service URL | No |

Set in Azure:
```powershell
az containerapp update `
  --name api-gateway `
  --resource-group rg-restaurant-gateway `
  --set-env-vars "JWT_SECRET=your-secret" "AUTH_SERVICE_URL=https://auth.example.com"
```

---

## 🐛 Troubleshooting

### GitHub Actions fails
- Verify all 5 secrets are configured correctly
- Ensure Service Principal has Contributor role on Resource Group

### Container not starting
```powershell
az containerapp logs show `
  --name api-gateway `
  --resource-group rg-restaurant-gateway `
  --follow
```

### Health check failing
- Verify Spring Boot Actuator is enabled
- Check application logs for startup errors


## Tech Stack
- Java 17
- Spring Boot 3.x
- Spring Cloud Gateway
- Docker
- Azure Container Apps

## Local Development

### Prerequisites
- JDK 17
- Maven
- Docker

### Run Locally
```powershell
# Set environment variables
$env:JWT_SECRET="your-secret-key"
$env:AUTH_SERVICE_URL="http://localhost:8081"
$env:MENU_SERVICE_URL="http://localhost:8082"
$env:ORDER_SERVICE_URL="http://localhost:8083"

# Run application
mvn spring-boot:run

# Test
curl http://localhost:8080/actuator/health
```

### Run with Docker
```bash
docker build -t api-gateway .
docker run -p 8080:8080 -e JWT_SECRET=your-secret api-gateway
```

## Azure Deployment

### Step 1: Create Azure Resources
Run the setup script to create all required Azure resources:
```powershell
.\setup-azure.ps1
```

This creates:
- Resource Group
- Azure Container Registry (ACR)
- Container App Environment
- Container App
- Service Principal for GitHub Actions

### Step 2: Configure GitHub Secrets
Add these secrets in GitHub: **Settings → Secrets and variables → Actions**

| Secret | Description |
|--------|-------------|
| `AZURE_CREDENTIALS` | Service principal JSON from setup script |
| `ACR_NAME` | Azure Container Registry name |
| `RESOURCE_GROUP` | Azure resource group name |
| `CONTAINER_APP_NAME` | Container app name |
| `IMAGE_NAME` | Docker image name (e.g., api-gateway) |
| `JWT_SECRET` | JWT secret key |

### Step 3: Deploy
Push to main branch to trigger automatic deployment:
```bash
git push origin main
```

GitHub Actions will:
1. Build Docker image with multi-stage build
2. Push to Azure Container Registry
3. Deploy to Azure Container Apps

### Step 4: Verify
```powershell
# Get app URL
az containerapp show --name api-gateway --resource-group rg-restaurant-gateway --query properties.configuration.ingress.fqdn -o tsv

# Test health endpoint
curl https://<your-app-url>/actuator/health
```

## Architecture

### Routes
- `/api/auth/**` - Authentication Service (public)
- `/api/menu/**` - Menu Service
- `/api/orders/**` - Order Service
- `/api/analytics/**` - Analytics Service
- `/api/kds/**` - Kitchen Display Service

### Security
- JWT validation on protected routes
- Role-based authorization
- Public paths: `/api/auth/**`, `/api/categories/**`, `/actuator/**`

## Monitoring
- Health check: `/actuator/health`
- Metrics: `/actuator/metrics`
- Liveness probe: `/actuator/health/liveness`
- Readiness probe: `/actuator/health/readiness`
### Step 3: Create Azure Resources
```powershell
# Create resource group
az group create --name $RESOURCE_GROUP --location $LOCATION

# Create Container Registry
az acr create --resource-group $RESOURCE_GROUP --name $ACR_NAME --sku Basic --admin-enabled true

# Create Container Apps Environment
az containerapp env create --name $CONTAINER_APP_ENV --resource-group $RESOURCE_GROUP --location $LOCATION

# Get ACR credentials (save these!)
$ACR_USERNAME = az acr credential show --name $ACR_NAME --query username --output tsv
$ACR_PASSWORD = az acr credential show --name $ACR_NAME --query "passwords[0].value" --output tsv

Write-Host "ACR Username: $ACR_USERNAME"
Write-Host "ACR Password: $ACR_PASSWORD"
```

### Step 4: Build and Push First Image
```powershell
# Build application
mvn clean package -DskipTests

# Login to ACR
az acr login --name $ACR_NAME

# Build and push Docker image
docker build -t $ACR_NAME.azurecr.io/api-gateway:latest .
docker push $ACR_NAME.azurecr.io/api-gateway:latest
```

### Step 5: Generate JWT Secret
```powershell
$JWT_SECRET = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 64 | ForEach-Object {[char]$_})
Write-Host "JWT Secret: $JWT_SECRET"
# SAVE THIS SECURELY!
```

### Step 6: Create Container App
```powershell
az containerapp create `
  --name $CONTAINER_APP_NAME `
  --resource-group $RESOURCE_GROUP `
  --environment $CONTAINER_APP_ENV `
  --image "$ACR_NAME.azurecr.io/api-gateway:latest" `
  --registry-server "$ACR_NAME.azurecr.io" `
  --registry-username $ACR_USERNAME `
  --registry-password $ACR_PASSWORD `
  --target-port 8080 `
  --ingress external `
  --min-replicas 1 `
  --max-replicas 3 `
  --cpu 0.5 `
  --memory 1Gi `
  --secrets jwt-secret="$JWT_SECRET" `
  --env-vars `
    SERVER_PORT=8080 `
    LOG_LEVEL=INFO `
    JWT_SECRET=secretref:jwt-secret `
    AUTH_SERVICE_URL="http://auth-service:8081" `
    MENU_SERVICE_URL="http://menu-service:8082" `
    ORDER_SERVICE_URL="http://order-service:8083" `
    ANALYTICS_SERVICE_URL="http://analytics-service:8084" `
    KDS_SERVICE_URL="http://kds-service:8085" `
    CORS_ALLOWED_ORIGINS="https://yourfrontend.com" `
    PUBLIC_PATHS="/api/auth/**,/api/categories/**,/api/menu/**,/api/media/**,/api/orders/active"

# Get app URL
$APP_URL = az containerapp show --name $CONTAINER_APP_NAME --resource-group $RESOURCE_GROUP --query properties.configuration.ingress.fqdn --output tsv
Write-Host "App URL: https://$APP_URL"

# Test
curl "https://$APP_URL/actuator/health"
```

### Step 7: Setup GitHub Actions CI/CD

#### Create Service Principal
```powershell
$SUBSCRIPTION_ID = az account show --query id --output tsv

az ad sp create-for-rbac `
  --name "sp-github-actions-gateway" `
  --role contributor `
  --scopes /subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP `
  --sdk-auth
```
**Copy the entire JSON output!**

#### Configure GitHub Secrets
Go to: **GitHub Repo → Settings → Secrets and variables → Actions**

Add these 3 secrets:
1. `AZURE_CREDENTIALS` = JSON output from above
2. `AZURE_ACR_USERNAME` = Your ACR username
3. `AZURE_ACR_PASSWORD` = Your ACR password

#### Update Workflow File
Edit `.github/workflows/azure-deploy.yml` (lines 8-10):
```yaml
env:
  AZURE_CONTAINER_REGISTRY: acrrestaurantapp  # Your ACR name
  RESOURCE_GROUP: rg-restaurant-gateway       # Your resource group
```

### Step 8: Deploy via GitHub
```powershell
git add .
git commit -m "Configure Azure deployment"
git push origin main
```

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `JWT_SECRET` | Yes | JWT signing secret (64+ chars) |
| `AUTH_SERVICE_URL` | Yes | Authentication service URL |
| `MENU_SERVICE_URL` | Yes | Menu service URL |
| `ORDER_SERVICE_URL` | Yes | Order service URL |
| `ANALYTICS_SERVICE_URL` | Yes | Analytics service URL |
| `KDS_SERVICE_URL` | Yes | KDS service URL |
| `CORS_ALLOWED_ORIGINS` | Yes | Allowed CORS origins |
| `PUBLIC_PATHS` | Yes | Public paths (no auth) |
| `SERVER_PORT` | No | Server port (default: 8080) |
| `LOG_LEVEL` | No | Log level (default: INFO) |

## Useful Commands

### View Logs
```powershell
az containerapp logs show --name api-gateway --resource-group $RESOURCE_GROUP --follow
```

### Update Environment Variable
```powershell
az containerapp update --name api-gateway --resource-group $RESOURCE_GROUP --set-env-vars LOG_LEVEL=DEBUG
```

### Scale
```powershell
az containerapp update --name api-gateway --resource-group $RESOURCE_GROUP --min-replicas 2 --max-replicas 5
```

### Check Status
```powershell
az containerapp show --name api-gateway --resource-group $RESOURCE_GROUP --query properties.latestRevisionName
```

### List Revisions
```powershell
az containerapp revision list --name api-gateway --resource-group $RESOURCE_GROUP --output table
```

### Rollback
```powershell
az containerapp revision activate --name api-gateway --resource-group $RESOURCE_GROUP --revision <revision-name>
```

## Architecture

- **Port**: 8080
- **Health Check**: `/actuator/health`
- **Routes**:
  - `/api/auth/**` → Auth Service
  - `/api/menu/**` → Menu Service
  - `/api/categories/**` → Menu Service
  - `/api/orders/**` → Order Service
  - `/api/analytics/**` → Analytics Service
  - `/api/kds/**` → KDS Service

## Security
- JWT-based authentication
- CORS configured
- Public paths: `/api/auth/**`, `/api/categories/**`, `/api/menu/**`, `/api/media/**`
- Protected paths require valid JWT token

