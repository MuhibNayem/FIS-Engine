package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.JournalWorkflow;
import com.bracit.fisprocess.domain.entity.JournalWorkflowLine;
import com.bracit.fisprocess.domain.enums.JournalStatus;
import com.bracit.fisprocess.domain.enums.JournalWorkflowStatus;
import com.bracit.fisprocess.domain.model.DraftJournalEntry;
import com.bracit.fisprocess.domain.model.DraftJournalLine;
import com.bracit.fisprocess.dto.request.ApproveWorkflowRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.RejectWorkflowRequestDto;
import com.bracit.fisprocess.dto.request.SubmitWorkflowRequestDto;
import com.bracit.fisprocess.dto.response.JournalEntryResponseDto;
import com.bracit.fisprocess.dto.response.JournalWorkflowActionResponseDto;
import com.bracit.fisprocess.exception.ApprovalViolationException;
import com.bracit.fisprocess.exception.DuplicateIdempotencyKeyException;
import com.bracit.fisprocess.exception.InvalidWorkflowStateException;
import com.bracit.fisprocess.exception.JournalWorkflowNotFoundException;
import com.bracit.fisprocess.exception.TenantNotFoundException;
import com.bracit.fisprocess.repository.BusinessEntityRepository;
import com.bracit.fisprocess.repository.JournalEntryRepository;
import com.bracit.fisprocess.repository.JournalWorkflowRepository;
import com.bracit.fisprocess.service.JournalWorkflowService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JournalWorkflowServiceImpl implements JournalWorkflowService {

    private final JournalWorkflowRepository journalWorkflowRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final BusinessEntityRepository businessEntityRepository;
    private final JournalPostingEngine journalPostingEngine;

    @Override
    @Transactional
    public JournalEntryResponseDto createDraft(
            UUID tenantId,
            CreateJournalEntryRequestDto request,
            @Nullable String traceparent) {
        if (journalEntryRepository.existsByTenantIdAndEventId(tenantId, request.getEventId())
                || journalWorkflowRepository.existsByTenantIdAndEventId(tenantId, request.getEventId())) {
            throw new DuplicateIdempotencyKeyException(request.getEventId());
        }

        BusinessEntity tenant = businessEntityRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()));

        JournalWorkflow workflow = JournalWorkflow.builder()
                .tenantId(tenantId)
                .eventId(request.getEventId())
                .postedDate(request.getPostedDate())
                .description(request.getDescription())
                .referenceId(request.getReferenceId())
                .transactionCurrency(request.getTransactionCurrency())
                .createdBy(request.getCreatedBy())
                .status(JournalWorkflowStatus.DRAFT)
                .traceparent(traceparent)
                .build();

        int order = 0;
        for (var line : request.getLines()) {
            workflow.addLine(JournalWorkflowLine.builder()
                    .accountCode(line.getAccountCode())
                    .amountCents(line.getAmountCents())
                    .isCredit(line.isCredit())
                    .dimensions(line.getDimensions())
                    .sortOrder(order++)
                    .build());
        }

        JournalWorkflow saved = journalWorkflowRepository.save(workflow);

        return JournalEntryResponseDto.builder()
                .journalEntryId(saved.getWorkflowId())
                .postedDate(saved.getPostedDate())
                .status(JournalStatus.DRAFT)
                .description(saved.getDescription())
                .referenceId(saved.getReferenceId())
                .transactionCurrency(saved.getTransactionCurrency())
                .baseCurrency(tenant.getBaseCurrency())
                .exchangeRate(BigDecimal.ONE)
                .lineCount(saved.getLines().size())
                .createdBy(saved.getCreatedBy())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public JournalWorkflowActionResponseDto submit(UUID tenantId, UUID workflowId, SubmitWorkflowRequestDto request) {
        JournalWorkflow workflow = journalWorkflowRepository.findByTenantIdAndWorkflowId(tenantId, workflowId)
                .orElseThrow(() -> new JournalWorkflowNotFoundException(workflowId));

        if (workflow.getStatus() != JournalWorkflowStatus.DRAFT) {
            throw new InvalidWorkflowStateException("Only DRAFT workflow entries can be submitted.");
        }

        workflow.setStatus(JournalWorkflowStatus.PENDING_APPROVAL);
        workflow.setSubmittedBy(request.getSubmittedBy());
        workflow.setSubmittedAt(OffsetDateTime.now());

        journalWorkflowRepository.save(workflow);

        return JournalWorkflowActionResponseDto.builder()
                .workflowId(workflow.getWorkflowId())
                .status(workflow.getStatus())
                .message("Workflow submitted for approval.")
                .build();
    }

    @Override
    @Transactional
    public JournalWorkflowActionResponseDto approve(
            UUID tenantId,
            UUID workflowId,
            ApproveWorkflowRequestDto request,
            @Nullable String actorRoleHeader) {
        JournalWorkflow workflow = journalWorkflowRepository.findWithLinesByTenantIdAndWorkflowId(tenantId, workflowId)
                .orElseThrow(() -> new JournalWorkflowNotFoundException(workflowId));

        if (workflow.getStatus() != JournalWorkflowStatus.PENDING_APPROVAL) {
            throw new InvalidWorkflowStateException("Only PENDING_APPROVAL workflow entries can be approved.");
        }
        if (request.getApprovedBy().equalsIgnoreCase(workflow.getCreatedBy())) {
            throw new ApprovalViolationException("Maker-checker violation: approver cannot be the creator.");
        }

        DraftJournalEntry draft = DraftJournalEntry.builder()
                .tenantId(tenantId)
                .eventId(workflow.getEventId())
                .postedDate(workflow.getPostedDate())
                .description(workflow.getDescription())
                .referenceId(workflow.getReferenceId())
                .transactionCurrency(workflow.getTransactionCurrency())
                .baseCurrency(resolveBaseCurrency(tenantId))
                .createdBy(workflow.getCreatedBy())
                .lines(workflow.getLines().stream()
                        .sorted(Comparator.comparingInt(JournalWorkflowLine::getSortOrder))
                        .map(line -> DraftJournalLine.builder()
                                .accountCode(line.getAccountCode())
                                .amountCents(line.getAmountCents())
                                .baseAmountCents(line.getAmountCents())
                                .isCredit(line.isCredit())
                                .dimensions(line.getDimensions())
                                .build())
                        .toList())
                .build();

        JournalEntryResponseDto posted = journalPostingEngine.post(
                tenantId,
                draft,
                actorRoleHeader,
                workflow.getTraceparent());

        workflow.setStatus(JournalWorkflowStatus.APPROVED);
        workflow.setApprovedBy(request.getApprovedBy());
        workflow.setApprovedAt(OffsetDateTime.now());
        workflow.setPostedJournalEntryId(posted.getJournalEntryId());
        journalWorkflowRepository.save(workflow);

        return JournalWorkflowActionResponseDto.builder()
                .workflowId(workflow.getWorkflowId())
                .status(workflow.getStatus())
                .postedJournalEntryId(posted.getJournalEntryId())
                .message("Workflow approved and journal entry posted.")
                .build();
    }

    @Override
    @Transactional
    public JournalWorkflowActionResponseDto reject(UUID tenantId, UUID workflowId, RejectWorkflowRequestDto request) {
        JournalWorkflow workflow = journalWorkflowRepository.findByTenantIdAndWorkflowId(tenantId, workflowId)
                .orElseThrow(() -> new JournalWorkflowNotFoundException(workflowId));

        if (workflow.getStatus() != JournalWorkflowStatus.PENDING_APPROVAL) {
            throw new InvalidWorkflowStateException("Only PENDING_APPROVAL workflow entries can be rejected.");
        }

        workflow.setStatus(JournalWorkflowStatus.REJECTED);
        workflow.setRejectedBy(request.getRejectedBy());
        workflow.setRejectedAt(OffsetDateTime.now());
        workflow.setRejectionReason(request.getReason());
        journalWorkflowRepository.save(workflow);

        return JournalWorkflowActionResponseDto.builder()
                .workflowId(workflow.getWorkflowId())
                .status(workflow.getStatus())
                .message("Workflow rejected.")
                .build();
    }

    private String resolveBaseCurrency(UUID tenantId) {
        return businessEntityRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId.toString()))
                .getBaseCurrency();
    }
}
