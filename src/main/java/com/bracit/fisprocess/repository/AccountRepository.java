package com.bracit.fisprocess.repository;

import com.bracit.fisprocess.domain.entity.Account;
import com.bracit.fisprocess.domain.enums.AccountType;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Account} persistence operations.
 * <p>
 * All queries are tenant-scoped to enforce data isolation.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

  /**
   * Finds an account by its unique code within a tenant.
   */
  Optional<Account> findByTenantIdAndCode(UUID tenantId, String code);

  /**
   * Checks if an account code already exists for a given tenant.
   */
  boolean existsByTenantIdAndCode(UUID tenantId, String code);

  /**
   * Lists accounts for a tenant with optional filters for account type and active
   * status.
   */
  @Query("""
      SELECT a FROM Account a
      WHERE a.tenantId = :tenantId
        AND (:accountType IS NULL OR a.accountType = :accountType)
        AND (:isActive IS NULL OR a.isActive = :isActive)
      """)
  Page<Account> findByTenantIdWithFilters(
      @Param("tenantId") UUID tenantId,
      @Param("accountType") @Nullable AccountType accountType,
      @Param("isActive") @Nullable Boolean isActive,
      Pageable pageable);

  /**
   * Acquires a pessimistic row-level lock on the account and atomically updates
   * the balance. Uses native SQL for SELECT ... FOR UPDATE semantics.
   *
   * @return number of rows updated (0 if account not found, 1 if successful)
   */
  @org.springframework.data.jpa.repository.Modifying
  @Query(value = """
      UPDATE fis_account
      SET current_balance = current_balance + :delta,
          updated_at = NOW()
      WHERE account_id = :accountId
      """, nativeQuery = true)
  int lockAndUpdateBalance(@Param("accountId") UUID accountId, @Param("delta") Long delta);

  @Query(value = """
      SELECT account_type, COALESCE(SUM(current_balance), 0)
      FROM fis_account
      WHERE tenant_id = :tenantId
      GROUP BY account_type
      """, nativeQuery = true)
  List<Object[]> sumBalancesByType(@Param("tenantId") UUID tenantId);

  /**
   * Computes the aggregated balance of an account and all its descendants
   * using a recursive CTE that traverses the parent→child hierarchy.
   *
   * @param tenantId the tenant UUID
   * @param code     the root account code
   * @return the sum of current_balance across the root and all descendants
   */
  @Query(value = """
      WITH RECURSIVE account_tree AS (
          SELECT account_id, parent_account_id, current_balance
          FROM fis_account WHERE tenant_id = :tenantId AND code = :code
          UNION ALL
          SELECT a.account_id, a.parent_account_id, a.current_balance
          FROM fis_account a
          JOIN account_tree t ON a.parent_account_id = t.account_id
      )
      SELECT COALESCE(SUM(current_balance), 0) FROM account_tree
      """, nativeQuery = true)
  Long findAggregatedBalance(@Param("tenantId") UUID tenantId, @Param("code") String code);

  /**
   * Finds all accounts of the given types with non-zero current balance for a
   * tenant.
   * Used by year-end close to identify Revenue/Expense accounts to zero out.
   */
  List<Account> findByTenantIdAndAccountTypeInAndCurrentBalanceNot(
      UUID tenantId, List<AccountType> accountTypes, Long balance);
}
