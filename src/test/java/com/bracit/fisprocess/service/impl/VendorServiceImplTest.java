package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.Vendor;
import com.bracit.fisprocess.domain.enums.PaymentTerms;
import com.bracit.fisprocess.dto.request.CreateVendorRequestDto;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("VendorServiceImpl Unit Tests")
class VendorServiceImplTest {

    @Mock
    private VendorRepository vendorRepository;
    @Mock
    private BusinessEntityRepository businessEntityRepository;

    @InjectMocks
    private VendorServiceImpl vendorService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID VENDOR_ID = UUID.randomUUID();

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

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("should create vendor successfully")
        void shouldCreateVendorSuccessfully() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(vendorRepository.existsByTenantIdAndCode(TENANT_ID, "VEND-001"))
                    .thenReturn(false);

            Vendor saved = Vendor.builder()
                    .vendorId(VENDOR_ID)
                    .tenantId(TENANT_ID)
                    .code("VEND-001")
                    .name("Test Vendor")
                    .currency("USD")
                    .paymentTerms(PaymentTerms.NET_30)
                    .status(Vendor.VendorStatus.ACTIVE)
                    .createdAt(OffsetDateTime.now())
                    .build();
            when(vendorRepository.save(any(Vendor.class))).thenReturn(saved);

            CreateVendorRequestDto request = CreateVendorRequestDto.builder()
                    .code("VEND-001")
                    .name("Test Vendor")
                    .paymentTerms(PaymentTerms.NET_30)
                    .build();

            Vendor result = vendorService.create(TENANT_ID, request);

            assertThat(result).isNotNull();
            assertThat(result.getCode()).isEqualTo("VEND-001");
            assertThat(result.getStatus()).isEqualTo(Vendor.VendorStatus.ACTIVE);
            verify(vendorRepository).save(any(Vendor.class));
        }

        @Test
        @DisplayName("should throw when vendor code already exists")
        void shouldThrowWhenDuplicateCode() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(vendorRepository.existsByTenantIdAndCode(TENANT_ID, "VEND-001"))
                    .thenReturn(true);

            CreateVendorRequestDto request = CreateVendorRequestDto.builder()
                    .code("VEND-001")
                    .name("Test Vendor")
                    .paymentTerms(PaymentTerms.NET_30)
                    .build();

            assertThatThrownBy(() -> vendorService.create(TENANT_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
            verify(vendorRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when tenant not found")
        void shouldThrowWhenTenantNotFound() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.empty());

            CreateVendorRequestDto request = CreateVendorRequestDto.builder()
                    .code("VEND-001")
                    .name("Test Vendor")
                    .build();

            assertThatThrownBy(() -> vendorService.create(TENANT_ID, request))
                    .isInstanceOf(TenantNotFoundException.class);
        }

        @Test
        @DisplayName("should trim vendor code and name")
        void shouldTrimCodeAndName() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(vendorRepository.existsByTenantIdAndCode(TENANT_ID, "VEND-001"))
                    .thenReturn(false);

            Vendor saved = Vendor.builder()
                    .vendorId(VENDOR_ID)
                    .tenantId(TENANT_ID)
                    .code("VEND-001")
                    .name("Test Vendor")
                    .status(Vendor.VendorStatus.ACTIVE)
                    .createdAt(OffsetDateTime.now())
                    .build();
            when(vendorRepository.save(any(Vendor.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateVendorRequestDto request = CreateVendorRequestDto.builder()
                    .code("  VEND-001  ")
                    .name("  Test Vendor  ")
                    .paymentTerms(PaymentTerms.NET_30)
                    .build();

            Vendor result = vendorService.create(TENANT_ID, request);

            assertThat(result.getCode()).isEqualTo("VEND-001");
            assertThat(result.getName()).isEqualTo("Test Vendor");
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("should update vendor successfully")
        void shouldUpdateVendorSuccessfully() {
            Vendor existing = buildActiveVendor();
            when(vendorRepository.findById(VENDOR_ID))
                    .thenReturn(Optional.of(existing));
            when(vendorRepository.save(any(Vendor.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateVendorRequestDto request = CreateVendorRequestDto.builder()
                    .code("VEND-UPDATED")
                    .name("Updated Vendor")
                    .paymentTerms(PaymentTerms.NET_60)
                    .build();

            Vendor result = vendorService.update(TENANT_ID, VENDOR_ID, request);

            assertThat(result.getCode()).isEqualTo("VEND-UPDATED");
            assertThat(result.getName()).isEqualTo("Updated Vendor");
            assertThat(result.getPaymentTerms()).isEqualTo(PaymentTerms.NET_60);
        }

        @Test
        @DisplayName("should throw when vendor not found")
        void shouldThrowWhenVendorNotFound() {
            when(vendorRepository.findById(VENDOR_ID)).thenReturn(Optional.empty());

            CreateVendorRequestDto request = CreateVendorRequestDto.builder()
                    .code("VEND-001")
                    .name("Test")
                    .build();

            assertThatThrownBy(() -> vendorService.update(TENANT_ID, VENDOR_ID, request))
                    .isInstanceOf(com.bracit.fisprocess.exception.VendorNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when updating to duplicate code")
        void shouldThrowWhenUpdatingToDuplicateCode() {
            Vendor existing = buildActiveVendor();
            when(vendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(existing));
            when(vendorRepository.existsByTenantIdAndCode(TENANT_ID, "VEND-DUP"))
                    .thenReturn(true);

            CreateVendorRequestDto request = CreateVendorRequestDto.builder()
                    .code("VEND-DUP")
                    .name("Test")
                    .build();

            assertThatThrownBy(() -> vendorService.update(TENANT_ID, VENDOR_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }
    }

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("should return vendor when found")
        void shouldReturnVendorWhenFound() {
            Vendor vendor = buildActiveVendor();
            when(vendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(vendor));

            Vendor result = vendorService.getById(TENANT_ID, VENDOR_ID);

            assertThat(result).isNotNull();
            assertThat(result.getVendorId()).isEqualTo(VENDOR_ID);
        }

        @Test
        @DisplayName("should throw when vendor not in tenant")
        void shouldThrowWhenVendorNotInTenant() {
            Vendor vendor = buildActiveVendor();
            vendor.setTenantId(UUID.randomUUID()); // Different tenant
            when(vendorRepository.findById(VENDOR_ID)).thenReturn(Optional.of(vendor));

            assertThatThrownBy(() -> vendorService.getById(TENANT_ID, VENDOR_ID))
                    .isInstanceOf(com.bracit.fisprocess.exception.VendorNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("list")
    class ListTests {

        @Test
        @DisplayName("should return paginated results")
        void shouldReturnPaginatedResults() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Vendor> page = new PageImpl<>(List.of(buildActiveVendor()), pageable, 1);

            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(vendorRepository.findByTenantIdWithFilters(TENANT_ID, null, pageable))
                    .thenReturn(page);

            Page<Vendor> result = vendorService.list(TENANT_ID, null, pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("should throw when tenant not found for list")
        void shouldThrowWhenTenantNotFoundForList() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> vendorService.list(TENANT_ID, null, PageRequest.of(0, 10)))
                    .isInstanceOf(TenantNotFoundException.class);
        }
    }

    // --- Helper Methods ---

    private Vendor buildActiveVendor() {
        return Vendor.builder()
                .vendorId(VENDOR_ID)
                .tenantId(TENANT_ID)
                .code("VEND-001")
                .name("Test Vendor")
                .currency("USD")
                .paymentTerms(PaymentTerms.NET_30)
                .status(Vendor.VendorStatus.ACTIVE)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }
}
