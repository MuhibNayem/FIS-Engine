package com.bracit.fisprocess.service.impl;

import com.bracit.fisprocess.service.Shard;
import com.bracit.fisprocess.service.ShardContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ShardRoutingServiceImplTest {

    private ShardRoutingServiceImpl shardRoutingService;

    @BeforeEach
    void setUp() {
        shardRoutingService = new ShardRoutingServiceImpl();
        shardRoutingService.setShardingEnabled(true);
        shardRoutingService.setDefaultShard(Shard.SHARD_1);
        ShardContextHolder.clear();
    }

    @AfterEach
    void tearDown() {
        ShardContextHolder.clear();
    }

    @Test
    void shouldReturnShard1ForAccount1000() {
        Shard shard = shardRoutingService.getShardForAccount("1000");

        assertThat(shard).isEqualTo(Shard.SHARD_1);
        assertThat(ShardContextHolder.getCurrentShard()).isEqualTo(Shard.SHARD_1);
    }

    @Test
    void shouldReturnShard1ForAccount4999() {
        Shard shard = shardRoutingService.getShardForAccount("4999");

        assertThat(shard).isEqualTo(Shard.SHARD_1);
    }

    @Test
    void shouldReturnShard2ForAccount5000() {
        Shard shard = shardRoutingService.getShardForAccount("5000");

        assertThat(shard).isEqualTo(Shard.SHARD_2);
    }

    @Test
    void shouldReturnShard2ForAccount9999() {
        Shard shard = shardRoutingService.getShardForAccount("9999");

        assertThat(shard).isEqualTo(Shard.SHARD_2);
    }

    @Test
    void shouldReturnShard3ForAccount10000() {
        Shard shard = shardRoutingService.getShardForAccount("10000");

        assertThat(shard).isEqualTo(Shard.SHARD_3);
    }

    @Test
    void shouldReturnShard3ForAccount999999() {
        Shard shard = shardRoutingService.getShardForAccount("999999");

        assertThat(shard).isEqualTo(Shard.SHARD_3);
    }

    @Test
    void shouldReturnDefaultShardWhenShardingDisabled() {
        shardRoutingService.setShardingEnabled(false);

        Shard shard = shardRoutingService.getShardForAccount("10000");

        assertThat(shard).isEqualTo(Shard.SHARD_1);
    }

    @Test
    void shouldSetTenantContextOnGetShardForTenant() {
        UUID tenantId = UUID.randomUUID();

        Shard shard = shardRoutingService.getShardForTenant(tenantId);

        assertThat(shard).isNotNull();
        assertThat(ShardContextHolder.getCurrentShard()).isEqualTo(shard);
        assertThat(ShardContextHolder.getCurrentTenantId()).isEqualTo(tenantId);
    }

    @Test
    void shouldDistributeTenantsAcrossShards() {
        int[] shardCounts = {0, 0, 0};
        int tenantCount = 100;

        for (int i = 0; i < tenantCount; i++) {
            UUID tenantId = UUID.randomUUID();
            Shard shard = shardRoutingService.getShardForTenant(tenantId);
            shardCounts[shard.ordinal()]++;
        }

        assertThat(shardCounts[0]).isGreaterThan(0);
        assertThat(shardCounts[1]).isGreaterThan(0);
        assertThat(shardCounts[2]).isGreaterThan(0);
    }

    @Test
    void shouldHandleNonNumericAccountCode() {
        Shard shard = shardRoutingService.getShardForAccount("ACCT-1000");

        assertThat(shard).isNotNull();
    }

    @Test
    void shouldReturnCorrectShardIndex() {
        assertThat(shardRoutingService.getShardIndex(Shard.SHARD_1)).isEqualTo(0);
        assertThat(shardRoutingService.getShardIndex(Shard.SHARD_2)).isEqualTo(1);
        assertThat(shardRoutingService.getShardIndex(Shard.SHARD_3)).isEqualTo(2);
    }
}