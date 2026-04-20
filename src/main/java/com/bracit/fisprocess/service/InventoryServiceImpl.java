package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.domain.entity.InventoryItem;
import com.bracit.fisprocess.domain.entity.InventoryMovement;
import com.bracit.fisprocess.domain.entity.InventoryValuationRun;
import com.bracit.fisprocess.domain.entity.Warehouse;
import com.bracit.fisprocess.domain.enums.InventoryMovementType;
import com.bracit.fisprocess.domain.enums.InventoryValuationMethod;
import com.bracit.fisprocess.dto.request.CreateInventoryItemRequestDto;
import com.bracit.fisprocess.dto.request.CreateJournalEntryRequestDto;
import com.bracit.fisprocess.dto.request.JournalLineRequestDto;
import com.bracit.fisprocess.dto.request.RecordInventoryMovementRequestDto;
import com.bracit.fisprocess.dto.request.CreateWarehouseRequestDto;
import com.bracit.fisprocess.dto.response.InventoryItemResponseDto;
import com.bracit.fisprocess.dto.response.InventoryMovementResponseDto;
import com.bracit.fisprocess.dto.response.InventoryValuationResponseDto;
import com.bracit.fisprocess.dto.response.InventoryValuationResponseDto.InventoryValuationItemDto;
import com.bracit.fisprocess.dto.response.WarehouseResponseDto;
import com.bracit.fisprocess.exception.InventoryItemNotFoundException;
import com.bracit.fisprocess.exception.WarehouseNotFoundException;
import com.bracit.fisprocess.repository.InventoryItemRepository;
import com.bracit.fisprocess.repository.InventoryMovementRepository;
import com.bracit.fisprocess.repository.InventoryValuationRunRepository;
import com.bracit.fisprocess.repository.WarehouseRepository;
import com.bracit.fisprocess.service.InventoryService;
import com.bracit.fisprocess.service.JournalEntryService;
import com.bracit.fisprocess.service.PeriodValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {

    private final WarehouseRepository whRepo;
    private final InventoryItemRepository itemRepo;
    private final InventoryMovementRepository moveRepo;
    private final InventoryValuationRunRepository valRepo;
    private final JournalEntryService journalEntryService;
    private final PeriodValidationService periodValidationService;
    private final ModelMapper mapper;

    @Value("${fis.inventory.inventory-account:INVENTORY}")
    private String inventoryAccount;

    @Value("${fis.inventory.cogs-account:COGS}")
    private String cogsAccount;

    @Value("${fis.inventory.grn-clearing-account:GRN_CLEARING}")
    private String grnClearingAccount;

    @Value("${fis.inventory.adjustment-gain-account:INVENTORY_GAIN}")
    private String adjustmentGainAccount;

    @Value("${fis.inventory.adjustment-loss-account:INVENTORY_LOSS}")
    private String adjustmentLossAccount;

    @Override
    @Transactional
    public WarehouseResponseDto createWarehouse(UUID tenantId, CreateWarehouseRequestDto req) {
        var wh = mapper.map(req, Warehouse.class);
        wh.setTenantId(tenantId);
        var saved = whRepo.save(wh);
        log.info("Created warehouse '{}' for tenant '{}'", saved.getCode(), tenantId);
        return mapper.map(saved, WarehouseResponseDto.class);
    }

    @Override
    @Transactional
    public InventoryItemResponseDto createItem(UUID tenantId, CreateInventoryItemRequestDto req) {
        var item = mapper.map(req, InventoryItem.class);
        item.setTenantId(tenantId);
        var saved = itemRepo.save(item);
        log.info("Created inventory item '{}' for tenant '{}'", saved.getSku(), tenantId);
        return mapper.map(saved, InventoryItemResponseDto.class);
    }

    @Override
    @Transactional
    public InventoryMovementResponseDto recordMovement(UUID tenantId,
            RecordInventoryMovementRequestDto req) {
        // FR-11: Validate accounting period is OPEN before posting to GL
        LocalDate refDate = req.getReferenceDate() != null ? req.getReferenceDate() : LocalDate.now();
        periodValidationService.validatePostingAllowed(tenantId, refDate, null);

        var move = mapper.map(req, InventoryMovement.class);
        move.setTenantId(tenantId);
        long unitCost = req.getUnitCost() != null ? req.getUnitCost() : 0;
        move.setUnitCost(unitCost);
        move.setTotalCost(req.getQuantity() * unitCost);

        var saved = moveRepo.save(move);

        // Post GL journal based on movement type
        postInventoryMovementJournal(tenantId, saved, req.getType(), req.getReferenceDate(), "system");

        log.info("Recorded inventory movement '{}' type '{}' for tenant '{}'",
                saved.getId(), req.getType(), tenantId);
        return mapper.map(saved, InventoryMovementResponseDto.class);
    }

    @Override
    public InventoryValuationResponseDto getValuation(UUID tenantId, String period) {
        List<InventoryItem> items = itemRepo.findByTenantId(tenantId);

        long totalValue = 0;
        List<InventoryValuationItemDto> itemDtos = new ArrayList<>();

        for (InventoryItem item : items) {
            long avgCost = calculateAverageCost(item);
            long qtyOnHand = item.getQuantityOnHand() != null ? item.getQuantityOnHand() : 0;
            long totalItemValue = avgCost * qtyOnHand;
            totalValue += totalItemValue;

            itemDtos.add(InventoryValuationItemDto.builder()
                    .sku(item.getSku())
                    .name(item.getName())
                    .quantity(qtyOnHand)
                    .unitCost(avgCost)
                    .totalValue(totalItemValue)
                    .build());
        }

        // Persist the valuation run
        InventoryValuationRun valRun = InventoryValuationRun.builder()
                .tenantId(tenantId)
                .period(period)
                .runDate(LocalDate.now())
                .totalValue(totalValue)
                .status("COMPLETED")
                .build();
        valRepo.save(valRun);

        return InventoryValuationResponseDto.builder()
                .period(period)
                .totalValue(totalValue)
                .items(itemDtos)
                .build();
    }

    @Override
    public WarehouseResponseDto getWarehouse(UUID tenantId, UUID id) {
        return whRepo.findByTenantIdAndId(tenantId, id)
                .map(w -> mapper.map(w, WarehouseResponseDto.class))
                .orElseThrow(() -> new WarehouseNotFoundException(id));
    }

    @Override
    public InventoryItemResponseDto getItem(UUID tenantId, UUID id) {
        return itemRepo.findByTenantIdAndId(tenantId, id)
                .map(i -> mapper.map(i, InventoryItemResponseDto.class))
                .orElseThrow(() -> new InventoryItemNotFoundException(id));
    }

    private long calculateAverageCost(InventoryItem item) {
        long qtyOnHand = item.getQuantityOnHand() != null ? item.getQuantityOnHand() : 0;
        if (qtyOnHand <= 0) {
            return item.getStandardCost() != null ? item.getStandardCost() : 0;
        }

        if (item.getCostMethod() == InventoryValuationMethod.FIFO) {
            return calculateFifoCost(item, qtyOnHand);
        }

        if (item.getCostMethod() == InventoryValuationMethod.WEIGHTED_AVERAGE) {
            long totalValue = item.getTotalValue() != null ? item.getTotalValue() : 0;
            return qtyOnHand > 0 ? totalValue / qtyOnHand : 0;
        }

        return item.getStandardCost() != null ? item.getStandardCost() : 0;
    }

    private long calculateFifoCost(InventoryItem item, long quantityNeeded) {
        // Get receipt movements ordered by reference date (oldest first)
        List<InventoryMovement> receipts = moveRepo.findByItemIdAndTypeOrderByReferenceDateAsc(
                item.getId(), InventoryMovementType.RECEIPT);

        if (receipts == null || receipts.isEmpty()) {
            return item.getStandardCost() != null ? item.getStandardCost() : 0;
        }

        long totalCost = 0;
        long remainingQty = quantityNeeded;

        for (InventoryMovement receipt : receipts) {
            if (remainingQty <= 0) break;

            long availableFromLayer = receipt.getQuantity() != null ? receipt.getQuantity() : 0;
            if (availableFromLayer <= 0) continue;

            long consumedFromLayer = Math.min(remainingQty, availableFromLayer);
            long unitCost = receipt.getUnitCost() != null ? receipt.getUnitCost() : 0;
            totalCost += consumedFromLayer * unitCost;
            remainingQty -= consumedFromLayer;
        }

        if (remainingQty > 0) {
            // Not enough receipt layers - fall back to standard cost for remainder
            log.warn("FIFO layer exhausted for item {} - using standard cost for {} units",
                    item.getSku(), remainingQty);
            totalCost += remainingQty * (item.getStandardCost() != null ? item.getStandardCost() : 0);
            remainingQty = 0;
        }

        return quantityNeeded > 0 ? totalCost / quantityNeeded : 0;
    }

    private void postInventoryMovementJournal(UUID tenantId, InventoryMovement movement,
            InventoryMovementType type, LocalDate referenceDate, String performedBy) {
        try {
            String eventId = "INV-" + movement.getId() + "-" + type.name();

            List<JournalLineRequestDto> journalLines = new ArrayList<>();

            switch (type) {
                case RECEIPT:
                    // DEBIT: Inventory, CREDIT: GRN Clearing
                    journalLines.add(JournalLineRequestDto.builder()
                            .accountCode(inventoryAccount)
                            .amountCents(movement.getTotalCost())
                            .isCredit(false)
                            .build());
                    journalLines.add(JournalLineRequestDto.builder()
                            .accountCode(grnClearingAccount)
                            .amountCents(movement.getTotalCost())
                            .isCredit(true)
                            .build());
                    break;

                case ISSUE:
                    // DEBIT: COGS, CREDIT: Inventory
                    journalLines.add(JournalLineRequestDto.builder()
                            .accountCode(cogsAccount)
                            .amountCents(movement.getTotalCost())
                            .isCredit(false)
                            .build());
                    journalLines.add(JournalLineRequestDto.builder()
                            .accountCode(inventoryAccount)
                            .amountCents(movement.getTotalCost())
                            .isCredit(true)
                            .build());
                    break;

                case ADJUSTMENT:
                    long cost = movement.getTotalCost();
                    if (cost != 0) {
                        if (cost > 0) {
                            // Increase inventory: DEBIT Inventory, CREDIT Gain
                            journalLines.add(JournalLineRequestDto.builder()
                                    .accountCode(inventoryAccount)
                                    .amountCents(cost)
                                    .isCredit(false)
                                    .build());
                            journalLines.add(JournalLineRequestDto.builder()
                                    .accountCode(adjustmentGainAccount)
                                    .amountCents(cost)
                                    .isCredit(true)
                                    .build());
                        } else {
                            // Decrease inventory: DEBIT Loss, CREDIT Inventory
                            journalLines.add(JournalLineRequestDto.builder()
                                    .accountCode(adjustmentLossAccount)
                                    .amountCents(Math.abs(cost))
                                    .isCredit(false)
                                    .build());
                            journalLines.add(JournalLineRequestDto.builder()
                                    .accountCode(inventoryAccount)
                                    .amountCents(Math.abs(cost))
                                    .isCredit(true)
                                    .build());
                        }
                    }
                    break;

                case TRANSFER:
                    // No GL impact for transfers between warehouses
                    return;
            }

            journalEntryService.createJournalEntry(
                    tenantId,
                    CreateJournalEntryRequestDto.builder()
                            .eventId(eventId)
                            .postedDate(LocalDate.now())
                            .effectiveDate(referenceDate != null ? referenceDate : LocalDate.now())
                            .transactionDate(referenceDate != null ? referenceDate : LocalDate.now())
                            .description("Inventory " + type.name() + " - " + movement.getId())
                            .referenceId("INV-MOVE-" + movement.getId())
                            .transactionCurrency("USD")
                            .createdBy(performedBy)
                            .lines(journalLines)
                            .build());

            log.info("Posted inventory movement journal for '{}' type '{}', amount: {}",
                    movement.getId(), type, movement.getTotalCost());
        } catch (Exception ex) {
            log.error("Failed to post GL journal for inventory movement '{}': {}",
                    movement.getId(), ex.getMessage(), ex);
            throw new RuntimeException("Failed to post inventory journal to GL: " + ex.getMessage());
        }
    }
}