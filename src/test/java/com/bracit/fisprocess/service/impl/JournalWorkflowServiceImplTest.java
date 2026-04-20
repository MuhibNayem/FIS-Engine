package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.BusinessEntity;
import com.bracit.fisprocess.domain.entity.JournalWorkflow;
import com.bracit.fisprocess.domain.entity.JournalWorkflowLine;
import com.bracit.fisprocess.domain.enums.JournalStatus;
import com.bracit.fisprocess.domain.enums.JournalWorkflowStatus;
import com.bracit.fisprocess.dto.request.ApproveWorkflowRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JournalWorkflowServiceImpl Unit Tests")
class JournalWorkflowServiceImplTest {

    @Mock private JournalWorkflowRepository journalWorkflowRepository;
    @Mock private JournalEntryRepository journalEntryRepository;
    @Mock private BusinessEntityRepository businessEntityRepository;
    @Mock private JournalPostingEngine journalPostingEngine;

    private JournalWorkflowServiceImpl service;
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new JournalWorkflowServiceImpl(
                journalWorkflowRepository, journalEntryRepository, businessEntityRepository, journalPostingEngine);
    }

    private BusinessEntity buildTenant() {
        return BusinessEntity.builder()
                .tenantId(TENANT_ID)
                .name("Test Corp")
                .baseCurrency("USD")
                .build();
    }

    private CreateJournalEntryRequestDto buildCreateRequest() {
        return CreateJournalEntryRequestDto.builder()
                .eventId("EVT-WF-001")
                .postedDate(LocalDate.of(2026, 4, 13))
                .transactionCurrency("USD")
                .createdBy("alice")
                .lines(List.of(
                        JournalLineRequestDto.builder().accountCode("CASH").amountCents(1000L).isCredit(false).build(),
                        JournalLineRequestDto.builder().accountCode("REV").amountCents(1000L).isCredit(true).build()))
                .build();
    }

    private JournalWorkflow buildDraftWorkflow() {
        JournalWorkflow wf = JournalWorkflow.builder()
                .tenantId(TENANT_ID)
                .eventId("EVT-WF-001")
                .postedDate(LocalDate.of(2026, 4, 13))
                .effectiveDate(LocalDate.of(2026, 4, 13))
                .transactionDate(LocalDate.of(2026, 4, 13))
                .description("Test workflow")
                .referenceId("REF-WF")
                .transactionCurrency("USD")
                .createdBy("alice")
                .status(JournalWorkflowStatus.DRAFT)
                .build();
        wf.addLine(JournalWorkflowLine.builder()
                .accountCode("CASH").amountCents(1000L).isCredit(false).sortOrder(0).build());
        wf.addLine(JournalWorkflowLine.builder()
                .accountCode("REV").amountCents(1000L).isCredit(true).sortOrder(1).build());
        return wf;
    }

    private void setWorkflowId(JournalWorkflow wf, UUID id) {
        try {
            java.lang.reflect.Field field = JournalWorkflow.class.getDeclaredField("workflowId");
            field.setAccessible(true);
            field.set(wf, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("createDraft")
    class CreateDraft {

        @Test
        @DisplayName("should create draft workflow")
        void shouldCreateDraft() {
            when(journalEntryRepository.existsByTenantIdAndEventId(TENANT_ID, "EVT-WF-001")).thenReturn(false);
            when(journalWorkflowRepository.existsByTenantIdAndEventId(TENANT_ID, "EVT-WF-001")).thenReturn(false);
            when(businessEntityRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenant()));
            when(journalWorkflowRepository.save(any(JournalWorkflow.class))).thenAnswer(inv -> {
                JournalWorkflow wf = inv.getArgument(0);
                // Simulate ID generation
                java.lang.reflect.Field field;
                try {
                    field = JournalWorkflow.class.getDeclaredField("workflowId");
                    field.set(wf, UUID.randomUUID());
                } catch (Exception ignored) {}
                return wf;
            });

            var result = service.createDraft(TENANT_ID, buildCreateRequest(), null);

            assertThat(result.getStatus()).isEqualTo(JournalStatus.DRAFT);
            assertThat(result.getLineCount()).isEqualTo(2);
            assertThat(result.getBaseCurrency()).isEqualTo("USD");
        }

        @Test
        @DisplayName("should throw DuplicateIdempotencyKeyException when event exists in journal entries")
        void shouldThrowWhenEventInJournalEntries() {
            when(journalEntryRepository.existsByTenantIdAndEventId(TENANT_ID, "EVT-WF-001")).thenReturn(true);

            assertThatThrownBy(() -> service.createDraft(TENANT_ID, buildCreateRequest(), null))
                    .isInstanceOf(DuplicateIdempotencyKeyException.class);
        }

        @Test
        @DisplayName("should throw DuplicateIdempotencyKeyException when event exists in workflows")
        void shouldThrowWhenEventInWorkflows() {
            when(journalEntryRepository.existsByTenantIdAndEventId(TENANT_ID, "EVT-WF-001")).thenReturn(false);
            when(journalWorkflowRepository.existsByTenantIdAndEventId(TENANT_ID, "EVT-WF-001")).thenReturn(true);

            assertThatThrownBy(() -> service.createDraft(TENANT_ID, buildCreateRequest(), null))
                    .isInstanceOf(DuplicateIdempotencyKeyException.class);
        }

        @Test
        @DisplayName("should throw TenantNotFoundException when tenant not found")
        void shouldThrowWhenTenantNotFound() {
            when(journalEntryRepository.existsByTenantIdAndEventId(TENANT_ID, "EVT-WF-001")).thenReturn(false);
            when(journalWorkflowRepository.existsByTenantIdAndEventId(TENANT_ID, "EVT-WF-001")).thenReturn(false);
            when(businessEntityRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createDraft(TENANT_ID, buildCreateRequest(), null))
                    .isInstanceOf(TenantNotFoundException.class);
        }

        @Test
        @DisplayName("should throw DuplicateIdempotencyKeyException on DataIntegrityViolationException for event_id")
        void shouldThrowOnDuplicateEventIdConstraint() {
            when(journalEntryRepository.existsByTenantIdAndEventId(TENANT_ID, "EVT-WF-001")).thenReturn(false);
            when(journalWorkflowRepository.existsByTenantIdAndEventId(TENANT_ID, "EVT-WF-001")).thenReturn(false);
            when(businessEntityRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenant()));
            when(journalWorkflowRepository.save(any(JournalWorkflow.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"event_id\""));

            assertThatThrownBy(() -> service.createDraft(TENANT_ID, buildCreateRequest(), null))
                    .isInstanceOf(DuplicateIdempotencyKeyException.class);
        }
    }

    @Nested
    @DisplayName("submit")
    class Submit {

        @Test
        @DisplayName("should submit draft workflow for approval")
        void shouldSubmitDraft() {
            UUID workflowId = UUID.randomUUID();
            JournalWorkflow wf = buildDraftWorkflow();
            setWorkflowId(wf, workflowId);
            when(journalWorkflowRepository.findByTenantIdAndWorkflowId(TENANT_ID, workflowId))
                    .thenReturn(Optional.of(wf));
            when(journalWorkflowRepository.save(any(JournalWorkflow.class))).thenAnswer(inv -> inv.getArgument(0));

            SubmitWorkflowRequestDto request = SubmitWorkflowRequestDto.builder()
                    .submittedBy("alice")
                    .build();

            JournalWorkflowActionResponseDto result = service.submit(TENANT_ID, workflowId, request);

            assertThat(result.getStatus()).isEqualTo(JournalWorkflowStatus.PENDING_APPROVAL);
            assertThat(result.getMessage()).contains("submitted");
        }

        @Test
        @DisplayName("should throw InvalidWorkflowStateException when not in DRAFT")
        void shouldThrowWhenNotDraft() {
            UUID workflowId = UUID.randomUUID();
            JournalWorkflow wf = buildDraftWorkflow();
            wf.setStatus(JournalWorkflowStatus.PENDING_APPROVAL);
            when(journalWorkflowRepository.findByTenantIdAndWorkflowId(TENANT_ID, workflowId))
                    .thenReturn(Optional.of(wf));

            assertThatThrownBy(() -> service.submit(TENANT_ID, workflowId,
                    SubmitWorkflowRequestDto.builder().submittedBy("alice").build()))
                    .isInstanceOf(InvalidWorkflowStateException.class)
                    .hasMessageContaining("DRAFT");
        }

        @Test
        @DisplayName("should throw JournalWorkflowNotFoundException when workflow not found")
        void shouldThrowWhenWorkflowNotFound() {
            UUID workflowId = UUID.randomUUID();
            when(journalWorkflowRepository.findByTenantIdAndWorkflowId(TENANT_ID, workflowId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.submit(TENANT_ID, workflowId,
                    SubmitWorkflowRequestDto.builder().submittedBy("alice").build()))
                    .isInstanceOf(JournalWorkflowNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("approve")
    class Approve {

        @Test
        @DisplayName("should approve workflow and post journal entry")
        void shouldApprove() {
            UUID workflowId = UUID.randomUUID();
            JournalWorkflow wf = buildDraftWorkflow();
            wf.setStatus(JournalWorkflowStatus.PENDING_APPROVAL);
            setWorkflowId(wf, workflowId);
            when(journalWorkflowRepository.findWithLinesForUpdate(TENANT_ID, workflowId))
                    .thenReturn(Optional.of(wf));
            when(businessEntityRepository.findById(TENANT_ID)).thenReturn(Optional.of(buildTenant()));
            JournalEntryResponseDto posted = JournalEntryResponseDto.builder()
                    .journalEntryId(UUID.randomUUID())
                    .status(JournalStatus.POSTED)
                    .build();
            when(journalPostingEngine.post(any(), any(), any(), any())).thenReturn(posted);
            when(journalWorkflowRepository.save(any(JournalWorkflow.class))).thenAnswer(inv -> inv.getArgument(0));

            ApproveWorkflowRequestDto request = ApproveWorkflowRequestDto.builder()
                    .approvedBy("bob")
                    .build();

            JournalWorkflowActionResponseDto result = service.approve(TENANT_ID, workflowId, request, null);

            assertThat(result.getStatus()).isEqualTo(JournalWorkflowStatus.APPROVED);
            assertThat(result.getPostedJournalEntryId()).isEqualTo(posted.getJournalEntryId());
            assertThat(result.getMessage()).contains("approved");
        }

        @Test
        @DisplayName("should throw ApprovalViolationException when approver is creator (maker-checker)")
        void shouldThrowWhenMakerCheckerViolation() {
            UUID workflowId = UUID.randomUUID();
            JournalWorkflow wf = buildDraftWorkflow();
            wf.setStatus(JournalWorkflowStatus.PENDING_APPROVAL);
            setWorkflowId(wf, workflowId);
            when(journalWorkflowRepository.findWithLinesForUpdate(TENANT_ID, workflowId))
                    .thenReturn(Optional.of(wf));

            ApproveWorkflowRequestDto request = ApproveWorkflowRequestDto.builder()
                    .approvedBy("alice") // Same as createdBy
                    .build();

            assertThatThrownBy(() -> service.approve(TENANT_ID, workflowId, request, null))
                    .isInstanceOf(ApprovalViolationException.class)
                    .hasMessageContaining("Maker-checker violation");
        }

        @Test
        @DisplayName("should throw InvalidWorkflowStateException when not PENDING_APPROVAL")
        void shouldThrowWhenNotPendingApproval() {
            UUID workflowId = UUID.randomUUID();
            JournalWorkflow wf = buildDraftWorkflow();
            wf.setStatus(JournalWorkflowStatus.DRAFT);
            when(journalWorkflowRepository.findWithLinesForUpdate(TENANT_ID, workflowId))
                    .thenReturn(Optional.of(wf));

            assertThatThrownBy(() -> service.approve(TENANT_ID, workflowId,
                    ApproveWorkflowRequestDto.builder().approvedBy("bob").build(), null))
                    .isInstanceOf(InvalidWorkflowStateException.class)
                    .hasMessageContaining("PENDING_APPROVAL");
        }

        @Test
        @DisplayName("should throw TenantNotFoundException when tenant not found for base currency")
        void shouldThrowWhenTenantNotFoundForCurrency() {
            UUID workflowId = UUID.randomUUID();
            JournalWorkflow wf = buildDraftWorkflow();
            wf.setStatus(JournalWorkflowStatus.PENDING_APPROVAL);
            setWorkflowId(wf, workflowId);
            when(journalWorkflowRepository.findWithLinesForUpdate(TENANT_ID, workflowId))
                    .thenReturn(Optional.of(wf));
            when(businessEntityRepository.findById(TENANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.approve(TENANT_ID, workflowId,
                    ApproveWorkflowRequestDto.builder().approvedBy("bob").build(), null))
                    .isInstanceOf(TenantNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("reject")
    class Reject {

        @Test
        @DisplayName("should reject workflow")
        void shouldReject() {
            UUID workflowId = UUID.randomUUID();
            JournalWorkflow wf = buildDraftWorkflow();
            wf.setStatus(JournalWorkflowStatus.PENDING_APPROVAL);
            setWorkflowId(wf, workflowId);
            when(journalWorkflowRepository.findByTenantIdAndWorkflowId(TENANT_ID, workflowId))
                    .thenReturn(Optional.of(wf));
            when(journalWorkflowRepository.save(any(JournalWorkflow.class))).thenAnswer(inv -> inv.getArgument(0));

            RejectWorkflowRequestDto request = RejectWorkflowRequestDto.builder()
                    .rejectedBy("bob")
                    .reason("Incorrect amounts")
                    .build();

            JournalWorkflowActionResponseDto result = service.reject(TENANT_ID, workflowId, request);

            assertThat(result.getStatus()).isEqualTo(JournalWorkflowStatus.REJECTED);
            assertThat(result.getMessage()).contains("rejected");
        }

        @Test
        @DisplayName("should throw InvalidWorkflowStateException when not PENDING_APPROVAL")
        void shouldThrowWhenRejectNotPending() {
            UUID workflowId = UUID.randomUUID();
            JournalWorkflow wf = buildDraftWorkflow();
            wf.setStatus(JournalWorkflowStatus.DRAFT);
            when(journalWorkflowRepository.findByTenantIdAndWorkflowId(TENANT_ID, workflowId))
                    .thenReturn(Optional.of(wf));

            assertThatThrownBy(() -> service.reject(TENANT_ID, workflowId,
                    RejectWorkflowRequestDto.builder().rejectedBy("bob").reason("bad").build()))
                    .isInstanceOf(InvalidWorkflowStateException.class);
        }

        @Test
        @DisplayName("should throw JournalWorkflowNotFoundException when workflow not found")
        void shouldThrowWhenRejectNotFound() {
            UUID workflowId = UUID.randomUUID();
            when(journalWorkflowRepository.findByTenantIdAndWorkflowId(TENANT_ID, workflowId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.reject(TENANT_ID, workflowId,
                    RejectWorkflowRequestDto.builder().rejectedBy("bob").reason("bad").build()))
                    .isInstanceOf(JournalWorkflowNotFoundException.class);
        }
    }
}
