package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.JournalSequence;
import com.bracit.fisprocess.domain.entity.JournalSequenceId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
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

    @Modifying
    @Query(value = """
            INSERT INTO fis_journal_sequence (tenant_id, fiscal_year, next_value)
            VALUES (:tenantId, :fiscalYear, 1)
            ON CONFLICT (tenant_id, fiscal_year) DO NOTHING
            """, nativeQuery = true)
    int initializeIfAbsent(@Param("tenantId") UUID tenantId, @Param("fiscalYear") int fiscalYear);

    @Modifying
    @Query(value = """
            UPDATE fis_journal_sequence
            SET next_value = next_value + :count
            WHERE tenant_id = :tenantId AND fiscal_year = :fiscalYear
            RETURNING next_value - :count as allocated_start
            """, nativeQuery = true)
    Optional<Long> allocateAndGetStart(
            @Param("tenantId") UUID tenantId,
            @Param("fiscalYear") int fiscalYear,
            @Param("count") int count);

    @Query(value = """
            SELECT next_value
            FROM fis_journal_sequence
            WHERE tenant_id = :tenantId AND fiscal_year = :fiscalYear
            FOR UPDATE
            """, nativeQuery = true)
    Optional<Long> getNextValueForUpdate(
            @Param("tenantId") UUID tenantId,
            @Param("fiscalYear") int fiscalYear);
}