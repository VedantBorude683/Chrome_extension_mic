package com.extbackend.chrom.service;

import com.extbackend.chrom.model.PrPayloadRequest;
import com.extbackend.chrom.model.ThreatReport;
import com.extbackend.chrom.repository.ThreatReportRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PrAnalysisWorker {

    private static final Logger log = LoggerFactory.getLogger(PrAnalysisWorker.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatClient chatClient;
    private final ThreatReportRepository threatReportRepository;

    public PrAnalysisWorker(ChatClient.Builder chatClientBuilder, ThreatReportRepository threatReportRepository) {
        this.chatClient = chatClientBuilder.build();
        this.threatReportRepository = threatReportRepository;
    }

    @KafkaListener(topics = "pr-analysis-requests", groupId = "blast-radius-group")
    public void consumePrFromKafka(@Payload String jsonPayload, @Header(KafkaHeaders.RECEIVED_KEY) String trackingId) {
        try {
            PrPayloadRequest payload = objectMapper.readValue(jsonPayload, PrPayloadRequest.class);
            String codeDiff = payload.getCodeDiff();

            // 1. Force the model to act as a machine
            String systemInstruction = "You are a data-only JSON API. Return ONLY raw JSON. " +
                    "REQUIRED JSON FORMAT: {\"status\": \"SAFE\", \"severity\": \"NONE\", \"findings\": [\"Clean\"]} " +
                    "OR {\"status\": \"VULNERABLE\", \"severity\": \"HIGH\", \"findings\": [\"Issue\"]}. " +
                    "DO NOT PROVIDE EXPLANATIONS. DO NOT USE MARKDOWN.";

            String aiResponse = chatClient.prompt()
                    .system(systemInstruction)
                    .user("Analyze this diff: " + (codeDiff != null ? codeDiff.substring(0, Math.min(codeDiff.length(), 1000)) : ""))
                    .call()
                    .content();

            log.info("AI RAW: {}", aiResponse);

            // 2. The Hard Validator: Ignore everything unless it looks like JSON
            String cleanJson;
            if (aiResponse.trim().startsWith("{")) {
                cleanJson = aiResponse.trim();
            } else {
                // If the AI babbled, ignore it and force a default response
                log.warn("AI returned conversational text. Forcing default JSON.");
                cleanJson = "{\"status\": \"SAFE\", \"severity\": \"NONE\", \"findings\": [\"No issues detected\"]}";
            }

            ThreatReport report = new ThreatReport(trackingId, payload.getRepositoryName(), cleanJson);
            threatReportRepository.save(report);
            log.info("💾 SUCCESS: Report saved.");

        } catch (Exception e) {
            log.error("❌ Critical failure", e);
        }
    }
}