package com.railway.security.file;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.railway.security.persistence.entity.CheckAttachment;
import com.railway.security.persistence.mapper.CheckAttachmentMapper;
import com.railway.security.persistence.mapper.ScanOutboxMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class FileScanQueueTest {
    private final ScanOutboxMapper outbox = mock(ScanOutboxMapper.class);
    private final CheckAttachmentMapper attachments = mock(CheckAttachmentMapper.class);
    private final FileScanWorker worker = mock(FileScanWorker.class);
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final FileScanQueue queue =
            new FileScanQueue(outbox, attachments, worker, rabbit, new SimpleMeterRegistry());

    @Test
    void outboxIsMarkedSentOnlyAfterBrokerConfirmation() {
        when(outbox.pending(20)).thenReturn(List.of(42L));
        when(outbox.claim(42L)).thenReturn(1);
        doAnswer(
                        invocation -> {
                            CorrelationData correlation = invocation.getArgument(3);
                            correlation
                                    .getFuture()
                                    .complete(new CorrelationData.Confirm(true, null));
                            return null;
                        })
                .when(rabbit)
                .convertAndSend(
                        eq(FileScanQueueTopology.EXCHANGE),
                        eq("scan"),
                        eq("42"),
                        any(CorrelationData.class));

        queue.publish();

        verify(outbox).sent(42L);
    }

    @Test
    void rejectedBrokerConfirmationLeavesOutboxRetryable() {
        when(outbox.pending(20)).thenReturn(List.of(42L));
        when(outbox.claim(42L)).thenReturn(1);
        doAnswer(
                        invocation -> {
                            CorrelationData correlation = invocation.getArgument(3);
                            correlation
                                    .getFuture()
                                    .complete(
                                            new CorrelationData.Confirm(false, "broker rejected"));
                            return null;
                        })
                .when(rabbit)
                .convertAndSend(
                        eq(FileScanQueueTopology.EXCHANGE),
                        eq("scan"),
                        eq("42"),
                        any(CorrelationData.class));

        queue.publish();

        verify(outbox).publishFailed(42L);
        org.mockito.Mockito.verify(outbox, org.mockito.Mockito.never()).sent(42L);
    }

    @Test
    void duplicateDeliveryDoesNotRescan() {
        when(attachments.claimScan(42L)).thenReturn(0);
        queue.consume("42");
        org.mockito.Mockito.verifyNoInteractions(worker);
    }

    @Test
    void finishedScanClosesOutboxEvent() {
        when(attachments.claimScan(42L)).thenReturn(1);
        var attachment = new CheckAttachment();
        attachment.setScanStatus("CLEAN");
        when(attachments.selectById(42L)).thenReturn(attachment);
        queue.consume("42");
        verify(worker).scanClaimed(42L);
        verify(outbox).done(42L);
    }
}
