package com.bracit.fisprocess.exception;
import org.springframework.http.HttpStatus;
import java.util.UUID;
public class AssetCategoryNotFoundException extends FisBusinessException {
    public AssetCategoryNotFoundException(UUID id) { super("Asset category not found: " + id, HttpStatus.NOT_FOUND, "/problems/asset-category-not-found"); }
}
