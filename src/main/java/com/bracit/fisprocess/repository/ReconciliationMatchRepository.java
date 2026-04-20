package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.ReconciliationMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface ReconciliationMatchRepository extends JpaRepository<ReconciliationMatch, UUID> {
    List<ReconciliationMatch> findByReconciliationId(UUID reconciliationId);
    List<ReconciliationMatch> findByStatementLineId(UUID statementLineId);
}
