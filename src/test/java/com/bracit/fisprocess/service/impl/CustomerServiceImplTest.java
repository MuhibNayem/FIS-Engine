package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.Customer;
import com.bracit.fisprocess.dto.request.CreateCustomerRequestDto;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerServiceImpl Unit Tests")
class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private BusinessEntityRepository businessEntityRepository;

    @InjectMocks
    private InvoiceServiceImpl invoiceService;

    private static final UUID TENANT_ID = UUID.randomUUID();

    private BusinessEntity activeTenant;

    @BeforeEach
    void setUp() {
        activeTenant = BusinessEntity.builder()
                .tenantId(TENANT_ID)
                .name("Test Corp")
                .baseCurrency("USD")
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    // --- createCustomer Tests ---

    @Nested
    @DisplayName("createCustomer")
    class CreateCustomerTests {

        @Test
        @DisplayName("should create customer successfully with default currency")
        void shouldCreateCustomerSuccessfully() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(customerRepository.existsByTenantIdAndCode(TENANT_ID, "CUST-001"))
                    .thenReturn(false);

            Customer saved = Customer.builder()
                    .customerId(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .code("CUST-001")
                    .name("Acme Corp")
                    .email("billing@acme.com")
                    .currency("USD")
                    .creditLimit(0L)
                    .status(Customer.CustomerStatus.ACTIVE)
                    .createdAt(OffsetDateTime.now())
                    .build();
            when(customerRepository.save(any(Customer.class))).thenReturn(saved);

            CreateCustomerRequestDto request = CreateCustomerRequestDto.builder()
                    .code("CUST-001")
                    .name("Acme Corp")
                    .email("billing@acme.com")
                    .build();

            Customer result = invoiceService.createCustomer(TENANT_ID, request);

            assertThat(result).isNotNull();
            assertThat(result.getCode()).isEqualTo("CUST-001");
            assertThat(result.getName()).isEqualTo("Acme Corp");
            assertThat(result.getCurrency()).isEqualTo("USD");
            assertThat(result.getStatus()).isEqualTo(Customer.CustomerStatus.ACTIVE);
            assertThat(result.getCreditLimit()).isZero();
            verify(customerRepository).save(any(Customer.class));
        }

        @Test
        @DisplayName("should create customer with custom currency and credit limit")
        void shouldCreateCustomerWithCustomSettings() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(customerRepository.existsByTenantIdAndCode(TENANT_ID, "CUST-EUR"))
                    .thenReturn(false);

            Customer saved = Customer.builder()
                    .customerId(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .code("CUST-EUR")
                    .name("Euro Client")
                    .currency("EUR")
                    .creditLimit(50000L)
                    .status(Customer.CustomerStatus.ACTIVE)
                    .createdAt(OffsetDateTime.now())
                    .build();
            when(customerRepository.save(any(Customer.class))).thenReturn(saved);

            CreateCustomerRequestDto request = CreateCustomerRequestDto.builder()
                    .code("CUST-EUR")
                    .name("Euro Client")
                    .currency("EUR")
                    .creditLimit(50000L)
                    .build();

            Customer result = invoiceService.createCustomer(TENANT_ID, request);

            assertThat(result.getCurrency()).isEqualTo("EUR");
            assertThat(result.getCreditLimit()).isEqualTo(50000L);
        }

        @Test
        @DisplayName("should throw when customer code already exists")
        void shouldThrowWhenDuplicateCode() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(customerRepository.existsByTenantIdAndCode(TENANT_ID, "CUST-001"))
                    .thenReturn(true);

            CreateCustomerRequestDto request = CreateCustomerRequestDto.builder()
                    .code("CUST-001")
                    .name("Duplicate")
                    .build();

            assertThatThrownBy(() -> invoiceService.createCustomer(TENANT_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
            verify(customerRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when tenant not found")
        void shouldThrowWhenTenantNotFound() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.empty());

            CreateCustomerRequestDto request = CreateCustomerRequestDto.builder()
                    .code("CUST-001")
                    .name("Test")
                    .build();

            assertThatThrownBy(() -> invoiceService.createCustomer(TENANT_ID, request))
                    .isInstanceOf(TenantNotFoundException.class);
        }
    }

    // --- getCustomer Tests ---

    @Nested
    @DisplayName("getCustomer")
    class GetCustomerTests {

        @Test
        @DisplayName("should return customer when found within tenant")
        void shouldReturnCustomerWhenFound() {
            UUID customerId = UUID.randomUUID();
            Customer customer = Customer.builder()
                    .customerId(customerId)
                    .tenantId(TENANT_ID)
                    .code("CUST-001")
                    .name("Test Customer")
                    .currency("USD")
                    .createdAt(OffsetDateTime.now())
                    .build();

            when(customerRepository.findById(customerId))
                    .thenReturn(Optional.of(customer));

            Customer result = invoiceService.getCustomer(TENANT_ID, customerId);

            assertThat(result).isNotNull();
            assertThat(result.getCustomerId()).isEqualTo(customerId);
            assertThat(result.getCode()).isEqualTo("CUST-001");
        }

        @Test
        @DisplayName("should throw when customer not found")
        void shouldThrowWhenCustomerNotFound() {
            UUID customerId = UUID.randomUUID();
            when(customerRepository.findById(customerId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> invoiceService.getCustomer(TENANT_ID, customerId))
                    .isInstanceOf(com.bracit.fisprocess.exception.CustomerNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when customer belongs to different tenant")
        void shouldThrowWhenDifferentTenant() {
            UUID customerId = UUID.randomUUID();
            Customer customer = Customer.builder()
                    .customerId(customerId)
                    .tenantId(UUID.randomUUID()) // different tenant
                    .code("CUST-001")
                    .build();

            when(customerRepository.findById(customerId))
                    .thenReturn(Optional.of(customer));

            assertThatThrownBy(() -> invoiceService.getCustomer(TENANT_ID, customerId))
                    .isInstanceOf(com.bracit.fisprocess.exception.CustomerNotFoundException.class);
        }
    }

    // --- listCustomers Tests ---

    @Nested
    @DisplayName("listCustomers")
    class ListCustomersTests {

        @Test
        @DisplayName("should return paginated results")
        void shouldReturnPaginatedResults() {
            Pageable pageable = PageRequest.of(0, 10);
            Customer customer = Customer.builder()
                    .customerId(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .code("CUST-001")
                    .name("Test Customer")
                    .currency("USD")
                    .createdAt(OffsetDateTime.now())
                    .build();
            Page<Customer> page = new PageImpl<>(List.of(customer), pageable, 1);

            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(customerRepository.findByTenantIdWithFilters(eq(TENANT_ID), eq(null), eq(pageable)))
                    .thenReturn(page);

            var result = invoiceService.listCustomers(TENANT_ID, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("should filter by search term")
        void shouldFilterBySearchTerm() {
            Pageable pageable = PageRequest.of(0, 10);

            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(customerRepository.findByTenantIdWithFilters(eq(TENANT_ID), eq("Acme"), eq(pageable)))
                    .thenReturn(Page.empty(pageable));

            var result = invoiceService.listCustomers(TENANT_ID, "Acme", pageable);

            assertThat(result.getContent()).isEmpty();
            verify(customerRepository).findByTenantIdWithFilters(eq(TENANT_ID), eq("Acme"), eq(pageable));
        }
    }
}
