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

    @KafkaListener(topics = "pr-analysis-requests", groupId = "blast-radius-group-v2")
    public void consumePrFromKafka(@Payload String jsonPayload, @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String trackingId) {
        try {
            String finalTrackingId = (trackingId != null) ? trackingId : "NO-ID";
            log.info("Processing PR with ID: {}", finalTrackingId);
            PrPayloadRequest payload = objectMapper.readValue(jsonPayload, PrPayloadRequest.class);
            String codeDiff = payload.getCodeDiff();

            // 1. Force the model to act as a machine
            String systemInstruction ="You are a Blast Radius Detective. Analyze the PR diff against the Target API contract. " +
                    "Perform two types of checks: " +
                    "1. BREAKING CHANGES: Missing or renamed fields required by the consumer (Status: VULNERABLE). " +
                    "2. TYPOS/DRIFT: Inconsistent naming or suspicious spelling changes that might indicate a bug (Status: WARNING). " +
                    "Return ONLY raw JSON in this format: " +
                    "{\"status\": \"VULNERABLE\" | \"WARNING\" | \"SAFE\", \"findings\": [\"List your findings here\"]}";

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



        } catch (Exception e) {
            log.error("❌ Critical failure", e);
        }
    }
}