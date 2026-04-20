package com.bracit.fisprocess.exception;
import org.springframework.http.HttpStatus;
import java.util.UUID;
public class WarehouseNotFoundException extends FisBusinessException {
    public WarehouseNotFoundException(UUID id) { super("Warehouse not found: " + id, HttpStatus.NOT_FOUND, "/problems/warehouse-not-found"); }
}
