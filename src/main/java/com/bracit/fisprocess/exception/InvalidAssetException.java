package com.bracit.fisprocess.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public class InvalidAssetException extends FisBusinessException {
    public InvalidAssetException(UUID assetId, String message) {
        super("Invalid asset operation on asset " + assetId + ": " + message, HttpStatus.BAD_REQUEST, "/problems/invalid-asset");
    }
}