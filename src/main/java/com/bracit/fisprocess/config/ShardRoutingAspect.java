package com.bracit.fisprocess.config;

import com.bracit.fisprocess.service.Shard;
import com.bracit.fisprocess.service.ShardContextHolder;
import com.bracit.fisprocess.service.ShardRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class ShardRoutingAspect {

    private final ShardRouter shardRouter;

    @Pointcut("execution(* com.bracit.fisprocess.repository.*Repository.*(..))")
    public void repositoryMethods() {}

    @Around("repositoryMethods() && @annotation(shardAware)")
    public Object routeShardExplicit(ProceedingJoinPoint joinPoint, ShardAware shardAware) throws Throwable {
        return routeAndExecute(joinPoint, "explicit");
    }

    @Around("repositoryMethods()")
    public Object routeShardAuto(ProceedingJoinPoint joinPoint) throws Throwable {
        return routeAndExecute(joinPoint, "auto");
    }

    private Object routeAndExecute(ProceedingJoinPoint joinPoint, String routingType) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        Shard shard = null;
        if (args.length > 0 && args[0] != null) {
            shard = determineShard(args[0], methodName);
            if (shard != null) {
                log.trace("Routing {} ({}) to shard {}", methodName, routingType, shard);
                ShardContextHolder.setCurrentShard(shard);
            }
        }

        try {
            return joinPoint.proceed();
        } finally {
            ShardContextHolder.clear();
        }
    }

    private Shard determineShard(Object firstArg, String methodName) {
        try {
            if (firstArg instanceof UUID tenantId) {
                return shardRouter.getShardForTenant(tenantId);
            } else if (firstArg instanceof String accountCode) {
                return shardRouter.getShardForAccount(accountCode);
            } else if (firstArg instanceof Integer accountCode) {
                return shardRouter.getShardForAccount(accountCode);
            }
        } catch (Exception e) {
            log.debug("Could not determine shard for {} in method {}: {}",
                    firstArg, methodName, e.getMessage());
        }
        return null;
    }
}