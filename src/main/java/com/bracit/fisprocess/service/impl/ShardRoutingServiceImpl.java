package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.service.Shard;
import com.bracit.fisprocess.service.ShardContextHolder;
import com.bracit.fisprocess.service.ShardRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class ShardRoutingServiceImpl implements ShardRouter {

    @Value("${fis.sharding.enabled:false}")
    private boolean shardingEnabled;

    @Value("${fis.sharding.default-shard:SHARD_1}")
    private Shard defaultShard;

    @Override
    public Shard getShardForAccount(String accountCode) {
        Shard shard = determineShardForAccount(accountCode);
        ShardContextHolder.setCurrentShard(shard);
        return shard;
    }

    @Override
    public Shard getShardForAccount(int accountCode) {
        Shard shard = determineShardForAccount(accountCode);
        ShardContextHolder.setCurrentShard(shard);
        return shard;
    }

    @Override
    public Shard getShardForTenant(UUID tenantId) {
        Shard shard = determineShardForTenant(tenantId);
        ShardContextHolder.setCurrentShard(shard);
        ShardContextHolder.setCurrentTenantId(tenantId);
        return shard;
    }

    @Override
    public int getShardIndex(Shard shard) {
        return shard.ordinal();
    }

    public boolean isShardingEnabled() {
        return shardingEnabled;
    }

    private Shard determineShardForAccount(String accountCode) {
        if (!shardingEnabled) {
            return defaultShard;
        }
        try {
            return Shard.forAccountCode(accountCode);
        } catch (Exception e) {
            log.warn("Could not determine shard for account {}: {}", accountCode, e.getMessage());
            return defaultShard;
        }
    }

    private Shard determineShardForAccount(int accountCode) {
        if (!shardingEnabled) {
            return defaultShard;
        }
        try {
            return Shard.forAccountCode(accountCode);
        } catch (Exception e) {
            log.warn("Could not determine shard for account code {}: {}", accountCode, e.getMessage());
            return defaultShard;
        }
    }

    private Shard determineShardForTenant(UUID tenantId) {
        if (!shardingEnabled) {
            return defaultShard;
        }
        return Shard.forTenant(tenantId);
    }

    public void setShardingEnabled(boolean enabled) {
        this.shardingEnabled = enabled;
    }

    public void setDefaultShard(Shard shard) {
        this.defaultShard = shard;
    }
}