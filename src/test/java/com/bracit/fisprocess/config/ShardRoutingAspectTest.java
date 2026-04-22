package com.bracit.fisprocess.config;

import com.bracit.fisprocess.service.Shard;
import com.bracit.fisprocess.service.ShardContextHolder;
import com.bracit.fisprocess.service.ShardRouter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShardRoutingAspect Unit Tests")
class ShardRoutingAspectTest {

    @Mock
    private ShardRouter shardRouter;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private Signature signature;

    private ShardRoutingAspect aspect;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        aspect = new ShardRoutingAspect(shardRouter);
        when(joinPoint.getSignature()).thenReturn(signature);
    }

    @AfterEach
    void tearDown() {
        ShardContextHolder.clear();
    }

    @Test
    @DisplayName("Should route by UUID tenantId")
    void shouldRouteByTenantId() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{TENANT_ID});
        when(joinPoint.proceed()).thenReturn("result");
        when(shardRouter.getShardForTenant(TENANT_ID)).thenReturn(Shard.SHARD_1);

        Object result = aspect.routeShardAuto(joinPoint);

        assertThat(result).isEqualTo("result");
        verify(shardRouter).getShardForTenant(TENANT_ID);
    }

    @Test
    @DisplayName("Should route by String accountCode")
    void shouldRouteByAccountCodeString() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{"1100"});
        when(joinPoint.proceed()).thenReturn("result");
        when(shardRouter.getShardForAccount("1100")).thenReturn(Shard.SHARD_1);

        Object result = aspect.routeShardAuto(joinPoint);

        assertThat(result).isEqualTo("result");
        verify(shardRouter).getShardForAccount("1100");
    }

    @Test
    @DisplayName("Should route by Integer accountCode")
    void shouldRouteByAccountCodeInt() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{2500});
        when(joinPoint.proceed()).thenReturn("result");
        when(shardRouter.getShardForAccount(2500)).thenReturn(Shard.SHARD_2);

        Object result = aspect.routeShardAuto(joinPoint);

        assertThat(result).isEqualTo("result");
        verify(shardRouter).getShardForAccount(2500);
    }

    @Test
    @DisplayName("Should clear context after repository call")
    void shouldClearContextAfterCall() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{TENANT_ID});
        when(joinPoint.proceed()).thenReturn("result");
        when(shardRouter.getShardForTenant(TENANT_ID)).thenReturn(Shard.SHARD_1);

        aspect.routeShardAuto(joinPoint);

        assertThat(ShardContextHolder.getCurrentShard()).isNull();
    }

    @Test
    @DisplayName("Should not route when no args")
    void shouldNotRouteWhenNoArgs() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.routeShardAuto(joinPoint);

        assertThat(result).isEqualTo("result");
        verify(shardRouter, never()).getShardForTenant(any());
        verify(shardRouter, never()).getShardForAccount(anyString());
    }

    @Test
    @DisplayName("Should not route when first arg is null")
    void shouldNotRouteWhenFirstArgNull() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{null});
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.routeShardAuto(joinPoint);

        assertThat(result).isEqualTo("result");
        verify(shardRouter, never()).getShardForTenant(any());
    }
}