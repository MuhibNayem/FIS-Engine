package com.bracit.fisprocess.controller;

import com.bracit.fisprocess.annotation.ApiVersion;
import com.bracit.fisprocess.domain.entity.CreditNote;
import com.bracit.fisprocess.dto.request.CreateCreditNoteRequestDto;
import com.bracit.fisprocess.dto.response.CreditNoteResponseDto;
import com.bracit.fisprocess.service.CreditNoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for AR Credit Note management.
 * <p>
 * All endpoints require the {@code X-Tenant-Id} header to scope operations
 * to a specific tenant.
 */
@RestController
@RequestMapping("/v1/ar/credit-notes")
@RequiredArgsConstructor
@ApiVersion(1)
public class CreditNoteController {

    private final CreditNoteService creditNoteService;
    private final ModelMapper modelMapper;

    /**
     * Creates a new Credit Note against an existing invoice.
     *
     * @param tenantId the tenant UUID
     * @param request  the credit note details
     * @return 201 Created with the new credit note details
     */
    @PostMapping
    public ResponseEntity<CreditNoteResponseDto> createCreditNote(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy,
            @Valid @RequestBody CreateCreditNoteRequestDto request) {
        CreditNote creditNote = creditNoteService.createCreditNote(tenantId, request, performedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponseDto(creditNote));
    }

    /**
     * Applies a credit note — posts a reversal journal entry to the GL.
     *
     * @param tenantId     the tenant UUID
     * @param creditNoteId the credit note UUID
     * @return 200 OK with the applied credit note details
     */
    @PostMapping("/{creditNoteId}/apply")
    public ResponseEntity<CreditNoteResponseDto> applyCreditNote(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID creditNoteId,
            @RequestHeader(value = "X-Actor-Id", defaultValue = "system") String performedBy) {
        CreditNote creditNote = creditNoteService.applyCreditNote(tenantId, creditNoteId, performedBy);
        return ResponseEntity.ok(toResponseDto(creditNote));
    }

    /**
     * Retrieves a credit note by ID.
     *
     * @param tenantId     the tenant UUID
     * @param creditNoteId the credit note UUID
     * @return 200 OK with credit note details
     */
    @GetMapping("/{creditNoteId}")
    public ResponseEntity<CreditNoteResponseDto> getCreditNote(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID creditNoteId) {
        CreditNote creditNote = creditNoteService.getCreditNote(tenantId, creditNoteId);
        return ResponseEntity.ok(toResponseDto(creditNote));
    }

    /**
     * Lists credit notes for a tenant with optional customer filter.
     *
     * @param tenantId   the tenant UUID
     * @param customerId optional customer filter
     * @param pageable   pagination parameters
     * @return 200 OK with paginated credit note list
     */
    @GetMapping
    public ResponseEntity<Page<CreditNoteResponseDto>> listCreditNotes(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) @Nullable UUID customerId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<CreditNoteResponseDto> response = creditNoteService.listCreditNotes(tenantId, customerId, pageable);
        return ResponseEntity.ok(response);
    }

    private CreditNoteResponseDto toResponseDto(CreditNote creditNote) {
        CreditNoteResponseDto dto = modelMapper.map(creditNote, CreditNoteResponseDto.class);
        dto.setCreditNoteId(creditNote.getCreditNoteId());
        dto.setStatus(creditNote.getStatus().name());
        return dto;
    }
}
