package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.JournalLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for journal lines.
 */
public interface JournalLineRepository extends JpaRepository<JournalLine, UUID> {

    List<JournalLine> findByJournalEntry_Id(UUID journalEntryId);
}
