package com.bracit.fisprocess.config;

import com.bracit.fisprocess.service.Shard;
import com.bracit.fisprocess.service.ShardContextHolder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@Slf4j
public class ShardDataSourceConfig {

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/fisdb}")
    private String baseUrl;

    @Value("${spring.datasource.username:fis_user}")
    private String username;

    @Value("${spring.datasource.password:}")
    private String password;

    @Value("${spring.datasource.hikari.maximum-pool-size:20}")
    private int maxPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:5}")
    private int minIdle;

    @Value("${fis.sharding.enabled:false}")
    private boolean shardingEnabled;

    @Value("${fis.citus.enabled:false}")
    private boolean citusEnabled;

    @Value("${fis.citus.coordinator-url:}")
    private String citusCoordinatorUrl;

    @Value("${fis.citus.coordinator-host:localhost}")
    private String citusCoordinatorHost;

    @Value("${fis.citus.coordinator-port:5432}")
    private int citusCoordinatorPort;

    @Value("${fis.citus.database:fisdb}")
    private String citusDatabase;

    @Value("${fis.read-replica.enabled:false}")
    private boolean readReplicaEnabled;

    @Value("${fis.read-replica.replica-urls:}")
    private String replicaUrls;

    private final Map<Shard, HikariDataSource> shardDataSources = new ConcurrentHashMap<>();
    private HikariDataSource singleDataSource;
    private HikariDataSource replicaDataSource;

    @Bean
    @Primary
    public DataSource dataSource() {
        if (citusEnabled) {
            log.info("Citus enabled, connecting to coordinator at {}", citusCoordinatorUrl);
            return createCitusDataSource();
        }

        if (!shardingEnabled) {
            log.info("Sharding disabled, using single DataSource");
            singleDataSource = createSingleDataSource();
            if (readReplicaEnabled) {
                log.info("Read replica enabled, wrapping with replica routing");
                return createReplicaRoutingDataSource(singleDataSource);
            }
            return singleDataSource;
        }
        log.info("Sharding enabled, initializing ShardRoutingDataSource");
        initializeShardDataSources();
        if (readReplicaEnabled) {
            log.warn("Both sharding and read-replica enabled - using shard routing only (replica not yet supported with sharding)");
        }
        return createShardRoutingDataSource();
    }

    private DataSource createCitusDataSource() {
        String url = citusCoordinatorUrl.isEmpty()
                ? String.format("jdbc:postgresql://%s:%d/%s", citusCoordinatorHost, citusCoordinatorPort, citusDatabase)
                : citusCoordinatorUrl;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setPoolName("FisPool-Citus");
        config.setAutoCommit(true);
        config.addDataSourceProperty("reWriteBatchedInserts", "true");

        HikariDataSource ds = new HikariDataSource(config);
        log.info("Configured Citus DataSource with URL: {}", url);
        return ds;
    }

    @Bean
    public Map<Shard, DataSource> shardDataSourcesMap() {
        Map<Shard, DataSource> map = new ConcurrentHashMap<>();
        if (shardingEnabled) {
            initializeShardDataSources();
            map.putAll(shardDataSources);
        } else {
            DataSource ds = singleDataSource != null ? singleDataSource : createSingleDataSource();
            for (Shard shard : Shard.values()) {
                map.put(shard, ds);
            }
        }
        return map;
    }

    @Bean
    public DataSource replicaDataSource() {
        if (replicaDataSource != null) {
            return replicaDataSource;
        }
        String[] replicaUrlArray = replicaUrls.isEmpty()
                ? new String[]{"jdbc:postgresql://localhost:5433/fisdb"}
                : replicaUrls.split(",");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(replicaUrlArray[0].trim());
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxPoolSize / 2);
        config.setPoolName("FisPool-Replica");
        config.setAutoCommit(true);
        config.setReadOnly(true);

        replicaDataSource = new HikariDataSource(config);
        log.info("Configured replica DataSource: {}", replicaUrlArray[0].trim());
        return replicaDataSource;
    }

    private synchronized void initializeShardDataSources() {
        if (!shardDataSources.isEmpty()) {
            return;
        }

        String[] urls = {
                "jdbc:postgresql://localhost:5432/fisdb_shard1",
                "jdbc:postgresql://localhost:5432/fisdb_shard2",
                "jdbc:postgresql://localhost:5432/fisdb_shard3"
        };

        for (int i = 0; i < Shard.values().length; i++) {
            Shard shard = Shard.values()[i];
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(urls[i]);
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(maxPoolSize / Shard.values().length);
            config.setMinimumIdle(minIdle / 2);
            config.setPoolName("FisPool-" + shard.name());
            config.setAutoCommit(true);

            HikariDataSource ds = new HikariDataSource(config);
            shardDataSources.put(shard, ds);
            log.info("Initialized DataSource for shard {} with URL: {}", shard.name(), urls[i]);
        }
    }

    private HikariDataSource createSingleDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(baseUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setPoolName("FisPool-Single");
        return new HikariDataSource(config);
    }

    private DataSource createShardRoutingDataSource() {
        ShardRoutingDataSource routingDataSource = new ShardRoutingDataSource();
        routingDataSource.setDefaultTargetDataSource(shardDataSources.get(Shard.SHARD_1));

        Map<Object, Object> dataSourceMap = new HashMap<>();
        for (Map.Entry<Shard, HikariDataSource> entry : shardDataSources.entrySet()) {
            dataSourceMap.put(entry.getKey(), entry.getValue());
        }
        routingDataSource.setTargetDataSources(dataSourceMap);
        routingDataSource.afterPropertiesSet();

        log.info("Configured ShardRoutingDataSource with {} shards", shardDataSources.size());
        return routingDataSource;
    }

    private DataSource createReplicaRoutingDataSource(DataSource primaryDs) {
        ReplicaRoutingDataSource routingDataSource = new ReplicaRoutingDataSource();
        routingDataSource.setDefaultTargetDataSource(primaryDs);

        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put(ReplicaRoutingDataSource.DataSourceType.PRIMARY, primaryDs);
        dataSourceMap.put(ReplicaRoutingDataSource.DataSourceType.REPLICA, replicaDataSource());
        routingDataSource.setTargetDataSources(dataSourceMap);
        routingDataSource.afterPropertiesSet();

        log.info("Configured ReplicaRoutingDataSource");
        return routingDataSource;
    }

    public void closeShardDataSources() {
        for (HikariDataSource ds : shardDataSources.values()) {
            if (ds != null && !ds.isClosed()) {
                ds.close();
            }
        }
        shardDataSources.clear();
        if (singleDataSource != null && !singleDataSource.isClosed()) {
            singleDataSource.close();
        }
        if (replicaDataSource != null && !replicaDataSource.isClosed()) {
            replicaDataSource.close();
        }
    }

    public static class ShardRoutingDataSource extends AbstractRoutingDataSource {

        @Override
        protected Object determineCurrentLookupKey() {
            Shard shard = ShardContextHolder.getCurrentShard();
            if (shard == null) {
                log.debug("No shard set in context, using default");
                return Shard.SHARD_1;
            }
            log.trace("Routing to shard: {}", shard);
            return shard;
        }
    }

    public static class ReplicaRoutingDataSource extends AbstractRoutingDataSource {

        public enum DataSourceType {
            PRIMARY,
            REPLICA
        }

        private static final ThreadLocal<DataSourceType> currentDataSource = ThreadLocal.withInitial(() -> null);

        public static void setCurrentDataSourceType(DataSourceType type) {
            currentDataSource.set(type);
        }

        public static DataSourceType getCurrentDataSourceType() {
            return currentDataSource.get();
        }

        public static void clearCurrentDataSourceType() {
            currentDataSource.remove();
        }

        @Override
        protected Object determineCurrentLookupKey() {
            DataSourceType type = currentDataSource.get();
            if (type != null) {
                return type;
            }
            boolean isReadOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
            return isReadOnly ? DataSourceType.REPLICA : DataSourceType.PRIMARY;
        }
    }
}