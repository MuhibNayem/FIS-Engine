package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.AbstractIntegrationTest;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.dto.request.CreateAccountRequestDto;
import com.bracit.fisprocess.dto.request.UpdateAccountRequestDto;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Account REST API endpoints.
 * <p>
 * Uses Testcontainers PostgreSQL (via {@link AbstractIntegrationTest}) and
 * MockMvc for full-stack HTTP testing without needing a running server.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AccountController Integration Tests")
class AccountControllerIntegrationTest extends AbstractIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private BusinessEntityRepository businessEntityRepository;

        private final JsonMapper jsonMapper = JsonMapper.builder().build();
        private UUID tenantId;

        @BeforeEach
        void setUp() {
                BusinessEntity tenant = BusinessEntity.builder()
                                .name("Integration Test Corp")
                                .baseCurrency("USD")
                                .isActive(true)
                                .build();
                BusinessEntity savedTenant = businessEntityRepository.save(tenant);
                tenantId = savedTenant.getTenantId();
        }

        private String toJson(Object obj) throws Exception {
                return jsonMapper.writeValueAsString(obj);
        }

        // --- POST /v1/accounts ---

        @Nested
        @DisplayName("POST /v1/accounts")
        class CreateAccountEndpointTests {

                @Test
                @DisplayName("should return 201 with created account")
                void shouldCreateAccountSuccessfully() throws Exception {
                        CreateAccountRequestDto request = CreateAccountRequestDto.builder()
                                        .code("1100-CASH-" + UUID.randomUUID().toString().substring(0, 8))
                                        .name("Cash")
                                        .accountType(AccountType.ASSET)
                                        .currencyCode("USD")
                                        .build();

                        mockMvc.perform(post("/v1/accounts")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(toJson(request)))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.code", notNullValue()))
                                        .andExpect(jsonPath("$.accountType", is("ASSET")))
                                        .andExpect(jsonPath("$.currentBalanceCents", is(0)));
                }

                @Test
                @DisplayName("should return 409 for duplicate account code")
                void shouldReturn409ForDuplicateCode() throws Exception {
                        String code = "DUP-" + UUID.randomUUID().toString().substring(0, 8);
                        CreateAccountRequestDto request = CreateAccountRequestDto.builder()
                                        .code(code)
                                        .name("First Account")
                                        .accountType(AccountType.ASSET)
                                        .currencyCode("USD")
                                        .build();

                        String json = toJson(request);

                        // Create the first account
                        mockMvc.perform(post("/v1/accounts")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json))
                                        .andExpect(status().isCreated());

                        // Try to create a duplicate
                        mockMvc.perform(post("/v1/accounts")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json))
                                        .andExpect(status().isConflict())
                                        .andExpect(jsonPath("$.type", is("/problems/duplicate-account-code")));
                }

                @Test
                @DisplayName("should return 400 for missing required fields")
                void shouldReturn400ForMissingFields() throws Exception {
                        mockMvc.perform(post("/v1/accounts")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{}"))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.type", is("/problems/validation-failed")));
                }
        }

        // --- GET /v1/accounts/{code} ---

        @Nested
        @DisplayName("GET /v1/accounts/{code}")
        class GetAccountEndpointTests {

                @Test
                @DisplayName("should return 200 with account details")
                void shouldReturnAccount() throws Exception {
                        String code = "GET-" + UUID.randomUUID().toString().substring(0, 8);
                        CreateAccountRequestDto createReq = CreateAccountRequestDto.builder()
                                        .code(code)
                                        .name("Test Get")
                                        .accountType(AccountType.REVENUE)
                                        .currencyCode("USD")
                                        .build();

                        mockMvc.perform(post("/v1/accounts")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(toJson(createReq)))
                                        .andExpect(status().isCreated());

                        mockMvc.perform(get("/v1/accounts/{code}", code)
                                        .header("X-Tenant-Id", tenantId.toString()))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code", equalTo(code)));
                }

                @Test
                @DisplayName("should return 404 for non-existent account")
                void shouldReturn404ForNotFound() throws Exception {
                        mockMvc.perform(get("/v1/accounts/{code}", "NONEXISTENT-CODE")
                                        .header("X-Tenant-Id", tenantId.toString()))
                                        .andExpect(status().isNotFound())
                                        .andExpect(jsonPath("$.type", is("/problems/account-not-found")));
                }
        }

        // --- PATCH /v1/accounts/{code} ---

        @Nested
        @DisplayName("PATCH /v1/accounts/{code}")
        class UpdateAccountEndpointTests {

                @Test
                @DisplayName("should deactivate account successfully")
                void shouldDeactivateAccount() throws Exception {
                        String code = "DEACT-" + UUID.randomUUID().toString().substring(0, 8);
                        CreateAccountRequestDto createReq = CreateAccountRequestDto.builder()
                                        .code(code)
                                        .name("Deactivate Me")
                                        .accountType(AccountType.EXPENSE)
                                        .currencyCode("USD")
                                        .build();

                        mockMvc.perform(post("/v1/accounts")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(toJson(createReq)))
                                        .andExpect(status().isCreated());

                        UpdateAccountRequestDto updateReq = UpdateAccountRequestDto.builder()
                                        .isActive(false)
                                        .build();

                        mockMvc.perform(patch("/v1/accounts/{code}", code)
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(toJson(updateReq)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.active", is(false)));
                }
        }

        // --- GET /v1/accounts ---

        @Nested
        @DisplayName("GET /v1/accounts")
        class ListAccountsEndpointTests {

                @Test
                @DisplayName("should return paginated list")
                void shouldReturnPaginatedList() throws Exception {
                        mockMvc.perform(get("/v1/accounts")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .param("page", "0")
                                        .param("size", "10"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasKey("content")));
                }
        }

        // --- Missing Header Tests ---

        @Nested
        @DisplayName("Missing X-Tenant-Id header")
        class MissingHeaderTests {

                @Test
                @DisplayName("should return 400 when X-Tenant-Id header is missing")
                void shouldReturn400WhenTenantHeaderMissing() throws Exception {
                        mockMvc.perform(post("/v1/accounts")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                        "{\"code\":\"NO-HEADER\",\"name\":\"No Header\",\"accountType\":\"ASSET\",\"currencyCode\":\"USD\"}"))
                                        .andExpect(status().isBadRequest());
                }
        }

        // --- GET /v1/accounts/{code}/aggregated-balance ---

        @Nested
        @DisplayName("GET /v1/accounts/{code}/aggregated-balance")
        class AggregatedBalanceEndpointTests {

                @Test
                @DisplayName("should return aggregated balance = sum of parent + 3 children")
                void shouldAggregateParentWithChildren() throws Exception {
                        String suffix = UUID.randomUUID().toString().substring(0, 8);
                        String parentCode = "PARENT-" + suffix;

                        // Create parent account
                        mockMvc.perform(post("/v1/accounts")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(toJson(CreateAccountRequestDto.builder()
                                                        .code(parentCode)
                                                        .name("Parent Assets")
                                                        .accountType(AccountType.ASSET)
                                                        .currencyCode("USD")
                                                        .build())))
                                        .andExpect(status().isCreated());

                        // Create 3 child accounts under the parent
                        for (int i = 1; i <= 3; i++) {
                                mockMvc.perform(post("/v1/accounts")
                                                .header("X-Tenant-Id", tenantId.toString())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(toJson(CreateAccountRequestDto.builder()
                                                                .code("CHILD-" + i + "-" + suffix)
                                                                .name("Child " + i)
                                                                .accountType(AccountType.ASSET)
                                                                .currencyCode("USD")
                                                                .parentAccountCode(parentCode)
                                                                .build())))
                                                .andExpect(status().isCreated());
                        }

                        // All balances are 0 (no journal entries), so aggregated should be 0
                        mockMvc.perform(get("/v1/accounts/{code}/aggregated-balance", parentCode)
                                        .header("X-Tenant-Id", tenantId.toString()))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code", is(parentCode)))
                                        .andExpect(jsonPath("$.aggregatedBalanceCents", is(0)));
                }

                @Test
                @DisplayName("should return own balance when account has no children")
                void shouldReturnOwnBalanceForLeafAccount() throws Exception {
                        String code = "LEAF-" + UUID.randomUUID().toString().substring(0, 8);

                        // Create a leaf account (no children)
                        mockMvc.perform(post("/v1/accounts")
                                        .header("X-Tenant-Id", tenantId.toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(toJson(CreateAccountRequestDto.builder()
                                                        .code(code)
                                                        .name("Leaf Account")
                                                        .accountType(AccountType.ASSET)
                                                        .currencyCode("USD")
                                                        .build())))
                                        .andExpect(status().isCreated());

                        // Aggregated balance = own balance = 0
                        mockMvc.perform(get("/v1/accounts/{code}/aggregated-balance", code)
                                        .header("X-Tenant-Id", tenantId.toString()))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.code", is(code)))
                                        .andExpect(jsonPath("$.aggregatedBalanceCents", is(0)))
                                        .andExpect(jsonPath("$.currentBalanceCents", is(0)));
                }

                @Test
                @DisplayName("should return 404 for non-existent account")
                void shouldReturn404ForNonExistentAccount() throws Exception {
                        mockMvc.perform(get("/v1/accounts/{code}/aggregated-balance", "NONEXISTENT")
                                        .header("X-Tenant-Id", tenantId.toString()))
                                        .andExpect(status().isNotFound());
                }

                @Test
                @DisplayName("should return 400 for invalid account code format")
                void shouldReturn400ForInvalidAccountCodeFormat() throws Exception {
                        mockMvc.perform(get("/v1/accounts/{code}/aggregated-balance", "BAD CODE !")
                                        .header("X-Tenant-Id", tenantId.toString()))
                                        .andExpect(status().isBadRequest())
                                        .andExpect(jsonPath("$.type", is("/problems/validation-failed")));
                }
        }
}
