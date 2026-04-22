package com.bracit.fisprocess.service;

import java.util.UUID;

public enum Shard {
    SHARD_1(1000, 4999, "Shard 1: Accounts 1000-4999"),
    SHARD_2(5000, 9999, "Shard 2: Accounts 5000-9999"),
    SHARD_3(10000, Integer.MAX_VALUE, "Shard 3: Accounts 10000+");

    private final int rangeStart;
    private final int rangeEnd;
    private final String description;

    Shard(int rangeStart, int rangeEnd, String description) {
        this.rangeStart = rangeStart;
        this.rangeEnd = rangeEnd;
        this.description = description;
    }

    public int getRangeStart() {
        return rangeStart;
    }

    public int getRangeEnd() {
        return rangeEnd;
    }

    public String getDescription() {
        return description;
    }

    public static Shard forAccountCode(String accountCode) {
        try {
            int code = Integer.parseInt(accountCode.replaceAll("[^0-9]", ""));
            return forAccountCode(code);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid account code format: " + accountCode);
        }
    }

    public static Shard forAccountCode(int accountCode) {
        for (Shard shard : values()) {
            if (accountCode >= shard.rangeStart && accountCode <= shard.rangeEnd) {
                return shard;
            }
        }
        throw new IllegalArgumentException("Account code out of range: " + accountCode);
    }

    public static Shard forTenant(UUID tenantId) {
        int hash = Math.abs(tenantId.hashCode());
        Shard[] shards = values();
        return shards[hash % shards.length];
    }

    public static int getShardCount() {
        return values().length;
    }
}