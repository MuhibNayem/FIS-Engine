package com.bracit.fisprocess.service;

import com.bracit.fisprocess.dto.request.*;
import com.bracit.fisprocess.dto.response.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FixedAssetService {

    // --- Category Management ---
    AssetCategoryResponseDto createCategory(UUID tenantId, CreateAssetCategoryRequestDto req);
    AssetCategoryResponseDto getCategoryById(UUID tenantId, UUID id);
    Page<AssetCategoryResponseDto> listCategories(UUID tenantId, Pageable pageable);
    List<AssetDepreciationScheduleDto> getDepreciationSchedule(UUID tenantId, UUID assetId);

    // --- Asset Lifecycle ---
    FixedAssetResponseDto register(UUID tenantId, RegisterAssetRequestDto req, String performedBy);
    FixedAssetResponseDto transfer(UUID tenantId, UUID assetId, String newLocation, String performedBy);
    FixedAssetResponseDto revalue(UUID tenantId, UUID assetId, long newValue, String reason, LocalDate date, String performedBy);
    FixedAssetResponseDto getById(UUID tenantId, UUID id);
    Page<FixedAssetResponseDto> list(UUID tenantId, String status, Pageable pageable);
    Page<FixedAssetResponseDto> listByCategory(UUID tenantId, UUID categoryId, Pageable pageable);

    // --- Depreciation ---
    AssetDepreciationRunResponseDto runDepreciation(UUID tenantId, String period, String performedBy);
    AssetDepreciationRunResponseDto runDepreciationForCategory(UUID tenantId, UUID categoryId, String period, String performedBy);
    AssetDepreciationRunResponseDto getDepreciationRun(UUID tenantId, UUID runId);
    List<AssetDepreciationRunResponseDto> listDepreciationRuns(UUID tenantId, String periodFrom, String periodTo);
    void reverseDepreciation(UUID tenantId, UUID runId, String performedBy);

    // --- Disposal ---
    AssetDisposalResponseDto dispose(UUID tenantId, UUID assetId, AssetDisposalRequestDto req, String performedBy);
    List<AssetDisposalResponseDto> listDisposals(UUID tenantId, LocalDate from, LocalDate to);

    // --- Reporting ---
    FixedAssetRegisterResponseDto getAssetRegister(UUID tenantId, UUID categoryId, String status);
    AssetValuationResponseDto getValuation(UUID tenantId, LocalDate asOfDate);
}