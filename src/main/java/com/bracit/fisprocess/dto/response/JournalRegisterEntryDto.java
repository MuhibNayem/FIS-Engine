package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A single entry in a Journal Register report.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalRegisterEntryDto {

    private UUID journalEntryId;
    private Long sequenceNumber;
    private LocalDate postedDate;
    private String description;
    private String status;
    private long totalDebits;
    private long totalCredits;
    private String createdBy;
}
