package com.bracit.fisprocess.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * RabbitMQ topology for Phase 3 event ingestion and outbound domain events.
 */
@Configuration
@EnableRabbit
public class RabbitMqConfig {

    @Bean
    Declarables fisRabbitTopology() {
        TopicExchange eventsExchange = new TopicExchange(RabbitMqTopology.EVENTS_EXCHANGE, true, false);
        TopicExchange domainExchange = new TopicExchange(RabbitMqTopology.DOMAIN_EXCHANGE, true, false);
        DirectExchange dlxExchange = new DirectExchange(RabbitMqTopology.DLX_EXCHANGE, true, false);

        Queue ingestionQueue = QueueBuilder.durable(RabbitMqTopology.INGESTION_QUEUE)
                .withArgument("x-queue-type", "quorum")
                .withArgument("x-dead-letter-exchange", RabbitMqTopology.DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", RabbitMqTopology.INGESTION_DLQ_ROUTING_KEY)
                .build();

        Queue ingestionDlq = QueueBuilder.durable(RabbitMqTopology.INGESTION_DLQ_QUEUE)
                .withArgument("x-queue-type", "quorum")
                .build();

        Binding ingestionBinding = BindingBuilder.bind(ingestionQueue)
                .to(eventsExchange)
                .with("*.*.*");

        Binding dlqBinding = BindingBuilder.bind(ingestionDlq)
                .to(dlxExchange)
                .with(RabbitMqTopology.INGESTION_DLQ_ROUTING_KEY);

        return new Declarables(
                eventsExchange,
                domainExchange,
                dlxExchange,
                ingestionQueue,
                ingestionDlq,
                ingestionBinding,
                dlqBinding);
    }

    @Bean
    MessageConverter rabbitMessageConverter(JsonMapper jsonMapper) {
        return new JacksonJsonMessageConverter(jsonMapper);
    }
}
