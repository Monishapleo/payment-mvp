package com.paymentguard.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.*;

@Configuration
public class KafkaConfig {
    @Bean
    NewTopic paymentSucceededTopic() {
        return new NewTopic("payment.succeeded", 3, (short) 1);
    }
}
