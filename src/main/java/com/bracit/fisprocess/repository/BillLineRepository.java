package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.BillLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link BillLine} persistence operations.
 */
@Repository
public interface BillLineRepository extends JpaRepository<BillLine, UUID> {

    /**
     * Finds all lines for a bill ordered by sort order.
     */
    List<BillLine> findByBillIdOrderBySortOrder(UUID billId);
}
