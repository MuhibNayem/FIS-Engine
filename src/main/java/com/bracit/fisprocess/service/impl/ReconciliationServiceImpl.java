package com.bracit.fisprocess.service.impl;
import com.bracit.fisprocess.domain.entity.*;
import com.bracit.fisprocess.domain.enums.ReconciliationStatus;
import com.bracit.fisprocess.dto.request.MatchLineRequestDto;
import com.bracit.fisprocess.dto.request.StartReconciliationRequestDto;
import com.bracit.fisprocess.dto.response.OutstandingItemDto;
import com.bracit.fisprocess.dto.response.OutstandingItemsReportDto;
import com.bracit.fisprocess.dto.response.ReconciliationResponseDto;
import com.bracit.fisprocess.exception.ReconciliationNotFoundException;
import com.bracit.fisprocess.repository.*;
import com.bracit.fisprocess.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
@Service @RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {
    private final ReconciliationRepository repo;
    private final ReconciliationMatchRepository matchRepo;
    private final BankStatementLineRepository stmtLineRepo;
    private final BankStatementRepository stmtRepo;
    private final BankAccountRepository bankAcctRepo;
    private final JournalLineRepository journalLineRepo;
    private final ModelMapper mapper;
    @Override @Transactional
    public ReconciliationResponseDto start(UUID tenantId, StartReconciliationRequestDto req, String performedBy) {
        Reconciliation r = Reconciliation.builder()
            .tenantId(tenantId).bankAccountId(req.getBankAccountId())
            .startDate(req.getStartDate()).endDate(req.getEndDate())
            .status(ReconciliationStatus.IN_PROGRESS).build();
        return toResponse(repo.save(r));
    }
    @Override @Transactional
    public List<MatchResult> autoMatch(UUID tenantId, UUID reconciliationId) {
        Reconciliation r = repo.findByTenantIdAndId(tenantId, reconciliationId)
            .orElseThrow(() -> new ReconciliationNotFoundException(reconciliationId));

        // Get the bank account to find its GL account code
        BankAccount bankAccount = bankAcctRepo.findByTenantIdAndId(tenantId, r.getBankAccountId())
            .orElseThrow(() -> new RuntimeException("Bank account not found: " + r.getBankAccountId()));

        if (bankAccount.getGlAccountCode() == null) {
            throw new RuntimeException("Bank account has no GL account code configured: " + r.getBankAccountId());
        }

        // Find the statement for this reconciliation period
        UUID statementId = findStatementIdForAccount(tenantId, r.getBankAccountId(), r.getStartDate(), r.getEndDate());
        if (statementId == null) {
            return List.of(); // No statement found for this period
        }

        // Get all unmatched statement lines
        List<BankStatementLine> unmatchedStatements = stmtLineRepo.findByStatementIdAndMatchedFalse(statementId);

        // Get GL journal lines for the bank account within date range
        List<JournalLineRepository.JournalLineMatchProjection> journalLines =
            journalLineRepo.findJournalLinesForMatching(
                tenantId, bankAccount.getGlAccountCode(), r.getStartDate(), r.getEndDate());

        List<MatchResult> results = new ArrayList<>();

        for (BankStatementLine sl : unmatchedStatements) {
            MatchResult match = findBestMatch(sl, journalLines);
            if (match != null) {
                ReconciliationMatch m = ReconciliationMatch.builder()
                    .reconciliationId(r.getId())
                    .statementLineId(sl.getId())
                    .journalLineId(match.journalLineId())
                    .amount(match.amount())
                    .build();
                matchRepo.save(m);
                sl.setMatched(true);
                sl.setMatchedJournalLineId(match.journalLineId());
                stmtLineRepo.save(sl);
                results.add(match);
            }
        }

        r.setTotalMatched((long) results.size());
        repo.save(r);
        return results;
    }

    private MatchResult findBestMatch(BankStatementLine stmtLine,
            List<JournalLineRepository.JournalLineMatchProjection> journalLines) {
        long targetAmount = Math.abs(stmtLine.getAmount());
        LocalDate stmtDate = stmtLine.getDate();
        String stmtRef = stmtLine.getReference() != null ? stmtLine.getReference().toLowerCase() : "";

        // Priority 1: Exact match - amount same, date within ±1 day, reference partial match
        for (JournalLineRepository.JournalLineMatchProjection jl : journalLines) {
            LocalDate jeDate = jl.getPostedDate();
            String jeRef = jl.getReferenceId() != null ? jl.getReferenceId().toLowerCase() : "";

            boolean amountMatch = Math.abs(jl.getAmount()) == targetAmount;
            boolean dateMatch = jeDate != null && Math.abs(ChronoUnit.DAYS.between(jeDate, stmtDate)) <= 1;
            boolean refMatch = !stmtRef.isEmpty() && (jeRef.contains(stmtRef) || stmtRef.contains(jeRef));

            if (amountMatch && dateMatch && refMatch) {
                return new MatchResult(UUID.randomUUID(), stmtLine.getId(), jl.getJournalLineId(),
                    stmtLine.getAmount(), "AUTO");
            }
        }

        // Priority 2: Fuzzy match - amount same, date within ±7 days
        for (JournalLineRepository.JournalLineMatchProjection jl : journalLines) {
            LocalDate jeDate = jl.getPostedDate();

            boolean amountMatch = Math.abs(jl.getAmount()) == targetAmount;
            boolean dateMatch = jeDate != null && Math.abs(ChronoUnit.DAYS.between(jeDate, stmtDate)) <= 7;

            if (amountMatch && dateMatch) {
                return new MatchResult(UUID.randomUUID(), stmtLine.getId(), jl.getJournalLineId(),
                    stmtLine.getAmount(), "AUTO");
            }
        }

        // Priority 3: Amount only match (fallback)
        for (JournalLineRepository.JournalLineMatchProjection jl : journalLines) {
            if (Math.abs(jl.getAmount()) == targetAmount) {
                return new MatchResult(UUID.randomUUID(), stmtLine.getId(), jl.getJournalLineId(),
                    stmtLine.getAmount(), "AUTO");
            }
        }

        return null; // No match found
    }
    @Override @Transactional
    public MatchResult manualMatch(UUID tenantId, UUID reconciliationId, MatchLineRequestDto req) {
        repo.findByTenantIdAndId(tenantId, reconciliationId)
            .orElseThrow(() -> new ReconciliationNotFoundException(reconciliationId));
        ReconciliationMatch m = ReconciliationMatch.builder()
            .reconciliationId(reconciliationId).statementLineId(req.getStatementLineId())
            .journalLineId(req.getJournalLineId()).amount(req.getAmount()).build();
        matchRepo.save(m);
        stmtLineRepo.findById(req.getStatementLineId()).ifPresent(sl -> { sl.setMatched(true); stmtLineRepo.save(sl); });
        return new MatchResult(m.getId(), req.getStatementLineId(), req.getJournalLineId(), req.getAmount(), "MANUAL");
    }
    @Override @Transactional
    public MatchResult unmatch(UUID tenantId, UUID matchId) {
        ReconciliationMatch m = matchRepo.findById(matchId)
            .orElseThrow(() -> new ReconciliationNotFoundException(matchId));
        stmtLineRepo.findById(m.getStatementLineId()).ifPresent(sl -> { sl.setMatched(false); stmtLineRepo.save(sl); });
        matchRepo.delete(m);
        return new MatchResult(m.getId(), m.getStatementLineId(), m.getJournalLineId(), m.getAmount(), "UNMATCHED");
    }
    @Override @Transactional
    public ReconciliationResponseDto complete(UUID tenantId, UUID reconciliationId, String performedBy) {
        Reconciliation r = repo.findByTenantIdAndId(tenantId, reconciliationId)
            .orElseThrow(() -> new ReconciliationNotFoundException(reconciliationId));
        r.setStatus(ReconciliationStatus.COMPLETED);
        r.setReconciledAt(OffsetDateTime.now());
        r.setReconciledBy(performedBy);
        return toResponse(repo.save(r));
    }
    @Override
    public ReconciliationResponseDto getById(UUID tenantId, UUID id) {
        return repo.findByTenantIdAndId(tenantId, id)
            .map(this::toResponse).orElseThrow(() -> new ReconciliationNotFoundException(id));
    }
    @Override
    public Page<ReconciliationResponseDto> list(UUID tenantId, @Nullable UUID bankAccountId, @Nullable String status, Pageable pageable) {
        ReconciliationStatus s = status != null ? ReconciliationStatus.valueOf(status) : null;
        return repo.findByTenantIdWithFilters(tenantId, bankAccountId, s, pageable).map(this::toResponse);
    }
    @Override
    public OutstandingItemsReportDto getOutstandingItems(UUID tenantId, UUID bankAccountId, LocalDate asOfDate) {
        // Get bank account to find GL account code
        BankAccount bankAccount = bankAcctRepo.findByTenantIdAndId(tenantId, bankAccountId)
            .orElseThrow(() -> new RuntimeException("Bank account not found: " + bankAccountId));

        // Find statements for this bank account
        LocalDate startDate = asOfDate.minusMonths(1);
        LocalDate endDate = asOfDate;

        List<BankStatement> statements = stmtRepo.findByTenantIdAndBankAccountIdAndDateRange(
            tenantId, bankAccountId, startDate, endDate);

        // Get all unmatched statement lines
        List<OutstandingItemDto> stmtItems = new ArrayList<>();
        long stmtTotal = 0;

        for (BankStatement stmt : statements) {
            List<BankStatementLine> unmatchedLines = stmtLineRepo.findByStatementIdAndMatchedFalse(stmt.getId());
            for (BankStatementLine sl : unmatchedLines) {
                stmtItems.add(OutstandingItemDto.builder()
                    .id(sl.getId().toString())
                    .date(sl.getDate() != null ? sl.getDate().toString() : "")
                    .description(sl.getDescription() != null ? sl.getDescription() : "")
                    .amount(Math.abs(sl.getAmount()))
                    .type("STATEMENT")
                    .build());
                stmtTotal += Math.abs(sl.getAmount());
            }
        }

        // Get unmatched journal lines for the GL account
        List<JournalLineRepository.JournalLineMatchProjection> unmatchedJl =
            journalLineRepo.findJournalLinesForMatching(
                tenantId, bankAccount.getGlAccountCode(), startDate, endDate);

        List<OutstandingItemDto> jlItems = new ArrayList<>();
        long jlTotal = 0;

        for (JournalLineRepository.JournalLineMatchProjection jl : unmatchedJl) {
            jlItems.add(OutstandingItemDto.builder()
                .id(jl.getJournalLineId().toString())
                .date(jl.getPostedDate() != null ? jl.getPostedDate().toString() : "")
                .description(jl.getDescription() != null ? jl.getDescription() : "")
                .amount(Math.abs(jl.getAmount()))
                .type("JOURNAL")
                .build());
            jlTotal += Math.abs(jl.getAmount());
        }

        return OutstandingItemsReportDto.builder()
            .bankAccountId(bankAccountId.toString())
            .asOfDate(asOfDate.toString())
            .outstandingStatementLines(stmtItems)
            .unmatchedJournalLines(jlItems)
            .totalOutstanding(stmtTotal + jlTotal)
            .build();
    }
    private UUID findStatementIdForAccount(UUID tenantId, UUID bankAccountId, LocalDate start, LocalDate end) {
        // Find statements for this account within the reconciliation date range
        List<BankStatement> statements = stmtRepo.findByTenantIdAndBankAccountIdAndDateRange(
                tenantId, bankAccountId, start, end);
        if (statements == null || statements.isEmpty()) {
            return null;
        }
        // Return the most recent statement that falls within or closest to the date range
        return statements.get(0).getId();
    }
    private ReconciliationResponseDto toResponse(Reconciliation r) {
        ReconciliationResponseDto dto = mapper.map(r, ReconciliationResponseDto.class);
        dto.setId(r.getId().toString()); dto.setTenantId(r.getTenantId().toString());
        dto.setBankAccountId(r.getBankAccountId().toString());
        dto.setStartDate(r.getStartDate().toString()); dto.setEndDate(r.getEndDate().toString());
        dto.setStatus(r.getStatus().name());
        if (r.getReconciledAt() != null) dto.setReconciledAt(r.getReconciledAt().toString());
        return dto;
    }
}
