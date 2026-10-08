package com.railway.security.file;

import com.railway.security.persistence.mapper.CheckAttachmentMapper;
import com.railway.security.persistence.mapper.ScanOutboxMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Configuration
@ConditionalOnProperty(name = "app.upload.queue.enabled", havingValue = "true")
class FileScanQueueTopology {
    static final String EXCHANGE = "railway.attachment.scan";
    static final String DEAD_EXCHANGE = "railway.attachment.scan.dead";
    static final String QUEUE = "railway.attachment.scan.work";
    static final String DEAD_QUEUE = "railway.attachment.scan.dead";

    @Bean
    DirectExchange fileScanExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange fileScanDeadExchange() {
        return new DirectExchange(DEAD_EXCHANGE, true, false);
    }

    @Bean
    Queue fileScanQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_EXCHANGE)
                .deadLetterRoutingKey("dead")
                .build();
    }

    @Bean
    Queue fileScanDeadQueue() {
        return QueueBuilder.durable(DEAD_QUEUE).build();
    }

    @Bean
    Binding fileScanBinding(
            @Qualifier("fileScanQueue") Queue fileScanQueue,
            @Qualifier("fileScanExchange") DirectExchange fileScanExchange) {
        return BindingBuilder.bind(fileScanQueue).to(fileScanExchange).with("scan");
    }

    @Bean
    Binding fileScanDeadBinding(
            @Qualifier("fileScanDeadQueue") Queue fileScanDeadQueue,
            @Qualifier("fileScanDeadExchange") DirectExchange fileScanDeadExchange) {
        return BindingBuilder.bind(fileScanDeadQueue).to(fileScanDeadExchange).with("dead");
    }
}

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.upload.queue.enabled", havingValue = "true")
public class FileScanQueue {
    private final ScanOutboxMapper outbox;
    private final CheckAttachmentMapper attachments;
    private final FileScanWorker worker;
    private final RabbitTemplate rabbit;
    private final MeterRegistry metrics;

    @Value("${app.upload.object-store.enabled:false}")
    private boolean objectStoreEnabled;

    @PostConstruct
    void validateConfiguration() {
        if (!objectStoreEnabled) {
            throw new IllegalStateException("启用附件消息队列时必须同时启用共享对象存储");
        }
        rabbit.setMandatory(true);
        Gauge.builder("railway.file.outbox.pending", outbox, ScanOutboxMapper::pendingCount)
                .register(metrics);
        Gauge.builder("railway.file.outbox.dead", outbox, ScanOutboxMapper::deadCount)
                .register(metrics);
    }

    @Scheduled(fixedDelayString = "${app.upload.queue.publish-interval-ms:3000}")
    public void publish() {
        outbox.recoverPublishing();
        outbox.recoverDeadPublishing();
        outbox.reconcileFinished();
        outbox.requeueDueScans();
        for (Long id : outbox.pending(20)) {
            if (outbox.claim(id) != 1) continue;
            try {
                publishConfirmed(FileScanQueueTopology.EXCHANGE, "scan", id);
                outbox.sent(id);
                Counter.builder("railway.file.outbox.total")
                        .tag("result", "sent")
                        .register(metrics)
                        .increment();
            } catch (Exception exception) {
                outbox.publishFailed(id);
                Counter.builder("railway.file.outbox.total")
                        .tag("result", "retry")
                        .register(metrics)
                        .increment();
                log.warn("附件 {} 的检测消息发布失败，将重试", id, exception);
            }
        }
        for (Long id : outbox.deadPending(20)) {
            if (outbox.claimDead(id) != 1) continue;
            try {
                publishConfirmed(FileScanQueueTopology.DEAD_EXCHANGE, "dead", id);
                outbox.deadPublished(id);
            } catch (Exception exception) {
                outbox.deadPublishFailed(id);
                log.warn("附件 {} 死信发布失败，将继续重试", id, exception);
            }
        }
    }

    @RabbitListener(queues = FileScanQueueTopology.QUEUE)
    // 消费者以附件 ID 驱动数据库条件认领；容器确认和业务成功不是同一概念，恢复路径应以数据库状态为准。
    public void consume(String body) {
        final Long id;
        try {
            id = Long.valueOf(body);
        } catch (NumberFormatException exception) {
            throw new AmqpRejectAndDontRequeueException("非法附件消息", exception);
        }
        if (attachments.claimScan(id) != 1) return;
        worker.scanClaimed(id);
        var attachment = attachments.selectById(id);
        if (attachment == null) return;
        switch (attachment.getScanStatus()) {
            case "CLEAN", "SKIPPED", "REJECTED" -> outbox.done(id);
            case "FAILED" -> {
                outbox.dead(id);
            }
            default -> {
                /* PENDING waits for scan_next_attempt_at and reconciliation. */
            }
        }
    }

    // 发布确认与不可路由返回都要处理；确认后标记 outbox，崩溃窗口可能重复投递，因此消费者仍必须幂等。
    private void publishConfirmed(String exchange, String key, Long id) throws Exception {
        var correlation = new CorrelationData(id + "-" + System.nanoTime());
        rabbit.convertAndSend(exchange, key, id.toString(), correlation);
        var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
        if (!confirm.isAck() || correlation.getReturned() != null) {
            throw new IllegalStateException("RabbitMQ 未确认消息路由");
        }
    }
}
