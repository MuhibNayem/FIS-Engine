package com.bracit.fisprocess.service.impl;
import com.bracit.fisprocess.domain.entity.BankStatement;
import com.bracit.fisprocess.domain.entity.BankStatementLine;
import com.bracit.fisprocess.domain.enums.BankStatementStatus;
import com.bracit.fisprocess.dto.request.ImportBankStatementRequestDto;
import com.bracit.fisprocess.dto.response.BankStatementLineResponseDto;
import com.bracit.fisprocess.dto.response.BankStatementResponseDto;
import com.bracit.fisprocess.exception.BankStatementNotFoundException;
import com.bracit.fisprocess.repository.BankStatementRepository;
import com.bracit.fisprocess.repository.BankStatementLineRepository;
import com.bracit.fisprocess.service.BankStatementService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
@Service @RequiredArgsConstructor
public class BankStatementServiceImpl implements BankStatementService {
    private final BankStatementRepository repo;
    private final BankStatementLineRepository lineRepo;
    private final ModelMapper mapper;
    @Override @Transactional
    public BankStatementResponseDto importStatement(UUID tenantId, ImportBankStatementRequestDto req) {
        BankStatement stmt = BankStatement.builder()
            .tenantId(tenantId).bankAccountId(req.getBankAccountId())
            .statementDate(req.getStatementDate()).openingBalance(req.getOpeningBalance())
            .closingBalance(req.getClosingBalance()).importedBy(req.getImportedBy())
            .status(BankStatementStatus.IMPORTED).build();
        int idx = 0;
        for (var ld : req.getLines()) {
            BankStatementLine line = BankStatementLine.builder()
                .date(ld.getDate()).description(ld.getDescription())
                .amount(ld.getAmount()).reference(ld.getReference()).build();
            stmt.addLine(line);
        }
        return toResponse(repo.save(stmt));
    }
    @Override
    public BankStatementResponseDto getById(UUID tenantId, UUID id) {
        return repo.findWithLinesByTenantIdAndId(tenantId, id)
            .map(this::toResponse).orElseThrow(() -> new BankStatementNotFoundException(id));
    }
    @Override
    public Page<BankStatementResponseDto> list(UUID tenantId, @Nullable UUID bankAccountId, Pageable pageable) {
        return repo.findByTenantIdWithFilters(tenantId, bankAccountId, pageable).map(this::toResponseSimple);
    }
    @Override
    public List<BankStatementLineResponseDto> getLines(UUID tenantId, UUID statementId) {
        repo.findByTenantIdAndId(tenantId, statementId)
            .orElseThrow(() -> new BankStatementNotFoundException(statementId));
        return lineRepo.findByStatementIdOrderByDateAsc(statementId).stream()
            .map(this::toLineResponse).collect(Collectors.toList());
    }
    private BankStatementResponseDto toResponse(BankStatement s) {
        BankStatementResponseDto dto = toResponseSimple(s);
        dto.setLines(s.getLines().stream().map(this::toLineResponse).collect(Collectors.toList()));
        return dto;
    }
    private BankStatementResponseDto toResponseSimple(BankStatement s) {
        BankStatementResponseDto dto = mapper.map(s, BankStatementResponseDto.class);
        dto.setId(s.getId().toString()); dto.setTenantId(s.getTenantId().toString());
        dto.setBankAccountId(s.getBankAccountId().toString());
        dto.setStatementDate(s.getStatementDate().toString());
        dto.setStatus(s.getStatus().name()); dto.setCreatedAt(s.getCreatedAt().toString());
        return dto;
    }
    private BankStatementLineResponseDto toLineResponse(BankStatementLine l) {
        BankStatementLineResponseDto dto = mapper.map(l, BankStatementLineResponseDto.class);
        dto.setId(l.getId().toString()); dto.setDate(l.getDate().toString());
        dto.setMatchedJournalLineId(l.getMatchedJournalLineId() != null ? l.getMatchedJournalLineId().toString() : null);
        return dto;
    }
}
