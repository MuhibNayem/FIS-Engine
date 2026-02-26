package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.MappingRule;
import com.bracit.fisprocess.dto.request.CreateMappingRuleRequestDto;
import com.bracit.fisprocess.dto.request.MappingRuleLineDto;
import com.bracit.fisprocess.dto.request.UpdateMappingRuleRequestDto;
import com.bracit.fisprocess.dto.response.MappingRuleResponseDto;
import com.bracit.fisprocess.exception.MappingRuleNotFoundException;
import com.bracit.fisprocess.repository.MappingRuleRepository;
import com.bracit.fisprocess.service.AuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.modelmapper.ModelMapper;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MappingRuleServiceImpl Unit Tests")
class MappingRuleServiceImplTest {

    @Mock
    private MappingRuleRepository mappingRuleRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private JsonMapper jsonMapper;
    @Spy
    private ModelMapper modelMapper = new ModelMapper();

    @InjectMocks
    private MappingRuleServiceImpl service;

    @Test
    @DisplayName("create should initialize version and active state")
    void createShouldInitializeVersionAndActive() {
        UUID tenantId = UUID.randomUUID();
        CreateMappingRuleRequestDto request = CreateMappingRuleRequestDto.builder()
                .eventType("SALE")
                .description("sale mapping")
                .createdBy("admin")
                .lines(List.of(MappingRuleLineDto.builder()
                        .accountCodeExpression("CASH")
                        .amountExpression("100")
                        .isCredit(false)
                        .sortOrder(1)
                        .build()))
                .build();
        when(mappingRuleRepository.save(any(MappingRule.class))).thenAnswer(inv -> {
            MappingRule rule = inv.getArgument(0);
            rule.setId(UUID.randomUUID());
            rule.setCreatedAt(OffsetDateTime.now());
            rule.setUpdatedAt(OffsetDateTime.now());
            return rule;
        });
        when(jsonMapper.convertValue(any(), any(Class.class))).thenReturn(Map.of());

        MappingRuleResponseDto response = service.create(tenantId, request);

        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.isActive()).isTrue();
        verify(auditService).logChange(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("update should increment version")
    void updateShouldIncrementVersion() {
        UUID tenantId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        MappingRule existing = MappingRule.builder()
                .id(ruleId)
                .tenantId(tenantId)
                .eventType("SALE")
                .description("old")
                .version(2)
                .isActive(true)
                .build();
        when(mappingRuleRepository.findByTenantIdAndId(tenantId, ruleId)).thenReturn(Optional.of(existing));
        when(mappingRuleRepository.save(any(MappingRule.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jsonMapper.convertValue(any(), any(Class.class))).thenReturn(Map.of());

        UpdateMappingRuleRequestDto request = UpdateMappingRuleRequestDto.builder()
                .eventType("SALE_V2")
                .description("new")
                .updatedBy("admin")
                .lines(List.of(MappingRuleLineDto.builder()
                        .accountCodeExpression("REV")
                        .amountExpression("200")
                        .isCredit(true)
                        .sortOrder(1)
                        .build()))
                .build();

        MappingRuleResponseDto response = service.update(tenantId, ruleId, request);

        assertThat(response.getVersion()).isEqualTo(3);
        verify(auditService).logChange(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("update should throw when rule not found")
    void updateShouldThrowWhenMissing() {
        when(mappingRuleRepository.findByTenantIdAndId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(UUID.randomUUID(), UUID.randomUUID(),
                UpdateMappingRuleRequestDto.builder()
                        .eventType("X")
                        .description("Y")
                        .updatedBy("u")
                        .lines(List.of())
                        .build()))
                .isInstanceOf(MappingRuleNotFoundException.class);
    }
}

