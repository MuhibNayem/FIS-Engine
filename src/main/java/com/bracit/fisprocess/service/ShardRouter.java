package com.bracit.fisprocess.service;

import java.util.UUID;

public interface ShardRouter {

    Shard getShardForAccount(String accountCode);

    Shard getShardForAccount(int accountCode);

    Shard getShardForTenant(UUID tenantId);

    int getShardIndex(Shard shard);
}