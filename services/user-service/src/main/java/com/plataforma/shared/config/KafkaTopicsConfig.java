package com.plataforma.shared.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    @Bean
    public NewTopic topicUserWalletLinked() {
        return TopicBuilder.name("user.wallet_linked").partitions(3).replicas(1).build();
    }
}
