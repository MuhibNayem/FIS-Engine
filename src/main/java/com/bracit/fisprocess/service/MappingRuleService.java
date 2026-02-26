package com.bracit.fisprocess.service;

import com.bracit.fisprocess.dto.request.CreateMappingRuleRequestDto;
import com.bracit.fisprocess.dto.request.UpdateMappingRuleRequestDto;
import com.bracit.fisprocess.dto.response.MappingRuleResponseDto;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface MappingRuleService {

    MappingRuleResponseDto create(UUID tenantId, CreateMappingRuleRequestDto request);

    Page<MappingRuleResponseDto> list(UUID tenantId, @Nullable String eventType, @Nullable Boolean isActive, Pageable pageable);

    MappingRuleResponseDto update(UUID tenantId, UUID ruleId, UpdateMappingRuleRequestDto request);

    void deactivate(UUID tenantId, UUID ruleId, String performedBy);
}
