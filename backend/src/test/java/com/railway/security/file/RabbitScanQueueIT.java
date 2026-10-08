package com.railway.security.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RabbitScanQueueIT {
    @Container
    static final RabbitMQContainer RABBIT =
            new RabbitMQContainer("rabbitmq:4.1.8-management-alpine");

    @Test
    void durableQueuesConfirmAndRouteDeadLetters() throws Exception {
        var factory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
        factory.setUsername(RABBIT.getAdminUsername());
        factory.setPassword(RABBIT.getAdminPassword());
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        try {
            var topology = new FileScanQueueTopology();
            var admin = new RabbitAdmin(factory);
            var exchange = topology.fileScanExchange();
            var deadExchange = topology.fileScanDeadExchange();
            var queue = topology.fileScanQueue();
            var deadQueue = topology.fileScanDeadQueue();
            admin.declareExchange(exchange);
            admin.declareExchange(deadExchange);
            admin.declareQueue(queue);
            admin.declareQueue(deadQueue);
            admin.declareBinding(topology.fileScanBinding(queue, exchange));
            admin.declareBinding(topology.fileScanDeadBinding(deadQueue, deadExchange));

            var template = new RabbitTemplate(factory);
            template.setMandatory(true);
            var confirm = new CorrelationData("attachment-42");
            template.convertAndSend(FileScanQueueTopology.EXCHANGE, "scan", "42", confirm);
            assertTrue(confirm.getFuture().get(5, TimeUnit.SECONDS).isAck());
            assertEquals("42", template.receiveAndConvert(FileScanQueueTopology.QUEUE, 5000));

            var deadConfirm = new CorrelationData("attachment-42-dead");
            template.convertAndSend(FileScanQueueTopology.DEAD_EXCHANGE, "dead", "42", deadConfirm);
            assertTrue(deadConfirm.getFuture().get(5, TimeUnit.SECONDS).isAck());
            assertEquals("42", template.receiveAndConvert(FileScanQueueTopology.DEAD_QUEUE, 5000));
        } finally {
            factory.destroy();
        }
    }
}
