package com.gradely.grading;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GradingListenerTest {
    @Test void startupDoesNotConsumeWithoutVerifiedRuntime() {
        var sandbox=mock(Sandbox.class); var listeners=mock(RabbitListenerEndpointRegistry.class);
        doThrow(new IllegalStateException("runsc required")).when(sandbox).verifyReady();
        var worker=new GradingListener(mock(GradingService.class),mock(GradingRepository.class),sandbox,new ObjectMapper(),listeners,30);
        assertThatThrownBy(() -> worker.run(null)).hasMessage("runsc required");
        verifyNoInteractions(listeners);
    }
    @Test void rejectsMalformedMessagesAndAcknowledgesOnlyAfterProcessing() throws Exception {
        var grading=mock(GradingService.class); var channel=mock(Channel.class);
        var worker=new GradingListener(grading,mock(GradingRepository.class),mock(Sandbox.class),new ObjectMapper(),mock(RabbitListenerEndpointRegistry.class),30);
        var props=new MessageProperties(); props.setDeliveryTag(12);
        worker.consume(new Message("{\"submissionId\":-1}".getBytes(),props),channel);
        verify(channel).basicReject(12,false); verifyNoInteractions(grading);
        worker.consume(new Message("{\"submissionId\":42}".getBytes(),props),channel);
        var order=inOrder(grading,channel); order.verify(grading).grade(42); order.verify(channel).basicAck(12,false);
        doThrow(new IllegalStateException("database unavailable")).when(grading).grade(99);
        props.setDeliveryTag(13);
        assertThatThrownBy(() -> worker.consume(new Message("{\"submissionId\":99}".getBytes(),props),channel)).isInstanceOf(IllegalStateException.class);
        verify(channel,never()).basicAck(13,false);
    }
}
