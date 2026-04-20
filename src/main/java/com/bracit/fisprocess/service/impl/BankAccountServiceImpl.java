package com.bracit.fisprocess.service.impl;
import com.bracit.fisprocess.domain.entity.BankAccount;
import com.bracit.fisprocess.domain.enums.BankAccountStatus;
import com.bracit.fisprocess.dto.request.RegisterBankAccountRequestDto;
import com.bracit.fisprocess.dto.response.BankAccountResponseDto;
import com.bracit.fisprocess.exception.BankAccountNotFoundException;
import com.bracit.fisprocess.repository.BankAccountRepository;
import com.bracit.fisprocess.service.BankAccountService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Service @RequiredArgsConstructor
public class BankAccountServiceImpl implements BankAccountService {
    private final BankAccountRepository repo;
    private final ModelMapper mapper;
    @Override @Transactional
    public BankAccountResponseDto register(UUID tenantId, RegisterBankAccountRequestDto req, String performedBy) {
        if (repo.existsByTenantIdAndAccountNumber(tenantId, req.getAccountNumber()))
            throw new BankAccountNotFoundException(UUID.randomUUID());
        BankAccount acct = mapper.map(req, BankAccount.class);
        acct.setTenantId(tenantId);
        acct.setStatus(BankAccountStatus.ACTIVE);
        return toResponse(repo.save(acct));
    }
    @Override @Transactional
    public BankAccountResponseDto close(UUID tenantId, UUID id, String performedBy) {
        BankAccount acct = repo.findByTenantIdAndId(tenantId, id)
            .orElseThrow(() -> new BankAccountNotFoundException(id));
        acct.setStatus(BankAccountStatus.CLOSED);
        return toResponse(repo.save(acct));
    }
    @Override
    public BankAccountResponseDto getById(UUID tenantId, UUID id) {
        return repo.findByTenantIdAndId(tenantId, id)
            .map(this::toResponse).orElseThrow(() -> new BankAccountNotFoundException(id));
    }
    @Override
    public Page<BankAccountResponseDto> list(UUID tenantId, @Nullable BankAccountStatus status, Pageable pageable) {
        return repo.findByTenantIdWithFilters(tenantId, status, pageable).map(this::toResponse);
    }
    private BankAccountResponseDto toResponse(BankAccount a) {
        BankAccountResponseDto dto = mapper.map(a, BankAccountResponseDto.class);
        dto.setId(a.getId().toString()); dto.setTenantId(a.getTenantId().toString());
        dto.setStatus(a.getStatus().name()); dto.setCreatedAt(a.getCreatedAt().toString());
        return dto;
    }
}
