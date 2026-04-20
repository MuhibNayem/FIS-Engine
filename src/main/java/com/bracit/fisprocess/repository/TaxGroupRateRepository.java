package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.TaxGroupRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link TaxGroupRate} persistence operations.
 */
@Repository
public interface TaxGroupRateRepository extends JpaRepository<TaxGroupRate, UUID> {

    /**
     * Finds all group rates for a tax group.
     */
    @Query("SELECT tgr FROM TaxGroupRate tgr JOIN FETCH tgr.group WHERE tgr.group.taxGroupId = :groupId")
    List<TaxGroupRate> findByGroupId(@Param("groupId") UUID groupId);

    /**
     * Deletes all group rates for a tax group.
     */
    void deleteByGroupTaxGroupId(UUID groupId);
}
