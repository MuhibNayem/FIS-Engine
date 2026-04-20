package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.TaxGroup;
import com.bracit.fisprocess.domain.entity.TaxGroupRate;
import com.bracit.fisprocess.domain.entity.TaxRate;
import com.bracit.fisprocess.domain.enums.TaxType;
import com.bracit.fisprocess.dto.request.CreateTaxGroupRequestDto;
import com.bracit.fisprocess.dto.request.CreateTaxRateRequestDto;
import com.bracit.fisprocess.dto.request.TaxGroupRateRequestDto;
import com.bracit.fisprocess.dto.response.TaxCalculationResponseDto;
import com.bracit.fisprocess.dto.response.TaxGroupResponseDto;
import com.bracit.fisprocess.exception.TaxGroupNotFoundException;
import com.bracit.fisprocess.exception.TaxRateNotFoundException;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.TaxGroupRateRepository;
import com.bracit.fisprocess.repository.TaxGroupRepository;
import com.bracit.fisprocess.repository.TaxRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
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
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TaxServiceImpl Unit Tests")
class TaxServiceImplTest {

    @Mock
    private TaxRateRepository taxRateRepository;
    @Mock
    private TaxGroupRepository taxGroupRepository;
    @Mock
    private TaxGroupRateRepository taxGroupRateRepository;
    @Mock
    private BusinessEntityRepository businessEntityRepository;
    @Spy
    private ModelMapper modelMapper = new ModelMapper();

    @InjectMocks
    private TaxServiceImpl taxService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID TAX_RATE_ID = UUID.randomUUID();
    private static final UUID TAX_GROUP_ID = UUID.randomUUID();

    private BusinessEntity activeTenant;
    private TaxRate activeTaxRate;
    private TaxGroup taxGroup;

    @BeforeEach
    void setUp() {
        modelMapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT)
                .setSkipNullEnabled(true);

        activeTenant = BusinessEntity.builder()
                .tenantId(TENANT_ID)
                .name("Test Corp")
                .baseCurrency("USD")
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .build();

        activeTaxRate = TaxRate.builder()
                .taxRateId(TAX_RATE_ID)
                .tenantId(TENANT_ID)
                .code("VAT-15")
                .name("VAT 15%")
                .rate(new BigDecimal("0.15"))
                .effectiveFrom(LocalDate.now().minusYears(1))
                .type(TaxType.VAT)
                .isActive(true)
                .createdAt(OffsetDateTime.now())
                .build();

        taxGroup = TaxGroup.builder()
                .taxGroupId(TAX_GROUP_ID)
                .tenantId(TENANT_ID)
                .name("Standard VAT Group")
                .createdAt(OffsetDateTime.now())
                .build();
    }

    // --- createTaxRate Tests ---

    @Nested
    @DisplayName("createTaxRate")
    class CreateTaxRateTests {

        @Test
        @DisplayName("should create tax rate successfully")
        void shouldCreateTaxRateSuccessfully() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(taxRateRepository.existsByTenantIdAndCode(TENANT_ID, "VAT-15"))
                    .thenReturn(false);
            when(taxRateRepository.save(any(TaxRate.class))).thenAnswer(inv -> inv.getArgument(0));

            CreateTaxRateRequestDto request = CreateTaxRateRequestDto.builder()
                    .code("VAT-15")
                    .name("VAT 15%")
                    .rate(new BigDecimal("0.15"))
                    .effectiveFrom(LocalDate.now())
                    .type(TaxType.VAT)
                    .build();

            var result = taxService.createTaxRate(TENANT_ID, request);

            assertThat(result.getCode()).isEqualTo("VAT-15");
            assertThat(result.getIsActive()).isTrue();
            verify(taxRateRepository).save(any(TaxRate.class));
        }

        @Test
        @DisplayName("should throw when tax rate code already exists")
        void shouldThrowWhenDuplicateCode() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(taxRateRepository.existsByTenantIdAndCode(TENANT_ID, "VAT-15"))
                    .thenReturn(true);

            CreateTaxRateRequestDto request = CreateTaxRateRequestDto.builder()
                    .code("VAT-15")
                    .name("VAT 15%")
                    .rate(new BigDecimal("0.15"))
                    .effectiveFrom(LocalDate.now())
                    .type(TaxType.VAT)
                    .build();

            assertThatThrownBy(() -> taxService.createTaxRate(TENANT_ID, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("should throw when tenant not found")
        void shouldThrowWhenTenantNotFound() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.empty());

            CreateTaxRateRequestDto request = CreateTaxRateRequestDto.builder()
                    .code("VAT-15")
                    .name("VAT 15%")
                    .rate(new BigDecimal("0.15"))
                    .effectiveFrom(LocalDate.now())
                    .build();

            assertThatThrownBy(() -> taxService.createTaxRate(TENANT_ID, request))
                    .isInstanceOf(TenantNotFoundException.class);
        }
    }

    // --- createTaxGroup Tests ---

    @Nested
    @DisplayName("createTaxGroup")
    class CreateTaxGroupTests {

        @Test
        @DisplayName("should create tax group with rates")
        void shouldCreateTaxGroupWithRates() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(taxGroupRepository.save(any(TaxGroup.class))).thenReturn(taxGroup);
            when(taxRateRepository.findById(TAX_RATE_ID)).thenReturn(Optional.of(activeTaxRate));
            when(taxGroupRateRepository.save(any(TaxGroupRate.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            CreateTaxGroupRequestDto request = CreateTaxGroupRequestDto.builder()
                    .name("Standard VAT Group")
                    .description("Standard VAT")
                    .rates(List.of(TaxGroupRateRequestDto.builder()
                            .taxRateId(TAX_RATE_ID)
                            .isCompound(false)
                            .build()))
                    .build();

            var result = taxService.createTaxGroup(TENANT_ID, request);

            assertThat(result.getName()).isEqualTo("Standard VAT Group");
            verify(taxGroupRepository).save(any(TaxGroup.class));
            verify(taxGroupRateRepository).save(any(TaxGroupRate.class));
        }

        @Test
        @DisplayName("should throw when tax rate not found in group")
        void shouldThrowWhenTaxRateNotFound() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(taxGroupRepository.save(any(TaxGroup.class))).thenReturn(taxGroup);
            when(taxRateRepository.findById(TAX_RATE_ID)).thenReturn(Optional.empty());

            CreateTaxGroupRequestDto request = CreateTaxGroupRequestDto.builder()
                    .name("Test Group")
                    .rates(List.of(TaxGroupRateRequestDto.builder()
                            .taxRateId(TAX_RATE_ID)
                            .build()))
                    .build();

            assertThatThrownBy(() -> taxService.createTaxGroup(TENANT_ID, request))
                    .isInstanceOf(TaxRateNotFoundException.class);
        }
    }

    // --- calculate Tests ---

    @Nested
    @DisplayName("calculate")
    class CalculateTests {

        @Test
        @DisplayName("should calculate tax-exclusive correctly")
        void shouldCalculateTaxExclusive() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(taxGroupRepository.findById(TAX_GROUP_ID)).thenReturn(Optional.of(taxGroup));

            TaxGroupRate groupRate = TaxGroupRate.builder()
                    .group(taxGroup)
                    .taxRateId(TAX_RATE_ID)
                    .isCompound(false)
                    .build();
            when(taxGroupRateRepository.findByGroupId(TAX_GROUP_ID)).thenReturn(List.of(groupRate));
            when(taxRateRepository.findActiveRatesByIdsAndDate(any(), any()))
                    .thenReturn(List.of(activeTaxRate));

            TaxCalculationResponseDto result = taxService.calculate(TENANT_ID, 10000L, TAX_GROUP_ID, false);

            assertThat(result.getTotalTax()).isEqualTo(1500L); // 10000 * 0.15
            assertThat(result.getBreakdown()).hasSize(1);
            assertThat(result.getBreakdown().get(0).getTaxAmount()).isEqualTo(1500L);
        }

        @Test
        @DisplayName("should calculate tax-inclusive correctly")
        void shouldCalculateTaxInclusive() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(taxGroupRepository.findById(TAX_GROUP_ID)).thenReturn(Optional.of(taxGroup));

            TaxGroupRate groupRate = TaxGroupRate.builder()
                    .group(taxGroup)
                    .taxRateId(TAX_RATE_ID)
                    .isCompound(false)
                    .build();
            when(taxGroupRateRepository.findByGroupId(TAX_GROUP_ID)).thenReturn(List.of(groupRate));
            when(taxRateRepository.findActiveRatesByIdsAndDate(any(), any()))
                    .thenReturn(List.of(activeTaxRate));

            TaxCalculationResponseDto result = taxService.calculate(TENANT_ID, 11500L, TAX_GROUP_ID, true);

            // tax = 11500 * 0.15 / 1.15 = 1500
            assertThat(result.getTotalTax()).isEqualTo(1500L);
        }

        @Test
        @DisplayName("should throw when tax group not found")
        void shouldThrowWhenTaxGroupNotFound() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(taxGroupRepository.findById(TAX_GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taxService.calculate(TENANT_ID, 10000L, TAX_GROUP_ID, false))
                    .isInstanceOf(TaxGroupNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when tax group has no rates")
        void shouldThrowWhenNoRates() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(taxGroupRepository.findById(TAX_GROUP_ID)).thenReturn(Optional.of(taxGroup));
            when(taxGroupRateRepository.findByGroupId(TAX_GROUP_ID)).thenReturn(List.of());

            assertThatThrownBy(() -> taxService.calculate(TENANT_ID, 10000L, TAX_GROUP_ID, false))
                    .isInstanceOf(com.bracit.fisprocess.exception.InvalidTaxCalculationException.class)
                    .hasMessageContaining("no rates");
        }

        @Test
        @DisplayName("should throw when no active rates for today")
        void shouldThrowWhenNoActiveRates() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(taxGroupRepository.findById(TAX_GROUP_ID)).thenReturn(Optional.of(taxGroup));

            TaxGroupRate groupRate = TaxGroupRate.builder()
                    .group(taxGroup)
                    .taxRateId(TAX_RATE_ID)
                    .isCompound(false)
                    .build();
            when(taxGroupRateRepository.findByGroupId(TAX_GROUP_ID)).thenReturn(List.of(groupRate));
            when(taxRateRepository.findActiveRatesByIdsAndDate(any(), any())).thenReturn(List.of());

            assertThatThrownBy(() -> taxService.calculate(TENANT_ID, 10000L, TAX_GROUP_ID, false))
                    .isInstanceOf(com.bracit.fisprocess.exception.InvalidTaxCalculationException.class)
                    .hasMessageContaining("No active");
        }

        @Test
        @DisplayName("should calculate compound tax correctly")
        void shouldCalculateCompoundTax() {
            // Add a second tax rate for compound calculation
            UUID taxRate2Id = UUID.randomUUID();
            TaxRate taxRate2 = TaxRate.builder()
                    .taxRateId(taxRate2Id)
                    .tenantId(TENANT_ID)
                    .code("SURCHARGE-5")
                    .name("Surcharge 5%")
                    .rate(new BigDecimal("0.05"))
                    .effectiveFrom(LocalDate.now().minusYears(1))
                    .type(TaxType.SALES_TAX)
                    .isActive(true)
                    .build();

            TaxGroupRate groupRate1 = TaxGroupRate.builder()
                    .group(taxGroup)
                    .taxRateId(TAX_RATE_ID)
                    .isCompound(false)
                    .build();
            TaxGroupRate groupRate2 = TaxGroupRate.builder()
                    .group(taxGroup)
                    .taxRateId(taxRate2Id)
                    .isCompound(true)
                    .build();

            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(taxGroupRepository.findById(TAX_GROUP_ID)).thenReturn(Optional.of(taxGroup));
            when(taxGroupRateRepository.findByGroupId(TAX_GROUP_ID))
                    .thenReturn(List.of(groupRate1, groupRate2));
            when(taxRateRepository.findActiveRatesByIdsAndDate(any(), any()))
                    .thenReturn(List.of(activeTaxRate, taxRate2));

            TaxCalculationResponseDto result = taxService.calculate(TENANT_ID, 10000L, TAX_GROUP_ID, false);

            // VAT: 10000 * 0.15 = 1500
            // Surcharge (compound): 10000 * 0.05 = 500 (applied to base)
            assertThat(result.getTotalTax()).isEqualTo(2000L);
            assertThat(result.getBreakdown()).hasSize(2);
        }
    }

    // --- getEffectiveRate Tests ---

    @Nested
    @DisplayName("getEffectiveRate")
    class GetEffectiveRateTests {

        @Test
        @DisplayName("should return effective rate for simple group")
        void shouldReturnEffectiveRateForSimpleGroup() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(taxGroupRepository.findById(TAX_GROUP_ID)).thenReturn(Optional.of(taxGroup));

            TaxGroupRate groupRate = TaxGroupRate.builder()
                    .group(taxGroup)
                    .taxRateId(TAX_RATE_ID)
                    .isCompound(false)
                    .build();
            when(taxGroupRateRepository.findByGroupId(TAX_GROUP_ID)).thenReturn(List.of(groupRate));
            when(taxRateRepository.findActiveRatesByIdsAndDate(any(), any()))
                    .thenReturn(List.of(activeTaxRate));

            BigDecimal effectiveRate = taxService.getEffectiveRate(TENANT_ID, TAX_GROUP_ID);

            assertThat(effectiveRate).isEqualTo(new BigDecimal("0.15"));
        }

        @Test
        @DisplayName("should return zero for group with no rates")
        void shouldReturnZeroForEmptyGroup() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(taxGroupRepository.findById(TAX_GROUP_ID)).thenReturn(Optional.of(taxGroup));
            when(taxGroupRateRepository.findByGroupId(TAX_GROUP_ID)).thenReturn(List.of());

            BigDecimal effectiveRate = taxService.getEffectiveRate(TENANT_ID, TAX_GROUP_ID);

            assertThat(effectiveRate).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("should throw when tax group not found")
        void shouldThrowWhenGroupNotFound() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(taxGroupRepository.findById(TAX_GROUP_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taxService.getEffectiveRate(TENANT_ID, TAX_GROUP_ID))
                    .isInstanceOf(TaxGroupNotFoundException.class);
        }
    }

    // --- listTaxRates Tests ---

    @Nested
    @DisplayName("listTaxRates")
    class ListTaxRatesTests {

        @Test
        @DisplayName("should return paginated results")
        void shouldReturnPaginatedResults() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<TaxRate> page = new PageImpl<>(List.of(activeTaxRate), pageable, 1);

            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(taxRateRepository.findByTenantIdWithFilters(eq(TENANT_ID), eq(null), eq(null), eq(pageable)))
                    .thenReturn(page);

            Page<TaxRate> result = taxService.listTaxRates(TENANT_ID, null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    // --- listTaxGroups Tests ---

    @Nested
    @DisplayName("listTaxGroups")
    class ListTaxGroupsTests {

        @Test
        @DisplayName("should return paginated results")
        void shouldReturnPaginatedResults() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<TaxGroup> page = new PageImpl<>(List.of(taxGroup), pageable, 1);

            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(taxGroupRepository.findByTenantId(eq(TENANT_ID), eq(pageable))).thenReturn(page);
            when(taxGroupRateRepository.findByGroupId(TAX_GROUP_ID)).thenReturn(List.of());

            Page<TaxGroupResponseDto> result = taxService.listTaxGroups(TENANT_ID, pageable);

            assertThat(result.getContent()).hasSize(1);
        }
    }
}
