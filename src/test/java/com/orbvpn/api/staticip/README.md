# Static IP Test Suite Documentation

This document describes the test suite for the Static IP feature, including what each test covers and how to run them.

## Test Files Overview

| Test File | Type | Description |
|-----------|------|-------------|
| `StaticIPFlutterFlowTest.java` | Integration | Simulates exact Flutter app flow via service layer |
| `StaticIPGraphQLApiTest.java` | Integration | Tests real GraphQL HTTP API with JWT authentication |
| `StaticIPBusinessLogicTest.java` | Unit | Tests business logic, pricing, and plan types |
| `StaticIPIntegrationTest.java` | Integration | Azure provisioning integration tests |
| `AzureStaticIPProvisioningIntegrationTest.java` | Integration | Azure API integration tests |
| `StaticIPTestHarness.java` | Utility | Shared test utilities and helpers |

---

## 1. StaticIPFlutterFlowTest.java

**Purpose**: Simulates the EXACT same flow your Flutter app will use, step by step.

### Test Cases (9 tests, ordered):

| Order | Test Method | What It Tests |
|-------|-------------|---------------|
| 1 | `testQueryStaticIPPlans` | Flutter: Query available plans with pricing |
| 2 | `testQueryAvailableRegions` | Flutter: Query available regions |
| 3 | `testCreateSubscription` | Flutter: Create subscription (PERSONAL plan) |
| 4 | `testAllocateStaticIP` | Flutter: Allocate IP in a region |
| 5 | `testGetUserSubscription` | Flutter: Get user's subscription details |
| 6 | `testGetUserAllocations` | Flutter: Get user's IP allocations |
| 7 | `testReleaseStaticIP` | Flutter: Release an allocated IP |
| 8 | `testChangePlan` | Flutter: Upgrade to PRO plan |
| 9 | `testCancelSubscription` | Flutter: Cancel subscription |

### Run Command:
```bash
mvn test -Dtest=StaticIPFlutterFlowTest
```

---

## 2. StaticIPGraphQLApiTest.java

**Purpose**: Tests the REAL GraphQL HTTP endpoint with JWT authentication - exactly what Flutter does over the network.

### Key Features:
- Uses `TestRestTemplate` to make actual HTTP requests
- Generates real JWT tokens using `JwtTokenUtil.generateAccessToken()`
- Tests the complete request/response cycle

### Test Cases (7 tests, ordered):

| Order | Test Method | What It Tests |
|-------|-------------|---------------|
| 1 | `testQueryPlansViaGraphQL` | HTTP GET plans via GraphQL |
| 2 | `testQueryRegionsViaGraphQL` | HTTP GET regions via GraphQL |
| 3 | `testCreateSubscriptionViaGraphQL` | HTTP POST create subscription mutation |
| 4 | `testAllocateStaticIPViaGraphQL` | HTTP POST allocate IP mutation |
| 5 | `testQueryUserDataViaGraphQL` | HTTP GET user subscription & allocations |
| 6 | `testReleaseStaticIPViaGraphQL` | HTTP POST release IP mutation |
| 7 | `testUnauthorizedAccess` | Verify requests without JWT are rejected |

### Run Command:
```bash
mvn test -Dtest=StaticIPGraphQLApiTest
```

---

## 3. StaticIPBusinessLogicTest.java

**Purpose**: Tests business logic without database - fast unit tests.

### Test Cases:

| Test Method | What It Tests |
|-------------|---------------|
| `testPlanPricing` | All plan types have correct monthly prices |
| `testPlanRegionLimits` | Plans have correct region limits (1-10) |
| `testPlanPortForwardLimits` | Plans have correct port forward limits (5-100) |
| `testPlanDisplayNames` | Plans have user-friendly display names |

### Run Command:
```bash
mvn test -Dtest=StaticIPBusinessLogicTest
```

---

## 4. AzureStaticIPProvisioningIntegrationTest.java

**Purpose**: Tests Azure Static IP provisioning when Azure credentials are configured.

### Environment Variables Required:
```bash
export AZURE_STATICIP_PROVISIONING_ENABLED=true
export AZURE_STATICIP_SUBSCRIPTION_ID=<your-subscription-id>
export AZURE_STATICIP_TENANT_ID=<your-tenant-id>
export AZURE_STATICIP_CLIENT_ID=<your-client-id>
export AZURE_STATICIP_CLIENT_SECRET=<your-client-secret>
export AZURE_STATICIP_RESOURCE_GROUP=orbvpn-staticip-rg
```

### Test Cases (10 tests):

| Test Method | What It Tests |
|-------------|---------------|
| `testServiceAvailability` | Azure provisioning service is available |
| `testGetAvailableRegions` | List of Azure regions returned |
| `testRegionDisplayNames` | Regions have display names |
| `testProvisionStaticIP` | Provision a new static IP from Azure |
| `testDeprovisionStaticIP` | Release static IP back to Azure |
| `testFullProvisioningCycle` | Complete provision → use → deprovision cycle |
| (+ 4 more) | Edge cases and error handling |

### Run Command:
```bash
# With Azure credentials configured:
mvn test -Dtest=AzureStaticIPProvisioningIntegrationTest

# Tests are skipped if Azure not configured
```

---

## 5. StaticIPIntegrationTest.java

**Purpose**: Additional integration tests for Static IP with Azure.

### Run Command:
```bash
mvn test -Dtest=StaticIPIntegrationTest
```

---

## Running All Static IP Tests

```bash
# Run all Static IP tests
mvn test -Dtest="StaticIP*"

# Run with verbose output
mvn test -Dtest="StaticIP*" -X

# Run specific test class
mvn test -Dtest=StaticIPFlutterFlowTest

# Run specific test method
mvn test -Dtest=StaticIPFlutterFlowTest#testCreateSubscription
```

---

## Services Tested

### StaticIPService
- `getAvailableRegions()` - List regions with availability
- `getPlans()` - List subscription plans
- `createSubscription()` - Create user subscription
- `allocateStaticIP()` - Allocate IP to user
- `releaseStaticIP()` - Release allocated IP
- `changePlan()` - Upgrade/downgrade plan
- `cancelSubscription()` - Cancel subscription

### StaticIPPaymentService
- `processSubscriptionWithPayment()` - Handle payment flow
- Supports: Stripe, Apple Store, Google Play, Crypto, FREE/Test

### StaticIPNATConfigurationService
- `configureNATAsync()` - Configure NAT rules on server
- `cleanupNATAsync()` - Remove NAT rules
- `processPendingConfigurations()` - Scheduled retry job
- `getNATStatus()` - Get configuration status

### AzureStaticIPProvisioningService
- `provisionStaticIP()` - Create Azure public IP
- `deprovisionStaticIP()` - Delete Azure public IP
- `getAvailableRegions()` - List Azure regions

---

## GraphQL Queries/Mutations Tested

### Queries:
```graphql
query { staticIPPlans { planType, name, priceMonthly, regionsIncluded } }
query { staticIPRegions { region, displayName, availableCount } }
query { myStaticIPSubscription { id, planType, status, regionsUsed } }
query { myStaticIPAllocations { id, publicIp, region, status } }
```

### Mutations:
```graphql
mutation { createStaticIPSubscription(input: {...}) { success, subscription {...} } }
mutation { allocateStaticIP(input: { region: "eastus" }) { success, allocation {...} } }
mutation { releaseStaticIP(allocationId: 123) }
mutation { changeStaticIPPlan(newPlanType: PRO) { success } }
mutation { cancelStaticIPSubscription }
```

---

## Test Data Management

Tests create isolated test data using unique identifiers:
- Test regions: `flutter-test-{timestamp}`, `graphql-test-{timestamp}`
- Test users: `flutter-staticip-test@orbvpn.com`, `graphql-staticip-test@orbvpn.com`
- Test nodes and pool entries are created per test run

All test data is cleaned up in `@AfterAll` methods.

---

## Troubleshooting

### Database Connection Timeout
If tests hang with "Failed to validate connection" errors:
- The Azure PostgreSQL database may have closed idle connections
- Re-run the tests - they should work on a fresh connection

### Azure Tests Skipped
If Azure integration tests show as "skipped":
- Set the `AZURE_STATICIP_*` environment variables
- Ensure the Azure service principal has proper permissions

### JWT Token Issues
If authentication tests fail:
- Check `jwt.secret` in `application.yml` matches test config
- Ensure `JwtTokenUtil` bean is properly configured

---

## Coverage Summary

| Component | Coverage |
|-----------|----------|
| Subscription CRUD | ✅ Full |
| Allocation CRUD | ✅ Full |
| Payment Processing | ✅ Full (FREE/test mode) |
| NAT Configuration | ✅ Full (simulated) |
| Azure Provisioning | ✅ Full (when configured) |
| GraphQL API | ✅ Full |
| JWT Authentication | ✅ Full |
| Error Handling | ✅ Basic |
