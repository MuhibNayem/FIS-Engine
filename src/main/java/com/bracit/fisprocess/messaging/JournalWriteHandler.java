package com.bracit.fisprocess.messaging;

import com.bracit.fisprocess.config.RabbitMqTopology;
import com.rabbitmq.client.Channel;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class JournalWriteHandler {

    private final BatchingJournalWriter batchingWriter;
    private final MeterRegistry meterRegistry;

    @Value("${fis.batch.worker-concurrency:10}")
    private int workerConcurrency;

    @Value("${fis.batch.enabled:false}")
    private boolean batchEnabled;

    private final List<JournalWriteMessage> buffer = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService bufferScheduler;

    @PostConstruct
    public void initialize() {
        batchingWriter.initialize();

        if (batchEnabled) {
            bufferScheduler = Executors.newSingleThreadScheduledExecutor();
            bufferScheduler.scheduleAtFixedRate(this::flushBuffer, 10, 10, TimeUnit.MILLISECONDS);
        }

        log.info("JournalWriteHandler initialized with batchEnabled={}, concurrency={}",
                batchEnabled, workerConcurrency);
    }

    @PreDestroy
    public void shutdown() {
        if (bufferScheduler != null) {
            bufferScheduler.shutdown();
        }
        batchingWriter.shutdown();
    }

    public void handle(List<JournalWriteMessage> messages, Channel channel, long deliveryTag) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            if (batchEnabled) {
                batchingWriter.submitBatch(messages);
            } else {
                for (JournalWriteMessage message : messages) {
                    batchingWriter.submit(message);
                }
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to handle journal write batch", e);
            meterRegistry.counter("fis.worker.handle.error").increment();
            try {
                channel.basicNack(deliveryTag, false, true);
            } catch (IOException ex) {
                log.error("Failed to nack message", ex);
            }
        } finally {
            sample.stop(Timer.builder("fis.worker.handle.duration").register(meterRegistry));
        }
    }

    public void handleSingle(JournalWriteMessage message, Channel channel, long deliveryTag) {
        handle(List.of(message), channel, deliveryTag);
    }

    private void flushBuffer() {
        if (buffer.isEmpty()) {
            return;
        }

        List<JournalWriteMessage> toFlush = new ArrayList<>(buffer);
        buffer.clear();

        if (!toFlush.isEmpty()) {
            log.debug("Flushing {} messages from buffer", toFlush.size());
        }
    }

    public int getBufferSize() {
        return buffer.size();
    }
}