package com.bracit.fisprocess.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Journal Register report — chronological listing of all journal entries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JournalRegisterReportDto {

    private ReportMetadataDto metadata;
    private List<JournalRegisterEntryDto> entries;
    private long totalEntries;
    private int page;
    private int size;
    private long totalPages;
}
