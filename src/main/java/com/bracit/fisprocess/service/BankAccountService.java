package com.bracit.fisprocess.service;
import com.bracit.fisprocess.domain.enums.BankAccountStatus;
import com.bracit.fisprocess.dto.request.RegisterBankAccountRequestDto;
import com.bracit.fisprocess.dto.response.BankAccountResponseDto;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
public interface BankAccountService {
    BankAccountResponseDto register(UUID tenantId, RegisterBankAccountRequestDto req, String performedBy);
    BankAccountResponseDto close(UUID tenantId, UUID id, String performedBy);
    BankAccountResponseDto getById(UUID tenantId, UUID id);
    Page<BankAccountResponseDto> list(UUID tenantId, @Nullable BankAccountStatus status, Pageable pageable);
}
