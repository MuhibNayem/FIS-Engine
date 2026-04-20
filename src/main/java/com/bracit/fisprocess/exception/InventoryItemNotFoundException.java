package com.bracit.fisprocess.exception;
import org.springframework.http.HttpStatus;
import java.util.UUID;
public class InventoryItemNotFoundException extends FisBusinessException {
    public InventoryItemNotFoundException(UUID id) { super("Inventory item not found: " + id, HttpStatus.NOT_FOUND, "/problems/inventory-item-not-found"); }
}
