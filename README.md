# API Gateway - Restaurant Management System

Spring Cloud Gateway for routing and authentication.

## Prerequisites
- JDK 17
- Maven
- Docker
- Azure CLI
- Azure subscription
- GitHub account

## Local Development

### 1. Set Environment Variables
```powershell
$env:JWT_SECRET="your-test-secret"
$env:AUTH_SERVICE_URL="http://localhost:8081"
$env:MENU_SERVICE_URL="http://localhost:8082"
$env:ORDER_SERVICE_URL="http://localhost:8083"
$env:ANALYTICS_SERVICE_URL="http://localhost:8084"
$env:KDS_SERVICE_URL="http://localhost:8085"
```

### 2. Run Application
```bash
mvn spring-boot:run
```

### 3. Test
```bash
curl http://localhost:8080/actuator/health
```

## Azure Deployment

### Step 1: Install Azure CLI
```powershell
winget install -e --id Microsoft.AzureCLI
az login
```

### Step 2: Set Variables (Update These!)
```powershell
$RESOURCE_GROUP="rg-restaurant-gateway"
$LOCATION="eastus"
$ACR_NAME="acrrestaurantapp"  # Must be globally unique, lowercase only
$CONTAINER_APP_ENV="env-restaurant-app"
$CONTAINER_APP_NAME="api-gateway"
```

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

