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

    public static final String JOURNAL_WRITE_EXCHANGE = "fis.journal.write.exchange";
    public static final String JOURNAL_WRITE_QUEUE = "fis.journal.write.queue";
    public static final String JOURNAL_WRITE_REPLY_QUEUE = "fis.journal.write.reply.queue";
    public static final String JOURNAL_WRITE_DLQ_QUEUE = "fis.journal.write.dlq.queue";
    public static final String JOURNAL_WRITE_DLQ_ROUTING_KEY = "fis.journal.write.dlq";
    public static final String JOURNAL_WRITE_ROUTING_KEY = "fis.journal.write";

    private RabbitMqTopology() {
    }
}
