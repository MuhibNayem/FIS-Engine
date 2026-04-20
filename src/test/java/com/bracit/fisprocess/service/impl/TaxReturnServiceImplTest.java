package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.TaxJurisdiction;
import com.bracit.fisprocess.domain.entity.TaxReturn;
import com.bracit.fisprocess.domain.entity.TaxReturnLine;
import com.bracit.fisprocess.domain.enums.FilingFrequency;
import com.bracit.fisprocess.domain.enums.TaxDirection;
import com.bracit.fisprocess.domain.enums.TaxReturnStatus;
import com.bracit.fisprocess.dto.request.GenerateTaxReturnRequestDto;
import com.bracit.fisprocess.dto.response.TaxLiabilityReportDto;
import com.bracit.fisprocess.dto.response.TaxReturnResponseDto;
import com.bracit.fisprocess.exception.TaxReturnAlreadyFiledException;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.TaxJurisdictionRepository;
import com.bracit.fisprocess.repository.TaxRateRepository;
import com.bracit.fisprocess.repository.TaxReturnLineRepository;
import com.bracit.fisprocess.repository.TaxReturnRepository;
import com.bracit.fisprocess.service.JournalEntryService;
import com.bracit.fisprocess.service.PeriodValidationService;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
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
@DisplayName("TaxReturnServiceImpl Unit Tests")
class TaxReturnServiceImplTest {

    @Mock
    private TaxReturnRepository taxReturnRepository;
    @Mock
    private TaxReturnLineRepository taxReturnLineRepository;
    @Mock
    private TaxJurisdictionRepository taxJurisdictionRepository;
    @Mock
    private TaxRateRepository taxRateRepository;
    @Mock
    private BusinessEntityRepository businessEntityRepository;
    @Mock
    private JournalEntryService journalEntryService;
    @Mock
    private PeriodValidationService periodValidationService;
    @Spy
    private ModelMapper modelMapper = new ModelMapper();

    @InjectMocks
    private TaxReturnServiceImpl taxReturnService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID JURISDICTION_ID = UUID.randomUUID();
    private static final UUID TAX_RETURN_ID = UUID.randomUUID();

    private BusinessEntity activeTenant;
    private TaxJurisdiction jurisdiction;
    private TaxReturn draftTaxReturn;

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

        jurisdiction = TaxJurisdiction.builder()
                .taxJurisdictionId(JURISDICTION_ID)
                .tenantId(TENANT_ID)
                .name("Federal")
                .country("US")
                .region("Federal")
                .filingFrequency(FilingFrequency.MONTHLY)
                .createdAt(OffsetDateTime.now())
                .build();

        draftTaxReturn = TaxReturn.builder()
                .taxReturnId(TAX_RETURN_ID)
                .tenantId(TENANT_ID)
                .jurisdictionId(JURISDICTION_ID)
                .period(YearMonth.of(2026, 3))
                .totalOutputTax(15000L)
                .totalInputTax(5000L)
                .netPayable(10000L)
                .status(TaxReturnStatus.DRAFT)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    // --- generate Tests ---

    @Nested
    @DisplayName("generate")
    class GenerateTests {

        @Test
        @DisplayName("should generate tax return successfully")
        void shouldGenerateTaxReturnSuccessfully() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(taxJurisdictionRepository.findById(JURISDICTION_ID))
                    .thenReturn(Optional.of(jurisdiction));
            when(taxReturnRepository.findByTenantIdAndJurisdictionIdAndPeriod(
                    eq(TENANT_ID), eq(JURISDICTION_ID), any(YearMonth.class)))
                    .thenReturn(Optional.empty());
            when(taxReturnRepository.save(any(TaxReturn.class))).thenAnswer(inv -> inv.getArgument(0));

            GenerateTaxReturnRequestDto request = GenerateTaxReturnRequestDto.builder()
                    .jurisdictionId(JURISDICTION_ID)
                    .period(YearMonth.of(2026, 4))
                    .build();

            TaxReturn result = taxReturnService.generate(TENANT_ID, request, "admin");

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(TaxReturnStatus.DRAFT);
            assertThat(result.getJurisdictionId()).isEqualTo(JURISDICTION_ID);
            verify(taxReturnRepository).save(any(TaxReturn.class));
        }

        @Test
        @DisplayName("should throw when tenant not found")
        void shouldThrowWhenTenantNotFound() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.empty());

            GenerateTaxReturnRequestDto request = GenerateTaxReturnRequestDto.builder()
                    .jurisdictionId(JURISDICTION_ID)
                    .period(YearMonth.of(2026, 4))
                    .build();

            assertThatThrownBy(() -> taxReturnService.generate(TENANT_ID, request, "admin"))
                    .isInstanceOf(com.bracit.fisprocess.exception.TenantNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when jurisdiction not found")
        void shouldThrowWhenJurisdictionNotFound() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(taxJurisdictionRepository.findById(JURISDICTION_ID)).thenReturn(Optional.empty());

            GenerateTaxReturnRequestDto request = GenerateTaxReturnRequestDto.builder()
                    .jurisdictionId(JURISDICTION_ID)
                    .period(YearMonth.of(2026, 4))
                    .build();

            assertThatThrownBy(() -> taxReturnService.generate(TENANT_ID, request, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("should throw when return already exists for period")
        void shouldThrowWhenReturnAlreadyExists() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(taxJurisdictionRepository.findById(JURISDICTION_ID))
                    .thenReturn(Optional.of(jurisdiction));
            when(taxReturnRepository.findByTenantIdAndJurisdictionIdAndPeriod(
                    eq(TENANT_ID), eq(JURISDICTION_ID), any(YearMonth.class)))
                    .thenReturn(Optional.of(draftTaxReturn));

            GenerateTaxReturnRequestDto request = GenerateTaxReturnRequestDto.builder()
                    .jurisdictionId(JURISDICTION_ID)
                    .period(YearMonth.of(2026, 3))
                    .build();

            assertThatThrownBy(() -> taxReturnService.generate(TENANT_ID, request, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }
    }

    // --- file Tests ---

    @Nested
    @DisplayName("file")
    class FileTests {

        @Test
        @DisplayName("should file a draft tax return")
        void shouldFileDraftTaxReturn() {
            when(taxReturnRepository.findByTenantIdAndId(TENANT_ID, TAX_RETURN_ID))
                    .thenReturn(Optional.of(draftTaxReturn));
            when(taxReturnRepository.save(any(TaxReturn.class))).thenAnswer(inv -> inv.getArgument(0));

            TaxReturn result = taxReturnService.file(TENANT_ID, TAX_RETURN_ID, "admin");

            assertThat(result.getStatus()).isEqualTo(TaxReturnStatus.FILED);
            assertThat(result.getFiledAt()).isNotNull();
            verify(taxReturnRepository).save(any(TaxReturn.class));
        }

        @Test
        @DisplayName("should throw when tax return already filed")
        void shouldThrowWhenAlreadyFiled() {
            TaxReturn filedReturn = TaxReturn.builder()
                    .taxReturnId(TAX_RETURN_ID)
                    .tenantId(TENANT_ID)
                    .jurisdictionId(JURISDICTION_ID)
                    .period(YearMonth.of(2026, 3))
                    .status(TaxReturnStatus.FILED)
                    .filedAt(OffsetDateTime.now())
                    .build();
            when(taxReturnRepository.findByTenantIdAndId(TENANT_ID, TAX_RETURN_ID))
                    .thenReturn(Optional.of(filedReturn));

            assertThatThrownBy(() -> taxReturnService.file(TENANT_ID, TAX_RETURN_ID, "admin"))
                    .isInstanceOf(TaxReturnAlreadyFiledException.class);
            verify(taxReturnRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when tax return already paid")
        void shouldThrowWhenAlreadyPaid() {
            TaxReturn paidReturn = TaxReturn.builder()
                    .taxReturnId(TAX_RETURN_ID)
                    .tenantId(TENANT_ID)
                    .jurisdictionId(JURISDICTION_ID)
                    .period(YearMonth.of(2026, 3))
                    .status(TaxReturnStatus.PAID)
                    .filedAt(OffsetDateTime.now().minusDays(10))
                    .build();
            when(taxReturnRepository.findByTenantIdAndId(TENANT_ID, TAX_RETURN_ID))
                    .thenReturn(Optional.of(paidReturn));

            assertThatThrownBy(() -> taxReturnService.file(TENANT_ID, TAX_RETURN_ID, "admin"))
                    .isInstanceOf(TaxReturnAlreadyFiledException.class);
        }

        @Test
        @DisplayName("should throw when tax return not found")
        void shouldThrowWhenNotFound() {
            when(taxReturnRepository.findByTenantIdAndId(TENANT_ID, TAX_RETURN_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> taxReturnService.file(TENANT_ID, TAX_RETURN_ID, "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }
    }

    // --- getById Tests ---

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("should return tax return when found")
        void shouldReturnTaxReturnWhenFound() {
            when(taxReturnRepository.findByTenantIdAndId(TENANT_ID, TAX_RETURN_ID))
                    .thenReturn(Optional.of(draftTaxReturn));

            TaxReturn result = taxReturnService.getById(TENANT_ID, TAX_RETURN_ID);

            assertThat(result).isNotNull();
            assertThat(result.getTaxReturnId()).isEqualTo(TAX_RETURN_ID);
        }

        @Test
        @DisplayName("should throw when tax return not found")
        void shouldThrowWhenNotFound() {
            when(taxReturnRepository.findByTenantIdAndId(TENANT_ID, TAX_RETURN_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> taxReturnService.getById(TENANT_ID, TAX_RETURN_ID))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // --- list Tests ---

    @Nested
    @DisplayName("list")
    class ListTests {

        @Test
        @DisplayName("should return paginated results")
        void shouldReturnPaginatedResults() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<TaxReturn> page = new PageImpl<>(List.of(draftTaxReturn), pageable, 1);

            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(taxReturnRepository.findByTenantIdWithFilters(
                    eq(TENANT_ID), eq(null), eq(null), eq(pageable)))
                    .thenReturn(page);

            Page<TaxReturnResponseDto> result = taxReturnService.list(
                    TENANT_ID, null, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo("DRAFT");
        }

        @Test
        @DisplayName("should throw when tenant not found")
        void shouldThrowWhenTenantNotFound() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> taxReturnService.list(
                    TENANT_ID, null, null, PageRequest.of(0, 10)))
                    .isInstanceOf(com.bracit.fisprocess.exception.TenantNotFoundException.class);
        }
    }

    // --- getLiabilityReport Tests ---

    @Nested
    @DisplayName("getLiabilityReport")
    class GetLiabilityReportTests {

        @Test
        @DisplayName("should generate liability report")
        void shouldGenerateLiabilityReport() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(taxJurisdictionRepository.findById(JURISDICTION_ID))
                    .thenReturn(Optional.of(jurisdiction));

            // Mock pagination to return empty
            Page<TaxReturn> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 1000), 0);
            when(taxReturnRepository.findByTenantIdWithFilters(
                    eq(TENANT_ID), eq(JURISDICTION_ID), eq(null), any(Pageable.class)))
                    .thenReturn(emptyPage);

            TaxLiabilityReportDto result = taxReturnService.getLiabilityReport(
                    TENANT_ID, JURISDICTION_ID,
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 3, 31));

            assertThat(result).isNotNull();
            assertThat(result.getJurisdictionId()).isEqualTo(JURISDICTION_ID.toString());
        }

        @Test
        @DisplayName("should throw when jurisdiction not found for report")
        void shouldThrowWhenJurisdictionNotFoundForReport() {
            when(businessEntityRepository.findByTenantIdAndIsActiveTrue(TENANT_ID))
                    .thenReturn(Optional.of(activeTenant));
            when(taxJurisdictionRepository.findById(JURISDICTION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> taxReturnService.getLiabilityReport(
                    TENANT_ID, JURISDICTION_ID,
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 3, 31)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }
    }
}
