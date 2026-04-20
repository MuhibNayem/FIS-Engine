package com.bracit.fisprocess.repository;
import com.bracit.fisprocess.domain.entity.BankStatementLine;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface BankStatementLineRepository extends JpaRepository<BankStatementLine, UUID> {
    List<BankStatementLine> findByStatementIdOrderByDateAsc(UUID statementId);
    List<BankStatementLine> findByStatementIdAndMatchedFalse(UUID statementId);
}
