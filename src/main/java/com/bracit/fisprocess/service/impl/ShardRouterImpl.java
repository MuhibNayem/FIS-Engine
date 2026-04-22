package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.service.Shard;
import com.bracit.fisprocess.service.ShardRouter;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ShardRouterImpl implements ShardRouter {

    @Override
    public Shard getShardForAccount(String accountCode) {
        return Shard.forAccountCode(accountCode);
    }

    @Override
    public Shard getShardForAccount(int accountCode) {
        return Shard.forAccountCode(accountCode);
    }

    @Override
    public Shard getShardForTenant(UUID tenantId) {
        return Shard.forTenant(tenantId);
    }

    @Override
    public int getShardIndex(Shard shard) {
        return shard.ordinal();
    }
}