package com.bracit.fisprocess.exception;
import org.springframework.http.HttpStatus;
import java.util.UUID;
public class AssetNotFoundException extends FisBusinessException {
    public AssetNotFoundException(UUID id) { super("Fixed asset not found: " + id, HttpStatus.NOT_FOUND, "/problems/fixed-asset-not-found"); }
}
