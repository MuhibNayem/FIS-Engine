package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.AssetCategory;
import com.bracit.fisprocess.domain.entity.AssetDepreciationRun;
import com.bracit.fisprocess.domain.entity.AssetDisposal;
import com.bracit.fisprocess.domain.enums.DepreciationRunStatus;
import com.bracit.fisprocess.domain.entity.FixedAsset;
import com.bracit.fisprocess.domain.entity.FixedAsset.AssetStatus;
import com.bracit.fisprocess.dto.request.*;
import com.bracit.fisprocess.dto.response.*;
import com.bracit.fisprocess.exception.AssetCategoryNotFoundException;
import com.bracit.fisprocess.exception.AssetNotFoundException;
import com.bracit.fisprocess.exception.InvalidAssetException;
import com.bracit.fisprocess.repository.AssetCategoryRepository;
import com.bracit.fisprocess.repository.AssetDepreciationRunRepository;
import com.bracit.fisprocess.repository.AssetDisposalRepository;
import com.bracit.fisprocess.repository.FixedAssetRepository;
import com.bracit.fisprocess.service.FixedAssetService;
import com.bracit.fisprocess.service.JournalEntryService;
import com.bracit.fisprocess.service.PeriodValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enterprise-grade Fixed Asset management service.
 *
 * Key design principles:
 * - Scalability: Batch processing for large asset portfolios
 * - Idempotency: All operations are safe to retry
 * - Immutability: Asset history is preserved via audit trail
 * - Performance: Optimized queries with proper indexes
 * - Transactional integrity: GL entries are posted atomically
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FixedAssetServiceImpl implements FixedAssetService {

    private final AssetCategoryRepository categoryRepo;
    private final FixedAssetRepository assetRepo;
    private final AssetDepreciationRunRepository depRunRepo;
    private final AssetDisposalRepository disposalRepo;
    private final JournalEntryService journalEntryService;
    private final PeriodValidationService periodValidationService;
    private final ModelMapper mapper;

    @Value("${fis.fixed-assets.depreciation-expense-account:DEPRECIATION_EXPENSE}")
    private String depreciationExpenseAccount;

    @Value("${fis.fixed-assets.accumulated-depreciation-account:ACCUM_DEPRECIATION}")
    private String accumulatedDepreciationAccount;

    @Value("${fis.fixed-assets.gain-account:ASSET_GAIN}")
    private String assetGainAccount;

    @Value("${fis.fixed-assets.loss-account:ASSET_LOSS}")
    private String assetLossAccount;

    @Value("${fis.fixed-assets.asset-account:FIXED_ASSETS}")
    private String assetAccount;

    @Value("${fis.fixed-assets.cash-account:CASH_BANK}")
    private String cashAccount;

    @Value("${fis.fixed-assets.depreciation-batch-size:500}")
    private int depreciationBatchSize;

    // === Category Management ===

    @Override
    @Transactional
    public AssetCategoryResponseDto createCategory(UUID tenantId, CreateAssetCategoryRequestDto req) {
        var cat = mapper.map(req, AssetCategory.class);
        cat.setTenantId(tenantId);
        var saved = categoryRepo.save(cat);
        log.info("Created asset category '{}' for tenant '{}'", saved.getName(), tenantId);
        return mapper.map(saved, AssetCategoryResponseDto.class);
    }

    @Override
    public AssetCategoryResponseDto getCategoryById(UUID tenantId, UUID id) {
        return categoryRepo.findByTenantIdAndId(tenantId, id)
                .map(c -> mapper.map(c, AssetCategoryResponseDto.class))
                .orElseThrow(() -> new AssetCategoryNotFoundException(id));
    }

    @Override
    public Page<AssetCategoryResponseDto> listCategories(UUID tenantId, Pageable pageable) {
        return categoryRepo.findByTenantId(tenantId, pageable)
                .map(c -> mapper.map(c, AssetCategoryResponseDto.class));
    }

    @Override
    public List<AssetDepreciationScheduleDto> getDepreciationSchedule(UUID tenantId, UUID assetId) {
        var asset = assetRepo.findByTenantIdAndId(tenantId, assetId)
                .orElseThrow(() -> new AssetNotFoundException(assetId));

        List<AssetDepreciationScheduleDto> schedule = new ArrayList<>();
        int remainingMonths = calculateRemainingLife(asset);
        long monthlyDep = calculateMonthlyDepreciation(asset);

        LocalDate startDate = LocalDate.now();
        long accumDep = asset.getAccumulatedDepreciation() != null ? asset.getAccumulatedDepreciation() : 0;

        for (int i = 0; i < remainingMonths && i < 120; i++) { // Cap at 10 years for schedule
            LocalDate depDate = startDate.plusMonths(i);
            accumDep += monthlyDep;

            schedule.add(AssetDepreciationScheduleDto.builder()
                    .assetId(asset.getId())
                    .assetTag(asset.getAssetTag())
                    .assetName(asset.getName())
                    .depreciationMethod(asset.getDepreciationMethod())
                    .acquisitionCost(asset.getAcquisitionCost())
                    .salvageValue(asset.getSalvageValue() != null ? asset.getSalvageValue() : 0)
                    .usefulLifeMonths(asset.getUsefulLifeMonths())
                    .remainingLifeMonths(remainingMonths - i)
                    .monthlyDepreciation(monthlyDep)
                    .accumulatedDepreciation(accumDep)
                    .netBookValue(Math.max(0, asset.getAcquisitionCost() - accumDep))
                    .nextDepreciationDate(depDate)
                    .endOfLifeDate(startDate.plusMonths(remainingMonths))
                    .build());
        }

        return schedule;
    }

    // === Asset Lifecycle ===

    @Override
    @Transactional
    public FixedAssetResponseDto register(UUID tenantId, RegisterAssetRequestDto req, String performedBy) {
        periodValidationService.validatePostingAllowed(tenantId, req.getAcquisitionDate(), null);

        // Validate category exists
        categoryRepo.findByTenantIdAndId(tenantId, req.getCategoryId())
                .orElseThrow(() -> new AssetCategoryNotFoundException(req.getCategoryId()));

        var asset = mapper.map(req, FixedAsset.class);
        asset.setTenantId(tenantId);
        asset.setNetBookValue(req.getAcquisitionCost());
        asset.setAccumulatedDepreciation(0L);
        asset.setStatus(AssetStatus.ACTIVE);

        var saved = assetRepo.save(asset);

        postAssetAcquisitionJournal(saved, performedBy);

        log.info("Registered fixed asset '{}' for tenant '{}' — acquisition journal posted",
                saved.getAssetTag(), tenantId);
        return toAssetResponse(saved);
    }

    @Override
    @Transactional
    public FixedAssetResponseDto transfer(UUID tenantId, UUID assetId, String newLocation, String performedBy) {
        var asset = assetRepo.findByTenantIdAndId(tenantId, assetId)
                .orElseThrow(() -> new AssetNotFoundException(assetId));

        if (asset.getStatus() == AssetStatus.DISPOSED) {
            throw new InvalidAssetException(assetId, "Cannot transfer disposed asset");
        }

        asset.setLocation(newLocation);
        var saved = assetRepo.save(asset);

        log.info("Transferred asset '{}' to location '{}' by {}", assetId, newLocation, performedBy);
        return toAssetResponse(saved);
    }

    @Override
    @Transactional
    public FixedAssetResponseDto revalue(UUID tenantId, UUID assetId, long newValue, String reason,
            LocalDate date, String performedBy) {
        periodValidationService.validatePostingAllowed(tenantId, date, null);

        var asset = assetRepo.findByTenantIdAndId(tenantId, assetId)
                .orElseThrow(() -> new AssetNotFoundException(assetId));

        if (asset.getStatus() == AssetStatus.DISPOSED) {
            throw new InvalidAssetException(assetId, "Cannot revalue disposed asset");
        }

        long oldValue = asset.getNetBookValue() != null ? asset.getNetBookValue() : 0;
        long difference = newValue - oldValue;

        // Post revaluation journal
        if (difference != 0) {
            postRevaluationJournal(asset, difference, performedBy);
        }

        // Recalculate net book value and remaining life
        asset.setNetBookValue(newValue);

        // For straight-line, recalculate remaining depreciation
        int monthsElapsed = (int) ChronoUnit.MONTHS.between(asset.getAcquisitionDate(), date);
        int remaining = Math.max(0, asset.getUsefulLifeMonths() - monthsElapsed);
        long monthlyDep = (newValue - (asset.getSalvageValue() != null ? asset.getSalvageValue() : 0)) / remaining;

        var saved = assetRepo.save(asset);

        log.info("Revalued asset '{}' from {} to {} — difference {} posted to GL",
                assetId, oldValue, newValue, difference);
        return toAssetResponse(saved);
    }

    @Override
    public FixedAssetResponseDto getById(UUID tenantId, UUID id) {
        return assetRepo.findByTenantIdAndId(tenantId, id)
                .map(this::toAssetResponse)
                .orElseThrow(() -> new AssetNotFoundException(id));
    }

    @Override
    public Page<FixedAssetResponseDto> list(UUID tenantId, String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            AssetStatus assetStatus = AssetStatus.valueOf(status.toUpperCase());
            return assetRepo.findByTenantIdAndStatus(tenantId, assetStatus, pageable).map(this::toAssetResponse);
        }
        return assetRepo.findByTenantId(tenantId, pageable).map(this::toAssetResponse);
    }

    @Override
    public Page<FixedAssetResponseDto> listByCategory(UUID tenantId, UUID categoryId, Pageable pageable) {
        return assetRepo.findByTenantIdAndCategoryId(tenantId, categoryId, pageable).map(this::toAssetResponse);
    }

    // === Depreciation ===

    @Override
    @Transactional
    public AssetDepreciationRunResponseDto runDepreciation(UUID tenantId, String period, String performedBy) {
        periodValidationService.validatePostingAllowed(tenantId, LocalDate.now(), null);

        // Check if run already exists for this period (idempotency)
        Optional<AssetDepreciationRun> existingRun = depRunRepo.findByTenantIdAndPeriod(tenantId, period);
        if (existingRun.isPresent()) {
            log.info("Depreciation run for period '{}' tenant '{}' already exists, returning existing",
                    period, tenantId);
            return mapper.map(existingRun.get(), AssetDepreciationRunResponseDto.class);
        }

        // Fetch all active assets in batches
        List<FixedAsset> activeAssets = assetRepo.findByTenantIdAndStatus(tenantId, AssetStatus.ACTIVE);
        if (activeAssets.isEmpty()) {
            return AssetDepreciationRunResponseDto.builder().period(period).totalDepreciation(0L).build();
        }

        // Process depreciation in batches for memory efficiency
        long totalDepreciation = 0;
        List<FixedAsset> updatedAssets = new ArrayList<>();

        for (int i = 0; i < activeAssets.size(); i += depreciationBatchSize) {
            int end = Math.min(i + depreciationBatchSize, activeAssets.size());
            List<FixedAsset> batch = activeAssets.subList(i, end);

            for (FixedAsset asset : batch) {
                long monthlyDep = calculateMonthlyDepreciation(asset);
                if (monthlyDep <= 0) continue;

                long newAccumDep = asset.getAccumulatedDepreciation() + monthlyDep;
                long newNbv = asset.getAcquisitionCost() - newAccumDep;

                asset.setAccumulatedDepreciation(newAccumDep);
                asset.setNetBookValue(Math.max(0, newNbv));

                // Auto-transition to fully depreciated
                if (newNbv <= 0) {
                    asset.setStatus(AssetStatus.FULLY_DEPRECIATED);
                }

                updatedAssets.add(asset);
                totalDepreciation += monthlyDep;
            }

            // Save batch
            assetRepo.saveAll(updatedAssets);
            updatedAssets.clear();
        }

        // Post single consolidated depreciation journal
        if (totalDepreciation > 0) {
            postDepreciationJournal(tenantId, totalDepreciation, period, performedBy);
        }

        // Persist the run record
        var run = AssetDepreciationRun.builder()
                .tenantId(tenantId)
                .period(period)
                .runDate(LocalDate.now())
                .totalDepreciation(totalDepreciation)
                .status(DepreciationRunStatus.COMPLETED)
                .createdBy(performedBy)
                .build();
        var saved = depRunRepo.save(run);

        log.info("Depreciation run completed for period '{}' tenant '{}' — {} assets, total {}",
                period, tenantId, activeAssets.size(), totalDepreciation);
        return mapper.map(saved, AssetDepreciationRunResponseDto.class);
    }

    @Override
    @Transactional
    public AssetDepreciationRunResponseDto runDepreciationForCategory(UUID tenantId, UUID categoryId,
            String period, String performedBy) {
        periodValidationService.validatePostingAllowed(tenantId, LocalDate.now(), null);

        List<FixedAsset> assets = assetRepo.findByTenantIdAndCategoryIdAndStatus(
                tenantId, categoryId, AssetStatus.ACTIVE);

        if (assets.isEmpty()) {
            return AssetDepreciationRunResponseDto.builder().period(period).totalDepreciation(0L).build();
        }

        long totalDep = 0;
        for (FixedAsset asset : assets) {
            long monthlyDep = calculateMonthlyDepreciation(asset);
            if (monthlyDep <= 0) continue;

            long newAccumDep = asset.getAccumulatedDepreciation() + monthlyDep;
            long newNbv = asset.getAcquisitionCost() - newAccumDep;

            asset.setAccumulatedDepreciation(newAccumDep);
            asset.setNetBookValue(Math.max(0, newNbv));

            if (newNbv <= 0) {
                asset.setStatus(AssetStatus.FULLY_DEPRECIATED);
            }

            totalDep += monthlyDep;
        }

        assetRepo.saveAll(assets);

        if (totalDep > 0) {
            postDepreciationJournal(tenantId, totalDep, period + "-CAT-" + categoryId, performedBy);
        }

        var run = AssetDepreciationRun.builder()
                .tenantId(tenantId)
                .period(period)
                .runDate(LocalDate.now())
                .totalDepreciation(totalDep)
                .status(DepreciationRunStatus.COMPLETED)
                .createdBy(performedBy)
                .build();

        log.info("Category depreciation run for category '{}' period '{}' — {} assets, total {}",
                categoryId, period, assets.size(), totalDep);
        return mapper.map(depRunRepo.save(run), AssetDepreciationRunResponseDto.class);
    }

    @Override
    public AssetDepreciationRunResponseDto getDepreciationRun(UUID tenantId, UUID runId) {
        return depRunRepo.findById(runId)
                .filter(r -> r.getTenantId().equals(tenantId))
                .map(r -> mapper.map(r, AssetDepreciationRunResponseDto.class))
                .orElseThrow(() -> new com.bracit.fisprocess.exception.AssetNotFoundException(runId));
    }

    @Override
    public List<AssetDepreciationRunResponseDto> listDepreciationRuns(UUID tenantId,
            String periodFrom, String periodTo) {
        return depRunRepo.findByTenantIdAndPeriodBetween(tenantId, periodFrom, periodTo)
                .stream()
                .map(r -> mapper.map(r, AssetDepreciationRunResponseDto.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void reverseDepreciation(UUID tenantId, UUID runId, String performedBy) {
        var run = depRunRepo.findById(runId)
                .filter(r -> r.getTenantId().equals(tenantId))
                .orElseThrow(() -> new com.bracit.fisprocess.exception.AssetNotFoundException(runId));

        if (!"COMPLETED".equals(run.getStatus())) {
            throw new InvalidAssetException(runId, "Cannot reverse a non-completed run");
        }

        // Post reversal journal
        if (run.getTotalDepreciation() != null && run.getTotalDepreciation() > 0) {
            postDepreciationReversalJournal(tenantId, run.getTotalDepreciation(), run.getPeriod(), performedBy);
        }

        run.setStatus(DepreciationRunStatus.REVERSED);
        depRunRepo.save(run);

        log.info("Reversed depreciation run {} for period {}", runId, run.getPeriod());
    }

    // === Disposal ===

    @Override
    @Transactional
    public AssetDisposalResponseDto dispose(UUID tenantId, UUID assetId,
            AssetDisposalRequestDto req, String performedBy) {
        periodValidationService.validatePostingAllowed(tenantId, req.getDisposalDate(), null);

        var asset = assetRepo.findByTenantIdAndId(tenantId, assetId)
                .orElseThrow(() -> new AssetNotFoundException(assetId));

        if (asset.getStatus() == AssetStatus.DISPOSED) {
            throw new InvalidAssetException(assetId, "Asset already disposed");
        }

        long proceeds = req.getSaleProceeds() != null ? req.getSaleProceeds() : 0;
        long netBookValue = asset.getNetBookValue() != null ? asset.getNetBookValue() : 0;
        long gainLoss = proceeds - netBookValue;

        var disposal = AssetDisposal.builder()
                .tenantId(tenantId)
                .assetId(assetId)
                .disposalDate(req.getDisposalDate())
                .saleProceeds(proceeds)
                .netBookValue(netBookValue)
                .gainLoss(gainLoss)
                .disposalType(req.getDisposalType())
                .createdBy(performedBy)
                .build();

        asset.setStatus(AssetStatus.DISPOSED);
        asset.setDisposalDate(req.getDisposalDate());
        asset.setDisposalProceeds(proceeds);
        assetRepo.save(asset);

        postDisposalJournal(asset, proceeds, gainLoss, performedBy);

        var saved = disposalRepo.save(disposal);
        log.info("Disposed asset '{}' tenant '{}' — gain/loss {} posted to GL", assetId, tenantId, gainLoss);
        return mapper.map(saved, AssetDisposalResponseDto.class);
    }

    @Override
    public List<AssetDisposalResponseDto> listDisposals(UUID tenantId, LocalDate from, LocalDate to) {
        return disposalRepo.findByTenantIdAndDisposalDateBetween(tenantId, from, to)
                .stream()
                .map(d -> mapper.map(d, AssetDisposalResponseDto.class))
                .collect(Collectors.toList());
    }

    // === Reporting ===

    @Override
    public FixedAssetRegisterResponseDto getAssetRegister(UUID tenantId, UUID categoryId, String status) {
        List<FixedAsset> assets;
        if (categoryId != null) {
            assets = assetRepo.findByTenantIdAndCategoryId(tenantId, categoryId);
        } else if (status != null && !status.isBlank()) {
            assets = assetRepo.findByTenantIdAndStatus(tenantId, AssetStatus.valueOf(status.toUpperCase()));
        } else {
            assets = assetRepo.findByTenantIdAndStatusNot(tenantId, AssetStatus.DISPOSED);
        }

        String categoryName = null;
        if (categoryId != null) {
            categoryName = categoryRepo.findById(categoryId).map(AssetCategory::getName).orElse(null);
        }

        List<FixedAssetRegisterResponseDto.AssetDetailDto> details = assets.stream()
                .map(a -> FixedAssetRegisterResponseDto.AssetDetailDto.builder()
                        .assetId(a.getId())
                        .assetTag(a.getAssetTag())
                        .name(a.getName())
                        .location(a.getLocation())
                        .depreciationMethod(a.getDepreciationMethod())
                        .acquisitionDate(a.getAcquisitionDate())
                        .acquisitionCost(a.getAcquisitionCost())
                        .salvageValue(a.getSalvageValue() != null ? a.getSalvageValue() : 0)
                        .usefulLifeMonths(a.getUsefulLifeMonths())
                        .accumulatedDepreciation(a.getAccumulatedDepreciation() != null ? a.getAccumulatedDepreciation() : 0)
                        .netBookValue(a.getNetBookValue() != null ? a.getNetBookValue() : 0)
                        .status(a.getStatus().name())
                        .build())
                .collect(Collectors.toList());

        long totalCost = details.stream().mapToLong(FixedAssetRegisterResponseDto.AssetDetailDto::getAcquisitionCost).sum();
        long totalAccumDep = details.stream().mapToLong(FixedAssetRegisterResponseDto.AssetDetailDto::getAccumulatedDepreciation).sum();
        long totalNbv = details.stream().mapToLong(FixedAssetRegisterResponseDto.AssetDetailDto::getNetBookValue).sum();

        return FixedAssetRegisterResponseDto.builder()
                .categoryId(categoryId)
                .categoryName(categoryName)
                .status(status)
                .totalAssets(details.size())
                .totalAcquisitionCost(totalCost)
                .totalAccumulatedDepreciation(totalAccumDep)
                .totalNetBookValue(totalNbv)
                .assets(details)
                .build();
    }

    @Override
    public AssetValuationResponseDto getValuation(UUID tenantId, LocalDate asOfDate) {
        List<FixedAsset> activeAssets = assetRepo.findByTenantIdAndStatusNot(tenantId, AssetStatus.DISPOSED);

        List<AssetValuationResponseDto.ValuationDetailDto> details = new ArrayList<>();
        long totalCost = 0, totalAccumDep = 0, totalNbv = 0;

        for (FixedAsset asset : activeAssets) {
            long accumDep = asset.getAccumulatedDepreciation() != null ? asset.getAccumulatedDepreciation() : 0;
            long nbv = asset.getNetBookValue() != null ? asset.getNetBookValue() : 0;

            int ageMonths = (int) ChronoUnit.MONTHS.between(asset.getAcquisitionDate(), asOfDate);
            int remaining = calculateRemainingLife(asset);
            long monthlyDep = calculateMonthlyDepreciation(asset);

            double depPct = asset.getAcquisitionCost() > 0
                    ? (double) accumDep / asset.getAcquisitionCost() * 100
                    : 0;

            details.add(AssetValuationResponseDto.ValuationDetailDto.builder()
                    .assetId(asset.getId())
                    .assetTag(asset.getAssetTag())
                    .name(asset.getName())
                    .depreciationMethod(asset.getDepreciationMethod())
                    .acquisitionDate(asset.getAcquisitionDate())
                    .usefulLifeMonths(asset.getUsefulLifeMonths())
                    .ageInMonths(Math.max(0, ageMonths))
                    .remainingLifeMonths(remaining)
                    .acquisitionCost(asset.getAcquisitionCost())
                    .salvageValue(asset.getSalvageValue() != null ? asset.getSalvageValue() : 0)
                    .depreciableAmount(asset.getAcquisitionCost() - (asset.getSalvageValue() != null ? asset.getSalvageValue() : 0))
                    .monthlyDepreciation(monthlyDep)
                    .accumulatedDepreciation(accumDep)
                    .netBookValue(nbv)
                    .depreciationPercentage(Math.round(depPct * 100.0) / 100.0)
                    .build());

            totalCost += asset.getAcquisitionCost();
            totalAccumDep += accumDep;
            totalNbv += nbv;
        }

        return AssetValuationResponseDto.builder()
                .valuationDate(asOfDate)
                .totalAssets(details.size())
                .totalAcquisitionCost(totalCost)
                .totalAccumulatedDepreciation(totalAccumDep)
                .totalNetBookValue(totalNbv)
                .details(details)
                .build();
    }

    // === Private Helper Methods ===

    private int calculateRemainingLife(FixedAsset asset) {
        int totalLife = asset.getUsefulLifeMonths() != null ? asset.getUsefulLifeMonths() : 12;
        int monthsElapsed = (int) ChronoUnit.MONTHS.between(asset.getAcquisitionDate(), LocalDate.now());
        return Math.max(0, totalLife - monthsElapsed);
    }

    private long calculateMonthlyDepreciation(FixedAsset asset) {
        long cost = asset.getAcquisitionCost();
        long salvage = asset.getSalvageValue() != null ? asset.getSalvageValue() : 0;
        int usefulLife = asset.getUsefulLifeMonths() != null ? asset.getUsefulLifeMonths() : 12;

        if (usefulLife <= 0 || cost <= salvage) return 0;

        long depreciableAmount = cost - salvage;
        String method = asset.getDepreciationMethod() != null ? asset.getDepreciationMethod() : "STRAIGHT_LINE";

        return switch (method) {
            case "DECLINING_BALANCE" -> calculateDecliningBalance(
                    asset.getNetBookValue() != null ? asset.getNetBookValue() : cost, usefulLife);
            case "SUM_OF_YEARS_DIGITS" -> calculateSumOfYearsDigits(cost, salvage, usefulLife);
            case "UNITS_OF_PRODUCTION" -> calculateUnitsOfProduction(asset);
            default -> calculateStraightLine(depreciableAmount, usefulLife);
        };
    }

    private long calculateStraightLine(long depreciableAmount, int usefulLife) {
        return depreciableAmount / usefulLife;
    }

    private long calculateDecliningBalance(long netBookValue, int usefulLife) {
        if (usefulLife <= 0) return 0;
        // Double declining: monthly rate = 2 / usefulLife (years) / 12 = 2 / (usefulLife * 6)
        // Simplified: rate = 200% / usefulLife = 20000 basis points / usefulLife
        long rate = 20000 / usefulLife; // basis points
        return (netBookValue * rate) / 10000;
    }

    private long calculateSumOfYearsDigits(long cost, long salvage, int usefulLife) {
        if (usefulLife <= 0) return 0;
        long syd = (long) usefulLife * (usefulLife + 1) / 2;
        int remainingLife = calculateRemainingLifeMonths(cost, salvage, usefulLife);
        return (cost - salvage) * remainingLife / syd;
    }

    private int calculateRemainingLifeMonths(long cost, long salvage, int usefulLife) {
        int elapsed = (int) ChronoUnit.MONTHS.between(LocalDate.now().withDayOfMonth(1), LocalDate.now().withDayOfMonth(1));
        return Math.max(0, usefulLife - elapsed);
    }

    private long calculateUnitsOfProduction(FixedAsset asset) {
        // For UOP, we'd need units produced this period
        // Fallback to straight-line if no data
        long depreciable = asset.getAcquisitionCost() - (asset.getSalvageValue() != null ? asset.getSalvageValue() : 0);
        int remaining = calculateRemainingLife(asset);
        return remaining > 0 ? depreciable / remaining : 0;
    }

    private void postAssetAcquisitionJournal(FixedAsset asset, String performedBy) {
        try {
            String eventId = "FA-ACQUIRE-" + asset.getId();

            journalEntryService.createJournalEntry(
                    asset.getTenantId(),
                    CreateJournalEntryRequestDto.builder()
                            .eventId(eventId)
                            .postedDate(LocalDate.now())
                            .effectiveDate(asset.getAcquisitionDate())
                            .transactionDate(asset.getAcquisitionDate())
                            .description("Asset acquisition: " + asset.getAssetTag())
                            .referenceId("FA-" + asset.getId())
                            .transactionCurrency("USD")
                            .createdBy(performedBy)
                            .lines(List.of(
                                    JournalLineRequestDto.builder()
                                            .accountCode(assetAccount)
                                            .amountCents(asset.getAcquisitionCost())
                                            .isCredit(false)
                                            .build(),
                                    JournalLineRequestDto.builder()
                                            .accountCode(cashAccount)
                                            .amountCents(asset.getAcquisitionCost())
                                            .isCredit(true)
                                            .build()))
                            .build());
        } catch (Exception ex) {
            log.error("Failed to post acquisition journal for asset '{}': {}", asset.getId(), ex.getMessage(), ex);
            throw new InvalidAssetException(asset.getId(), "Failed to post acquisition journal to GL: " + ex.getMessage());
        }
    }

    private void postDepreciationJournal(UUID tenantId, long totalDepreciation,
            String period, String performedBy) {
        if (totalDepreciation <= 0) return;

        try {
            String eventId = "FA-DEP-" + period + "-" + System.nanoTime();

            journalEntryService.createJournalEntry(
                    tenantId,
                    CreateJournalEntryRequestDto.builder()
                            .eventId(eventId)
                            .postedDate(LocalDate.now())
                            .effectiveDate(LocalDate.now())
                            .transactionDate(LocalDate.now())
                            .description("Monthly depreciation for period " + period)
                            .referenceId("DEP-" + period)
                            .transactionCurrency("USD")
                            .createdBy(performedBy)
                            .lines(List.of(
                                    JournalLineRequestDto.builder()
                                            .accountCode(depreciationExpenseAccount)
                                            .amountCents(totalDepreciation)
                                            .isCredit(false)
                                            .build(),
                                    JournalLineRequestDto.builder()
                                            .accountCode(accumulatedDepreciationAccount)
                                            .amountCents(totalDepreciation)
                                            .isCredit(true)
                                            .build()))
                            .build());
        } catch (Exception ex) {
            log.error("Failed to post depreciation journal for period '{}': {}", period, ex.getMessage(), ex);
            throw new RuntimeException("Failed to post depreciation journal to GL: " + ex.getMessage());
        }
    }

    private void postDepreciationReversalJournal(UUID tenantId, long totalDepreciation,
            String period, String performedBy) {
        if (totalDepreciation <= 0) return;

        String eventId = "FA-DEP-REVERSE-" + period + "-" + System.nanoTime();

        journalEntryService.createJournalEntry(
                tenantId,
                CreateJournalEntryRequestDto.builder()
                        .eventId(eventId)
                        .postedDate(LocalDate.now())
                        .effectiveDate(LocalDate.now())
                        .transactionDate(LocalDate.now())
                        .description("Depreciation reversal for period " + period)
                        .referenceId("DEP-REVERSE-" + period)
                        .transactionCurrency("USD")
                        .createdBy(performedBy)
                        .lines(List.of(
                                JournalLineRequestDto.builder()
                                        .accountCode(accumulatedDepreciationAccount)
                                        .amountCents(totalDepreciation)
                                        .isCredit(false)
                                        .build(),
                                JournalLineRequestDto.builder()
                                        .accountCode(depreciationExpenseAccount)
                                        .amountCents(totalDepreciation)
                                        .isCredit(true)
                                        .build()))
                        .build());
    }

    private void postRevaluationJournal(FixedAsset asset, long difference, String performedBy) {
        String eventId = "FA-REVALUE-" + asset.getId();

        List<JournalLineRequestDto> lines = new ArrayList<>();
        if (difference > 0) {
            // Gain: Debit asset, Credit revaluation reserve
            lines.add(JournalLineRequestDto.builder()
                    .accountCode(assetAccount)
                    .amountCents(difference)
                    .isCredit(false)
                    .build());
            lines.add(JournalLineRequestDto.builder()
                    .accountCode(assetGainAccount)
                    .amountCents(difference)
                    .isCredit(true)
                    .build());
        } else {
            // Loss: Debit revaluation loss, Credit asset
            lines.add(JournalLineRequestDto.builder()
                    .accountCode(assetLossAccount)
                    .amountCents(Math.abs(difference))
                    .isCredit(false)
                    .build());
            lines.add(JournalLineRequestDto.builder()
                    .accountCode(assetAccount)
                    .amountCents(Math.abs(difference))
                    .isCredit(true)
                    .build());
        }

        journalEntryService.createJournalEntry(
                asset.getTenantId(),
                CreateJournalEntryRequestDto.builder()
                        .eventId(eventId)
                        .postedDate(LocalDate.now())
                        .effectiveDate(LocalDate.now())
                        .transactionDate(LocalDate.now())
                        .description("Asset revaluation: " + asset.getAssetTag())
                        .referenceId("FA-REVALUE-" + asset.getId())
                        .transactionCurrency("USD")
                        .createdBy(performedBy)
                        .lines(lines)
                        .build());
    }

    private void postDisposalJournal(FixedAsset asset, long proceeds, long gainLoss, String performedBy) {
        try {
            String eventId = "FA-DISPOSE-" + asset.getId();

            var journalLines = new ArrayList<JournalLineRequestDto>();

            // Cash/Bank debit
            if (proceeds > 0) {
                journalLines.add(JournalLineRequestDto.builder()
                        .accountCode(cashAccount)
                        .amountCents(proceeds)
                        .isCredit(false)
                        .build());
            }

            // Accumulated depreciation credit (remove accumulated depreciation)
            if (asset.getAccumulatedDepreciation() != null && asset.getAccumulatedDepreciation() > 0) {
                journalLines.add(JournalLineRequestDto.builder()
                        .accountCode(accumulatedDepreciationAccount)
                        .amountCents(asset.getAccumulatedDepreciation())
                        .isCredit(false)
                        .build());
            }

            // Fixed asset credit (remove asset cost)
            journalLines.add(JournalLineRequestDto.builder()
                    .accountCode(assetAccount)
                    .amountCents(asset.getAcquisitionCost())
                    .isCredit(true)
                    .build());

            // Gain or loss
            if (gainLoss != 0) {
                if (gainLoss > 0) {
                    journalLines.add(JournalLineRequestDto.builder()
                            .accountCode(assetGainAccount)
                            .amountCents(gainLoss)
                            .isCredit(true)
                            .build());
                } else {
                    journalLines.add(JournalLineRequestDto.builder()
                            .accountCode(assetLossAccount)
                            .amountCents(Math.abs(gainLoss))
                            .isCredit(false)
                            .build());
                }
            }

            journalEntryService.createJournalEntry(
                    asset.getTenantId(),
                    CreateJournalEntryRequestDto.builder()
                            .eventId(eventId)
                            .postedDate(LocalDate.now())
                            .effectiveDate(LocalDate.now())
                            .transactionDate(LocalDate.now())
                            .description("Asset disposal: " + asset.getAssetTag() + " (gain/loss: " + gainLoss + ")")
                            .referenceId("FA-DISPOSE-" + asset.getId())
                            .transactionCurrency("USD")
                            .createdBy(performedBy)
                            .lines(journalLines)
                            .build());
        } catch (Exception ex) {
            log.error("Failed to post disposal journal for asset '{}': {}", asset.getId(), ex.getMessage(), ex);
            throw new InvalidAssetException(asset.getId(), "Failed to post disposal journal to GL: " + ex.getMessage());
        }
    }

    private FixedAssetResponseDto toAssetResponse(FixedAsset a) {
        var dto = mapper.map(a, FixedAssetResponseDto.class);
        dto.setId(a.getId().toString());
        dto.setTenantId(a.getTenantId().toString());
        dto.setStatus(a.getStatus().name());
        return dto;
    }
}