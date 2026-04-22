package com.bracit.fisprocess.service;

import com.bracit.fisprocess.service.Shard;

import java.util.UUID;

public final class ShardContextHolder {

    private static final ThreadLocal<Shard> currentShard = new ThreadLocal<>();
    private static final ThreadLocal<UUID> currentTenantId = new ThreadLocal<>();

    private ShardContextHolder() {
    }

    public static void setCurrentShard(Shard shard) {
        currentShard.set(shard);
    }

    public static Shard getCurrentShard() {
        return currentShard.get();
    }

    public static void setCurrentTenantId(UUID tenantId) {
        currentTenantId.set(tenantId);
    }

    public static UUID getCurrentTenantId() {
        return currentTenantId.get();
    }

    public static void clear() {
        currentShard.remove();
        currentTenantId.remove();
    }

    public static Shard forCurrentRequest(String accountCode) {
        Shard shard = currentShard.get();
        if (shard != null) {
            return shard;
        }
        return Shard.forAccountCode(accountCode);
    }
}