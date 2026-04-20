package com.bracit.fisprocess.service;
import java.util.UUID;
public interface ConsolidationService {
    com.bracit.fisprocess.dto.response.ConsolidationGroupResponseDto createGroup(UUID tenantId, com.bracit.fisprocess.dto.request.CreateConsolidationGroupRequestDto req);
    com.bracit.fisprocess.dto.response.ConsolidationMemberResponseDto addMember(UUID groupId, com.bracit.fisprocess.dto.request.AddConsolidationMemberRequestDto req);
    com.bracit.fisprocess.dto.response.ConsolidationRunResponseDto run(UUID groupId, String period);
    com.bracit.fisprocess.dto.response.ConsolidationGroupResponseDto getGroup(UUID tenantId, UUID id);
    org.springframework.data.domain.Page<com.bracit.fisprocess.dto.response.ConsolidationGroupResponseDto> listGroups(UUID tenantId, org.springframework.data.domain.Pageable pageable);
}
