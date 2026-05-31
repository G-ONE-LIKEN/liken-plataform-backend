package com.plataforma.projects.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    // Tópicos que publica este servicio
    @Bean
    public NewTopic topicProjectsCreated() {
        return TopicBuilder.name("projects.created").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic topicProjectsStateChanged() {
        return TopicBuilder.name("projects.state_changed").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic topicProjectsMetricsUpdated() {
        return TopicBuilder.name("projects.metrics_updated").partitions(3).replicas(1).build();
    }
}
