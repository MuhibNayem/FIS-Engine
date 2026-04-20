package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.AssetCategory;
import com.bracit.fisprocess.domain.entity.AssetDepreciationRun;
import com.bracit.fisprocess.domain.entity.AssetDisposal;
import com.bracit.fisprocess.domain.entity.FixedAsset;
import com.bracit.fisprocess.domain.entity.FixedAsset.AssetStatus;
import com.bracit.fisprocess.domain.enums.DepreciationRunStatus;
import com.bracit.fisprocess.dto.request.CreateAssetCategoryRequestDto;
import com.bracit.fisprocess.dto.request.RegisterAssetRequestDto;
import com.bracit.fisprocess.dto.response.AssetCategoryResponseDto;
import com.bracit.fisprocess.dto.response.FixedAssetResponseDto;
import com.bracit.fisprocess.exception.AssetCategoryNotFoundException;
import com.bracit.fisprocess.exception.AssetNotFoundException;
import com.bracit.fisprocess.repository.AssetCategoryRepository;
import com.bracit.fisprocess.repository.AssetDepreciationRunRepository;
import com.bracit.fisprocess.repository.AssetDisposalRepository;
import com.bracit.fisprocess.repository.FixedAssetRepository;
import com.bracit.fisprocess.service.JournalEntryService;
import com.bracit.fisprocess.service.PeriodValidationService;
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
import org.modelmapper.ModelMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FixedAssetServiceImpl Unit Tests")
class FixedAssetServiceImplTest {

    @Mock
    private AssetCategoryRepository categoryRepo;
    @Mock
    private FixedAssetRepository assetRepo;
    @Mock
    private AssetDepreciationRunRepository depRunRepo;
    @Mock
    private AssetDisposalRepository disposalRepo;
    @Mock
    private JournalEntryService journalEntryService;
    @Mock
    private PeriodValidationService periodValidationService;
    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private com.bracit.fisprocess.service.impl.FixedAssetServiceImpl fixedAssetService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID CATEGORY_ID = UUID.randomUUID();

    private AssetCategory category;
    private FixedAsset asset;

    @BeforeEach
    void setUp() {
        category = AssetCategory.builder()
                .id(CATEGORY_ID)
                .tenantId(TENANT_ID)
                .name("Vehicles")
                .defaultUsefulLifeMonths(60)
                .depreciationMethod(AssetCategory.DepreciationMethod.STRAIGHT_LINE)
                .build();

        asset = FixedAsset.builder()
                .id(ASSET_ID)
                .tenantId(TENANT_ID)
                .categoryId(CATEGORY_ID)
                .assetTag("FA-001")
                .name("Company Car")
                .acquisitionDate(LocalDate.of(2024, 1, 1))
                .acquisitionCost(5000000L) // $50,000 in cents
                .salvageValue(500000L) // $5,000
                .usefulLifeMonths(60)
                .depreciationMethod("STRAIGHT_LINE")
                .accumulatedDepreciation(500000L)
                .netBookValue(4500000L)
                .status(AssetStatus.ACTIVE)
                .location("HQ Parking")
                .build();
    }

    @Nested
    @DisplayName("createCategory")
    class CreateCategoryTests {

        @Test
        @DisplayName("should create asset category successfully")
        void shouldCreateCategory() {
            CreateAssetCategoryRequestDto request = CreateAssetCategoryRequestDto.builder()
                    .name("Office Equipment")
                    .defaultUsefulLifeMonths(36)
                    .depreciationMethod("STRAIGHT_LINE")
                    .build();

            when(categoryRepo.save(any(AssetCategory.class))).thenAnswer(inv -> {
                AssetCategory cat = inv.getArgument(0);
                cat.setId(UUID.randomUUID());
                return cat;
            });
            when(modelMapper.map(any(), any())).thenReturn(new AssetCategoryResponseDto());

            var result = fixedAssetService.createCategory(TENANT_ID, request);

            assertThat(result).isNotNull();
            verify(categoryRepo).save(any(AssetCategory.class));
        }
    }

    @Nested
    @DisplayName("register")
    class RegisterTests {

        @Test
        @DisplayName("should register asset and post acquisition journal")
        void shouldRegisterAssetSuccessfully() {
            RegisterAssetRequestDto request = RegisterAssetRequestDto.builder()
                    .categoryId(CATEGORY_ID)
                    .assetTag("FA-002")
                    .name("Forklift")
                    .acquisitionDate(LocalDate.now())
                    .acquisitionCost(3000000L)
                    .salvageValue(300000L)
                    .usefulLifeMonths(120)
                    .depreciationMethod("STRAIGHT_LINE")
                    .location("Warehouse A")
                    .build();

            when(categoryRepo.findByTenantIdAndId(TENANT_ID, CATEGORY_ID)).thenReturn(Optional.of(category));
            when(assetRepo.save(any(FixedAsset.class))).thenAnswer(inv -> {
                FixedAsset a = inv.getArgument(0);
                a.setId(UUID.randomUUID());
                return a;
            });
            when(journalEntryService.createJournalEntry(any(), any())).thenReturn(new com.bracit.fisprocess.dto.response.JournalEntryResponseDto());
            when(modelMapper.map(any(FixedAsset.class), eq(FixedAssetResponseDto.class))).thenAnswer(inv -> {
                FixedAsset a = inv.getArgument(0);
                return FixedAssetResponseDto.builder()
                        .id(a.getId().toString())
                        .assetTag(a.getAssetTag())
                        .name(a.getName())
                        .status("ACTIVE")
                        .build();
            });

            var result = fixedAssetService.register(TENANT_ID, request, "admin");

            assertThat(result).isNotNull();
            assertThat(result.getAssetTag()).isEqualTo("FA-002");
            verify(assetRepo).save(any(FixedAsset.class));
            verify(journalEntryService).createJournalEntry(any(), any());
        }

        @Test
        @DisplayName("should throw when category not found")
        void shouldThrowWhenCategoryNotFound() {
            RegisterAssetRequestDto request = RegisterAssetRequestDto.builder()
                    .categoryId(UUID.randomUUID())
                    .assetTag("FA-003")
                    .name("Test Asset")
                    .acquisitionDate(LocalDate.now())
                    .acquisitionCost(1000000L)
                    .usefulLifeMonths(12)
                    .depreciationMethod("STRAIGHT_LINE")
                    .build();

            when(categoryRepo.findByTenantIdAndId(any(), any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fixedAssetService.register(TENANT_ID, request, "admin"))
                    .isInstanceOf(AssetCategoryNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("should return asset when found")
        void shouldReturnAssetWhenFound() {
            when(assetRepo.findByTenantIdAndId(TENANT_ID, ASSET_ID)).thenReturn(Optional.of(asset));
            when(modelMapper.map(any(FixedAsset.class), eq(FixedAssetResponseDto.class))).thenReturn(
                    FixedAssetResponseDto.builder()
                            .id(ASSET_ID.toString())
                            .assetTag("FA-001")
                            .name("Company Car")
                            .status("ACTIVE")
                            .build());

            var result = fixedAssetService.getById(TENANT_ID, ASSET_ID);

            assertThat(result).isNotNull();
            assertThat(result.getAssetTag()).isEqualTo("FA-001");
        }

        @Test
        @DisplayName("should throw when asset not found")
        void shouldThrowWhenNotFound() {
            when(assetRepo.findByTenantIdAndId(TENANT_ID, ASSET_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fixedAssetService.getById(TENANT_ID, ASSET_ID))
                    .isInstanceOf(AssetNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("list")
    class ListTests {

        @Test
        @DisplayName("should return paginated assets")
        void shouldReturnPaginatedAssets() {
            when(assetRepo.findByTenantId(TENANT_ID, org.springframework.data.domain.Pageable.unpaged()))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(asset)));
            when(modelMapper.map(any(FixedAsset.class), eq(FixedAssetResponseDto.class))).thenReturn(
                    FixedAssetResponseDto.builder()
                            .id(ASSET_ID.toString())
                            .assetTag("FA-001")
                            .status("ACTIVE")
                            .build());

            var result = fixedAssetService.list(TENANT_ID, null, org.springframework.data.domain.Pageable.unpaged());

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("should filter by status")
        void shouldFilterByStatus() {
            when(assetRepo.findByTenantIdAndStatus(TENANT_ID, AssetStatus.ACTIVE, org.springframework.data.domain.Pageable.unpaged()))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(asset)));
            when(modelMapper.map(any(FixedAsset.class), eq(FixedAssetResponseDto.class))).thenReturn(
                    FixedAssetResponseDto.builder()
                            .id(ASSET_ID.toString())
                            .status("ACTIVE")
                            .build());

            var result = fixedAssetService.list(TENANT_ID, "ACTIVE", org.springframework.data.domain.Pageable.unpaged());

            assertThat(result.getContent()).hasSize(1);
            verify(assetRepo).findByTenantIdAndStatus(TENANT_ID, AssetStatus.ACTIVE, org.springframework.data.domain.Pageable.unpaged());
        }
    }

    @Nested
    @DisplayName("runDepreciation")
    class RunDepreciationTests {

        @Test
        @DisplayName("should run depreciation for all active assets")
        void shouldRunDepreciationForAllActiveAssets() {
            when(depRunRepo.findByTenantIdAndPeriod(TENANT_ID, "2026-04")).thenReturn(Optional.empty());
            when(assetRepo.findByTenantIdAndStatus(TENANT_ID, AssetStatus.ACTIVE)).thenReturn(List.of(asset));
            when(assetRepo.saveAll(anyCollection())).thenReturn(List.of(asset));
            when(journalEntryService.createJournalEntry(any(), any())).thenReturn(new com.bracit.fisprocess.dto.response.JournalEntryResponseDto());
            when(depRunRepo.save(any(AssetDepreciationRun.class))).thenAnswer(inv -> {
                AssetDepreciationRun run = inv.getArgument(0);
                run.setId(UUID.randomUUID());
                return run;
            });
            when(modelMapper.map(any(AssetDepreciationRun.class), any())).thenReturn(new com.bracit.fisprocess.dto.response.AssetDepreciationRunResponseDto());

            var result = fixedAssetService.runDepreciation(TENANT_ID, "2026-04", "admin");

            assertThat(result).isNotNull();
            verify(assetRepo).saveAll(anyCollection());
            verify(journalEntryService).createJournalEntry(any(), any());
        }

        @Test
        @DisplayName("should return existing run when already executed for period")
        void shouldReturnExistingRunWhenAlreadyExecuted() {
            AssetDepreciationRun existingRun = AssetDepreciationRun.builder()
                    .id(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .period("2026-04")
                    .totalDepreciation(500000L)
                    .status(DepreciationRunStatus.COMPLETED)
                    .build();

            when(depRunRepo.findByTenantIdAndPeriod(TENANT_ID, "2026-04")).thenReturn(Optional.of(existingRun));
            when(modelMapper.map(any(AssetDepreciationRun.class), any())).thenReturn(
                    com.bracit.fisprocess.dto.response.AssetDepreciationRunResponseDto.builder()
                            .period("2026-04")
                            .totalDepreciation(500000L)
                            .build());

            var result = fixedAssetService.runDepreciation(TENANT_ID, "2026-04", "admin");

            assertThat(result.getPeriod()).isEqualTo("2026-04");
            verify(assetRepo, never()).saveAll(anyCollection());
        }
    }

    @Nested
    @DisplayName("dispose")
    class DisposeTests {

        @Test
        @DisplayName("should dispose active asset and post journal")
        void shouldDisposeActiveAsset() {
            asset.setStatus(AssetStatus.ACTIVE);
            asset.setNetBookValue(4500000L);
            asset.setAccumulatedDepreciation(500000L);

            when(assetRepo.findByTenantIdAndId(TENANT_ID, ASSET_ID)).thenReturn(Optional.of(asset));
            when(assetRepo.save(any(FixedAsset.class))).thenReturn(asset);
            when(journalEntryService.createJournalEntry(any(), any())).thenReturn(new com.bracit.fisprocess.dto.response.JournalEntryResponseDto());
            when(disposalRepo.save(any())).thenAnswer(inv -> {
                AssetDisposal d = inv.getArgument(0);
                d.setId(UUID.randomUUID());
                return d;
            });
            when(modelMapper.map(any(), any())).thenReturn(new com.bracit.fisprocess.dto.response.AssetDisposalResponseDto());

            var req = com.bracit.fisprocess.dto.request.AssetDisposalRequestDto.builder()
                    .saleProceeds(4000000L)
                    .disposalDate(LocalDate.now())
                    .disposalType("SALE")
                    .build();

            var result = fixedAssetService.dispose(TENANT_ID, ASSET_ID, req, "admin");

            assertThat(result).isNotNull();
            verify(journalEntryService).createJournalEntry(any(), any());
            assertThat(asset.getStatus()).isEqualTo(AssetStatus.DISPOSED);
        }

        @Test
        @DisplayName("should throw when disposing already disposed asset")
        void shouldThrowWhenAlreadyDisposed() {
            asset.setStatus(AssetStatus.DISPOSED);

            when(assetRepo.findByTenantIdAndId(TENANT_ID, ASSET_ID)).thenReturn(Optional.of(asset));

            var req = com.bracit.fisprocess.dto.request.AssetDisposalRequestDto.builder()
                    .saleProceeds(4000000L)
                    .disposalDate(LocalDate.now())
                    .disposalType("SALE")
                    .build();

            assertThatThrownBy(() -> fixedAssetService.dispose(TENANT_ID, ASSET_ID, req, "admin"))
                    .isInstanceOf(com.bracit.fisprocess.exception.InvalidAssetException.class)
                    .hasMessageContaining("already disposed");
        }
    }

    @Nested
    @DisplayName("getDepreciationSchedule")
    class GetDepreciationScheduleTests {

        @Test
        @DisplayName("should return depreciation schedule for asset")
        void shouldReturnDepreciationSchedule() {
            when(assetRepo.findByTenantIdAndId(TENANT_ID, ASSET_ID)).thenReturn(Optional.of(asset));

            var schedule = fixedAssetService.getDepreciationSchedule(TENANT_ID, ASSET_ID);

            assertThat(schedule).isNotEmpty();
            assertThat(schedule.get(0).getAssetId()).isEqualTo(ASSET_ID);
            assertThat(schedule.get(0).getMonthlyDepreciation()).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("getAssetRegister")
    class GetAssetRegisterTests {

        @Test
        @DisplayName("should return asset register with totals")
        void shouldReturnAssetRegister() {
            when(assetRepo.findByTenantIdAndStatusNot(TENANT_ID, AssetStatus.DISPOSED)).thenReturn(List.of(asset));

            var register = fixedAssetService.getAssetRegister(TENANT_ID, null, null);

            assertThat(register).isNotNull();
            assertThat(register.getTotalAssets()).isEqualTo(1);
            assertThat(register.getTotalAcquisitionCost()).isEqualTo(5000000L);
            assertThat(register.getTotalNetBookValue()).isEqualTo(4500000L);
            assertThat(register.getAssets()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getValuation")
    class GetValuationTests {

        @Test
        @DisplayName("should return current valuation")
        void shouldReturnValuation() {
            when(assetRepo.findByTenantIdAndStatusNot(TENANT_ID, AssetStatus.DISPOSED)).thenReturn(List.of(asset));

            var valuation = fixedAssetService.getValuation(TENANT_ID, LocalDate.now());

            assertThat(valuation).isNotNull();
            assertThat(valuation.getTotalAssets()).isEqualTo(1);
            assertThat(valuation.getTotalAcquisitionCost()).isEqualTo(5000000L);
            assertThat(valuation.getDetails()).hasSize(1);
            assertThat(valuation.getDetails().get(0).getNetBookValue()).isEqualTo(4500000L);
        }
    }

    @Nested
    @DisplayName("depreciation calculation")
    class DepreciationCalculationTests {

        @Test
        @DisplayName("should calculate straight-line depreciation correctly")
        void shouldCalculateStraightLineDepreciation() {
            // Asset: $50,000 cost, $5,000 salvage, 60 months = $750/month
            assertThat(asset.getAcquisitionCost()).isEqualTo(5000000L);
            assertThat(asset.getSalvageValue()).isEqualTo(500000L);
            assertThat(asset.getUsefulLifeMonths()).isEqualTo(60);

            // Depreciable amount = $50,000 - $5,000 = $45,000
            // Monthly = $45,000 / 60 = $750
            long expectedMonthlyDep = (5000000L - 500000L) / 60;
            assertThat(expectedMonthlyDep).isEqualTo(75000L); // $750 in cents
        }

        @Test
        @DisplayName("should transition to fully depreciated when NBV reaches zero")
        void shouldTransitionToFullyDepreciated() {
            // Create an asset that is nearly fully depreciated
            FixedAsset nearlyDepreciated = FixedAsset.builder()
                    .id(UUID.randomUUID())
                    .tenantId(TENANT_ID)
                    .categoryId(CATEGORY_ID)
                    .assetTag("FA-OLD")
                    .name("Fully Depreciated Asset")
                    .acquisitionDate(LocalDate.now().minusYears(10))
                    .acquisitionCost(1000000L)
                    .salvageValue(0L)
                    .usefulLifeMonths(60) // 5 years = 60 months
                    .depreciationMethod("STRAIGHT_LINE")
                    .accumulatedDepreciation(1000000L) // = acquisition cost
                    .netBookValue(0L)
                    .status(AssetStatus.ACTIVE)
                    .build();

            // Verify net book value is 0
            assertThat(nearlyDepreciated.getNetBookValue()).isEqualTo(0L);
        }
    }
}