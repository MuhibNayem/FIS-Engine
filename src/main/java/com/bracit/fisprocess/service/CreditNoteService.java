package com.bracit.fisprocess.service;

import com.bracit.fisprocess.domain.entity.CreditNote;
import com.bracit.fisprocess.dto.request.CreateCreditNoteRequestDto;
import com.bracit.fisprocess.dto.response.CreditNoteResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Orchestrator service for AR Credit Note operations.
 */
public interface CreditNoteService {

    /**
     * Creates a new Credit Note against an existing invoice.
     */
    CreditNote createCreditNote(UUID tenantId, CreateCreditNoteRequestDto request, String performedBy);

    /**
     * Applies a credit note — posts a reversal journal entry to the GL.
     */
    CreditNote applyCreditNote(UUID tenantId, UUID creditNoteId, String performedBy);

    /**
     * Retrieves a credit note by ID, validating tenant ownership.
     */
    CreditNote getCreditNote(UUID tenantId, UUID creditNoteId);

    /**
     * Lists credit notes for a tenant with optional customer filter.
     */
    Page<CreditNoteResponseDto> listCreditNotes(
            UUID tenantId,
            UUID customerId,
            Pageable pageable);
}
