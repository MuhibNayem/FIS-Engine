package com.bracit.fisprocess.service;

/**
 * Computes deterministic SHA-256 payload hashes.
 */
public interface PayloadHashService {

    String sha256Hex(Object payload);
}
