package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.dto.request.CreateMappingRuleRequestDto;
import com.bracit.fisprocess.dto.request.UpdateMappingRuleRequestDto;
import com.bracit.fisprocess.dto.response.MappingRuleResponseDto;
import com.bracit.fisprocess.service.MappingRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/mapping-rules")
@RequiredArgsConstructor
public class MappingRuleController {

    private final MappingRuleService mappingRuleService;

    @PostMapping
    public ResponseEntity<MappingRuleResponseDto> create(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody CreateMappingRuleRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mappingRuleService.create(tenantId, request));
    }

    @GetMapping
    public ResponseEntity<Page<MappingRuleResponseDto>> list(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable String eventType,
            @RequestParam(required = false) @Nullable Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(mappingRuleService.list(tenantId, eventType, isActive, PageRequest.of(page, size)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MappingRuleResponseDto> update(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMappingRuleRequestDto request) {
        return ResponseEntity.ok(mappingRuleService.update(tenantId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy,
            @PathVariable UUID id) {
        mappingRuleService.deactivate(tenantId, id, performedBy);
        return ResponseEntity.noContent().build();
    }
}
