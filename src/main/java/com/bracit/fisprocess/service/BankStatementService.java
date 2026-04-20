package com.bracit.fisprocess.service;
import com.bracit.fisprocess.dto.request.ImportBankStatementRequestDto;
import com.bracit.fisprocess.dto.response.BankStatementLineResponseDto;
import com.bracit.fisprocess.dto.response.BankStatementResponseDto;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;
public interface BankStatementService {
    BankStatementResponseDto importStatement(UUID tenantId, ImportBankStatementRequestDto req);
    BankStatementResponseDto getById(UUID tenantId, UUID id);
    Page<BankStatementResponseDto> list(UUID tenantId, @Nullable UUID bankAccountId, Pageable pageable);
    List<BankStatementLineResponseDto> getLines(UUID tenantId, UUID statementId);
}
