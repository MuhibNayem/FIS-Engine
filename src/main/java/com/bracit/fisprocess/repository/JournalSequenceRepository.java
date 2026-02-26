package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.JournalSequence;
import com.bracit.fisprocess.domain.entity.JournalSequenceId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface JournalSequenceRepository extends JpaRepository<JournalSequence, JournalSequenceId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM JournalSequence s
            WHERE s.id.tenantId = :tenantId
              AND s.id.fiscalYear = :fiscalYear
            """)
    Optional<JournalSequence> findForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("fiscalYear") int fiscalYear);
}
