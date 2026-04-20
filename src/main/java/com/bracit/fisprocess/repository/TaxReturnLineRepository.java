package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.TaxReturnLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link TaxReturnLine} persistence operations.
 */
@Repository
public interface TaxReturnLineRepository extends JpaRepository<TaxReturnLine, UUID> {

    /**
     * Finds all lines for a tax return.
     */
    List<TaxReturnLine> findByTaxReturnId(UUID taxReturnId);
}
