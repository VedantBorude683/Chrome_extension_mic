package com.extbackend.chrom.service;

import com.extbackend.chrom.model.PrPayloadRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class PrIngestionService {

    private static final Logger log = LoggerFactory.getLogger(PrIngestionService.class);
    private static final String TOPIC = "pr-analysis-requests";

    private final KafkaTemplate<String, String> kafkaTemplate;

    // 1. Create the translator directly! No need to wait for Spring to "Autowire" it.
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 2. Only ask Spring for the KafkaTemplate
    public PrIngestionService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void processIncomingPr(PrPayloadRequest payload, String trackingId) {
        try {
            log.info("⚙️ Service Layer: Translating payload to JSON for Kafka...");

            String jsonPayload = objectMapper.writeValueAsString(payload);

            kafkaTemplate.send(TOPIC, trackingId, jsonPayload);

            log.info("📨 Fired PR {} into Kafka topic '{}' successfully!", trackingId, TOPIC);

        } catch (Exception e) {
            log.error("❌ Failed to translate payload to JSON", e);
        }
    }
}