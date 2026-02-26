package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.MappingRule;
import com.bracit.fisprocess.domain.entity.MappingRuleLine;
import com.bracit.fisprocess.domain.enums.AuditAction;
import com.bracit.fisprocess.domain.enums.AuditEntityType;
import com.bracit.fisprocess.dto.request.CreateMappingRuleRequestDto;
import com.bracit.fisprocess.dto.request.MappingRuleLineDto;
import com.bracit.fisprocess.dto.request.UpdateMappingRuleRequestDto;
import com.bracit.fisprocess.dto.response.MappingRuleLineResponseDto;
import com.bracit.fisprocess.dto.response.MappingRuleResponseDto;
import com.bracit.fisprocess.exception.MappingRuleConflictException;
import com.bracit.fisprocess.exception.MappingRuleNotFoundException;
import com.bracit.fisprocess.repository.MappingRuleRepository;
import com.bracit.fisprocess.service.AuditService;
import com.bracit.fisprocess.service.MappingRuleService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MappingRuleServiceImpl implements MappingRuleService {

    private final MappingRuleRepository mappingRuleRepository;
    private final AuditService auditService;
    private final JsonMapper jsonMapper;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public MappingRuleResponseDto create(UUID tenantId, CreateMappingRuleRequestDto request) {
        MappingRule rule = modelMapper.map(request, MappingRule.class);
        rule.setTenantId(tenantId);
        rule.setActive(true);
        rule.setVersion(1);
        rule.setLinesReplacing(toRuleLines(request.getLines()));

        try {
            MappingRule saved = mappingRuleRepository.save(rule);
            MappingRuleResponseDto response = toResponse(saved);
            auditService.logChange(
                    tenantId,
                    AuditEntityType.MAPPING_RULE,
                    saved.getId(),
                    AuditAction.CREATED,
                    null,
                    toMap(response),
                    request.getCreatedBy());
            return response;
        } catch (DataIntegrityViolationException ex) {
            throw new MappingRuleConflictException(request.getEventType());
        }
    }

    @Override
    public Page<MappingRuleResponseDto> list(UUID tenantId, @Nullable String eventType, @Nullable Boolean isActive, Pageable pageable) {
        return mappingRuleRepository.findByFilters(tenantId, eventType, isActive, pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public MappingRuleResponseDto update(UUID tenantId, UUID ruleId, UpdateMappingRuleRequestDto request) {
        MappingRule rule = mappingRuleRepository.findByTenantIdAndId(tenantId, ruleId)
                .orElseThrow(() -> new MappingRuleNotFoundException(ruleId));
        Map<String, Object> oldValue = toMap(toResponse(rule));

        rule.setEventType(request.getEventType());
        rule.setDescription(request.getDescription());
        rule.setVersion(rule.getVersion() + 1);
        rule.setActive(true);
        rule.setLinesReplacing(toRuleLines(request.getLines()));

        try {
            MappingRule saved = mappingRuleRepository.save(rule);
            MappingRuleResponseDto response = toResponse(saved);
            auditService.logChange(
                    tenantId,
                    AuditEntityType.MAPPING_RULE,
                    saved.getId(),
                    AuditAction.UPDATED,
                    oldValue,
                    toMap(response),
                    request.getUpdatedBy());
            return response;
        } catch (DataIntegrityViolationException ex) {
            throw new MappingRuleConflictException(request.getEventType());
        }
    }

    @Override
    @Transactional
    public void deactivate(UUID tenantId, UUID ruleId, String performedBy) {
        MappingRule rule = mappingRuleRepository.findByTenantIdAndId(tenantId, ruleId)
                .orElseThrow(() -> new MappingRuleNotFoundException(ruleId));
        if (!rule.isActive()) {
            return;
        }

        Map<String, Object> oldValue = toMap(toResponse(rule));
        rule.setActive(false);
        mappingRuleRepository.save(rule);

        auditService.logChange(
                tenantId,
                AuditEntityType.MAPPING_RULE,
                rule.getId(),
                AuditAction.DEACTIVATED,
                oldValue,
                toMap(toResponse(rule)),
                performedBy);
    }

    private List<MappingRuleLine> toRuleLines(List<MappingRuleLineDto> lines) {
        return lines.stream()
                .map(line -> modelMapper.map(line, MappingRuleLine.class))
                .toList();
    }

    private MappingRuleResponseDto toResponse(MappingRule rule) {
        MappingRuleResponseDto response = modelMapper.map(rule, MappingRuleResponseDto.class);
        response.setRuleId(rule.getId());
        response.setLines(rule.getLines().stream()
                .sorted(Comparator.comparingInt(MappingRuleLine::getSortOrder))
                .map(line -> modelMapper.map(line, MappingRuleLineResponseDto.class))
                .toList());
        return response;
    }

    private Map<String, Object> toMap(Object value) {
        return jsonMapper.convertValue(value, Map.class);
    }
}
