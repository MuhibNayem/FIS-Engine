package com.bracit.fisprocess.domain;

import com.bracit.fisprocess.AbstractIntegrationTest;
import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.enums.AccountType;
import com.bracit.fisprocess.repository.AccountRepository;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("Entity Proxy Behavior Tests")
class EntityProxyBehaviorTest extends AbstractIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private BusinessEntityRepository businessEntityRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("equals/hashCode should work consistently for entity and JPA proxy")
    void equalsAndHashCodeShouldWorkWithProxy() {
        UUID tenantId = businessEntityRepository.save(BusinessEntity.builder()
                .name("Proxy Test Tenant 1")
                .baseCurrency("USD")
                .isActive(true)
                .build()).getTenantId();
        Account saved = accountRepository.save(Account.builder()
                .tenantId(tenantId)
                .code("PROXY-CASH")
                .name("Proxy Cash")
                .accountType(AccountType.ASSET)
                .currencyCode("USD")
                .currentBalance(0L)
                .build());

        entityManager.flush();
        entityManager.clear();

        Account loaded = accountRepository.findById(saved.getAccountId()).orElseThrow();
        Account proxy = entityManager.getReference(Account.class, saved.getAccountId());

        assertThat(proxy).isEqualTo(loaded);
        assertThat(loaded).isEqualTo(proxy);
        assertThat(proxy.hashCode()).isEqualTo(loaded.hashCode());
    }

    @Test
    @DisplayName("equals/hashCode/toString should not force parent lazy initialization")
    void equalsHashCodeToStringShouldNotInitializeLazyParent() {
        UUID tenantId = businessEntityRepository.save(BusinessEntity.builder()
                .name("Proxy Test Tenant 2")
                .baseCurrency("USD")
                .isActive(true)
                .build()).getTenantId();

        Account parent = accountRepository.save(Account.builder()
                .tenantId(tenantId)
                .code("PARENT")
                .name("Parent")
                .accountType(AccountType.ASSET)
                .currencyCode("USD")
                .currentBalance(0L)
                .build());

        Account child = accountRepository.save(Account.builder()
                .tenantId(tenantId)
                .code("CHILD")
                .name("Child")
                .accountType(AccountType.ASSET)
                .currencyCode("USD")
                .currentBalance(0L)
                .parentAccount(parent)
                .build());

        entityManager.flush();
        entityManager.clear();

        Account loadedChild = accountRepository.findById(child.getAccountId()).orElseThrow();
        Account parentProxy = loadedChild.getParentAccount();
        assertThat(Hibernate.isInitialized(parentProxy)).isFalse();

        loadedChild.equals(loadedChild);
        loadedChild.hashCode();
        loadedChild.toString();

        assertThat(Hibernate.isInitialized(parentProxy)).isFalse();
    }
}
