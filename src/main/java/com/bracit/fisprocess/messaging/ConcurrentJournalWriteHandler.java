package com.bracit.fisprocess.messaging;

import com.bracit.fisprocess.config.RabbitMqTopology;
import com.bracit.fisprocess.service.Shard;
import com.bracit.fisprocess.service.ShardAwareExecutorService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import com.rabbitmq.client.Channel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConcurrentJournalWriteHandler {

    private final JournalWriteWorker worker;
    private final ShardAwareExecutorService executorService;
    private final RabbitTemplate rabbitTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${fis.worker.batch-size:50}")
    private int batchSize;

    @Value("${fis.worker.batch-timeout-ms:100}")
    private long batchTimeoutMs;

    @Value("${fis.backpressure.enabled:true}")
    private boolean backpressureEnabled;

    @Value("${fis.backpressure.queue-threshold:5000}")
    private int queueThreshold;

    @Value("${fis.backpressure.reject-threshold:8000}")
    private int rejectThreshold;

    private final AtomicLong processedCount = new AtomicLong(0);

    @RabbitListener(queues = RabbitMqTopology.JOURNAL_WRITE_QUEUE, concurrency = "1-10")
    public void handleMessages(List<JournalWriteMessage> messages, Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        if (messages == null || messages.isEmpty()) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        int totalMessages = messages.size();
        meterRegistry.counter("fis.worker.batch.received").increment();
        Gauge.builder("fis.worker.batch.size", () -> totalMessages).register(meterRegistry);

        log.info("Received batch of {} journal write messages", totalMessages);

        if (backpressureEnabled && executorService.getTotalQueueDepth() > rejectThreshold) {
            log.warn("System overloaded, rejecting batch of {} messages", totalMessages);
            meterRegistry.counter("fis.worker.batch.rejected").increment();
            channel.basicNack(deliveryTag, false, true);
            return;
        }

        if (totalMessages == 1) {
            worker.consume(messages.get(0), channel, deliveryTag);
            return;
        }

        processBatch(messages, channel, deliveryTag);
    }

    private void processBatch(List<JournalWriteMessage> messages, Channel channel, long deliveryTag) {
        List<JournalWriteMessage> failedMessages = new ArrayList<>();

        for (JournalWriteMessage message : messages) {
            try {
                Shard shard = Shard.forTenant(message.getTenantId());

                if (backpressureEnabled && executorService.isOverloaded(shard, queueThreshold)) {
                    log.warn("Shard {} overloaded, requeuing message {}", shard, message.getTrackingId());
                    meterRegistry.counter("fis.worker.message.requeued", "shard", shard.name()).increment();
                    failedMessages.add(message);
                    continue;
                }

                executorService.executeToShard(shard, () -> {
                    try {
                        worker.consume(message, null, 0);
                    } catch (Exception e) {
                        log.error("Error processing message {} in shard worker",
                                message.getTrackingId(), e);
                    }
                });

                processedCount.incrementAndGet();

            } catch (Exception e) {
                log.error("Error dispatching message {} to shard executor",
                        message.getTrackingId(), e);
                failedMessages.add(message);
            }
        }

        try {
            if (failedMessages.isEmpty()) {
                channel.basicAck(deliveryTag, false);
            } else {
                int nackCount = failedMessages.size();
                log.warn("Nacking {} failed messages", nackCount);
                channel.basicNack(deliveryTag, false, true);
            }
        } catch (IOException e) {
            log.error("Failed to ack/nack batch", e);
        }

        meterRegistry.gauge("fis.worker.processed.total", processedCount, AtomicLong::get);
    }

    public long getProcessedCount() {
        return processedCount.get();
    }
}