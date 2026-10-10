package com.gradely.grading;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="gradely.worker.enabled",havingValue="true")
public class GradingListener implements ApplicationRunner {
    private final GradingService grading;
    private final GradingRepository jobs;
    private final Sandbox sandbox;
    private final ObjectMapper json;
    private final RabbitListenerEndpointRegistry listeners;
    private final int leaseSeconds;
    public GradingListener(GradingService grading,GradingRepository jobs,Sandbox sandbox,ObjectMapper json,
            RabbitListenerEndpointRegistry listeners,@Value("${SANDBOX_TIMEOUT_SECONDS:600}") int timeout) {
        this.grading=grading; this.jobs=jobs; this.sandbox=sandbox; this.json=json; this.listeners=listeners; this.leaseSeconds=timeout*2+600;
    }
    @Bean Queue gradingQueue() { return new Queue("gradely.submissions",true); }
    @Override public void run(ApplicationArguments args) {
        sandbox.verifyReady();
        listeners.getListenerContainer("gradely-grading").start();
    }
    @RabbitListener(id="gradely-grading",queues="gradely.submissions",ackMode="MANUAL",concurrency="1",autoStartup="false")
    public void consume(Message message,Channel channel) throws java.io.IOException {
        long tag=message.getMessageProperties().getDeliveryTag();
        long id;
        try {
            if (message.getBody().length>1024) throw new IllegalArgumentException();
            var node=json.readTree(message.getBody()).get("submissionId");
            if (node==null || !node.isIntegralNumber() || !node.canConvertToLong() || node.longValue()<=0) throw new IllegalArgumentException();
            id=node.longValue();
        } catch (Exception invalid) { channel.basicReject(tag,false); return; }
        grading.grade(id);
        channel.basicAck(tag,false);
    }
    @Scheduled(fixedDelay=60000,initialDelay=60000)
    public void recover() { jobs.recoverStale(leaseSeconds); }
}
