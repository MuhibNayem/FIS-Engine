package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.AbstractIntegrationTest;
import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.enums.AccountType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AdminIntegrityController Integration Tests")
class AdminIntegrityControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private com.bracit.fisprocess.repository.BusinessEntityRepository businessEntityRepository;
    @Autowired
    private com.bracit.fisprocess.repository.AccountRepository accountRepository;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        BusinessEntity tenant = BusinessEntity.builder()
                .name("Integrity Test Corp")
                .baseCurrency("USD")
                .isActive(true)
                .build();
        tenantId = businessEntityRepository.save(tenant).getTenantId();

        accountRepository.save(Account.builder()
                .tenantId(tenantId)
                .code("A")
                .name("Asset")
                .accountType(AccountType.ASSET)
                .currencyCode("USD")
                .currentBalance(1000L)
                .build());
        accountRepository.save(Account.builder()
                .tenantId(tenantId)
                .code("L")
                .name("Liability")
                .accountType(AccountType.LIABILITY)
                .currencyCode("USD")
                .currentBalance(700L)
                .build());
        accountRepository.save(Account.builder()
                .tenantId(tenantId)
                .code("E")
                .name("Equity")
                .accountType(AccountType.EQUITY)
                .currencyCode("USD")
                .currentBalance(300L)
                .build());
    }

    @Test
    void shouldReturnBalancedIntegrityCheckResponse() throws Exception {
        mockMvc.perform(get("/v1/admin/integrity-check")
                        .header("X-Tenant-Id", tenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId", is(tenantId.toString())))
                .andExpect(jsonPath("$.balanced", is(true)))
                .andExpect(jsonPath("$.equationDelta", is(0)));
    }
}
