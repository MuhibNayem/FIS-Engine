package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.AbstractIntegrationTest;
import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.AccountingPeriod;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.domain.enums.PeriodStatus;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.AccountingPeriodRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "fis.security.enabled=true"
})
@AutoConfigureMockMvc
@DisplayName("Security RBAC Integration Tests")
class SecurityRbacIntegrationTest extends AbstractIntegrationTest {

    private static final KeyPair JWT_KEY_PAIR = generateKeyPair();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private BusinessEntityRepository businessEntityRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private AccountingPeriodRepository accountingPeriodRepository;

    private UUID tenantId;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("fis.security.enabled", () -> "true");
        registry.add("fis.security.jwt.public-key-pem", () -> toPem((RSAPublicKey) JWT_KEY_PAIR.getPublic()));
    }

    @BeforeEach
    void setUp() {
        tenantId = businessEntityRepository.save(BusinessEntity.builder()
                .name("Security Test Corp")
                .baseCurrency("USD")
                .isActive(true)
                .build()).getTenantId();

        accountingPeriodRepository.save(AccountingPeriod.builder()
                .tenantId(tenantId)
                .name("2026-02")
                .startDate(LocalDate.of(2026, 2, 1))
                .endDate(LocalDate.of(2026, 2, 28))
                .status(PeriodStatus.OPEN)
                .build());

        accountRepository.save(Account.builder()
                .tenantId(tenantId)
                .code("CASH")
                .name("Cash")
                .accountType(AccountType.ASSET)
                .currencyCode("USD")
                .build());
        accountRepository.save(Account.builder()
                .tenantId(tenantId)
                .code("REV")
                .name("Revenue")
                .accountType(AccountType.REVENUE)
                .currencyCode("USD")
                .build());
    }

    @Test
    void shouldReturn401WhenJwtMissing() throws Exception {
        mockMvc.perform(post("/v1/journal-entries")
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(validJe("SEC-401"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldEnforceRbacForReaderAndAccountantAndAdmin() throws Exception {
        String readerToken = bearer(tokenForRoles(List.of("FIS_READER")));
        String accountantToken = bearer(tokenForRoles(List.of("FIS_ACCOUNTANT")));
        String adminToken = bearer(tokenForRoles(List.of("FIS_ADMIN")));

        mockMvc.perform(post("/v1/journal-entries")
                        .header("Authorization", readerToken)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(validJe("SEC-READER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/v1/journal-entries")
                        .header("Authorization", accountantToken)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(validJe("SEC-ACCOUNTANT"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/mapping-rules")
                        .header("Authorization", accountantToken)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType":"SECURITY_TEST_EVENT",
                                  "description":"blocked for accountant",
                                  "createdBy":"security-test",
                                  "lines":[
                                    {"accountCodeExpression":"CASH","isCredit":false,"amountExpression":"100","sortOrder":1},
                                    {"accountCodeExpression":"REV","isCredit":true,"amountExpression":"100","sortOrder":2}
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/v1/mapping-rules")
                        .header("Authorization", adminToken)
                        .header("X-Tenant-Id", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType":"SECURITY_TEST_EVENT_ADMIN",
                                  "description":"allowed for admin",
                                  "createdBy":"security-test",
                                  "lines":[
                                    {"accountCodeExpression":"CASH","isCredit":false,"amountExpression":"100","sortOrder":1},
                                    {"accountCodeExpression":"REV","isCredit":true,"amountExpression":"100","sortOrder":2}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated());
    }

    private CreateJournalEntryRequestDto validJe(String eventId) {
        return CreateJournalEntryRequestDto.builder()
                .eventId(eventId)
                .postedDate(LocalDate.of(2026, 2, 25))
                .transactionCurrency("USD")
                .createdBy("security-test")
                .lines(List.of(
                        JournalLineRequestDto.builder().accountCode("CASH").amountCents(1000L).isCredit(false).build(),
                        JournalLineRequestDto.builder().accountCode("REV").amountCents(1000L).isCredit(true).build()))
                .build();
    }

    private String tokenForRoles(List<String> roles) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("security-test-user")
                .issueTime(new Date())
                .expirationTime(Date.from(OffsetDateTime.now().plusHours(1).toInstant()))
                .claim("roles", roles)
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(JWT_KEY_PAIR.getPrivate()));
        return jwt.serialize();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to generate JWT RSA key pair for tests", ex);
        }
    }

    private static String toPem(RSAPublicKey publicKey) {
        String encoded = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----";
    }
}
