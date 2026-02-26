package com.bracit.fisprocess.service;

import com.bracit.fisprocess.dto.request.ApproveWorkflowRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.RejectWorkflowRequestDto;
import com.bracit.fisprocess.dto.request.SubmitWorkflowRequestDto;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.dto.response.JournalWorkflowActionResponseDto;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public interface JournalWorkflowService {

    JournalEntryResponseDto createDraft(
            UUID tenantId,
            CreateJournalEntryRequestDto request,
            @Nullable String traceparent);

    JournalWorkflowActionResponseDto submit(
            UUID tenantId,
            UUID workflowId,
            SubmitWorkflowRequestDto request);

    JournalWorkflowActionResponseDto approve(
            UUID tenantId,
            UUID workflowId,
            ApproveWorkflowRequestDto request,
            @Nullable String actorRoleHeader);

    JournalWorkflowActionResponseDto reject(
            UUID tenantId,
            UUID workflowId,
            RejectWorkflowRequestDto request);
}
