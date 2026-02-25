package com.bracit.fisprocess.config;

/**
 * RabbitMQ naming constants.
 */
public final class RabbitMqTopology {
    public static final String EVENTS_EXCHANGE = "fis.events.exchange";
    public static final String INGESTION_QUEUE = "fis.ingestion.queue";
    public static final String DLX_EXCHANGE = "fis.dlx.exchange";
    public static final String INGESTION_DLQ_QUEUE = "fis.ingestion.dlq.queue";
    public static final String INGESTION_DLQ_ROUTING_KEY = "fis.ingestion.dlq";
    public static final String DOMAIN_EXCHANGE = "fis.domain.exchange";

    private RabbitMqTopology() {
    }
}
