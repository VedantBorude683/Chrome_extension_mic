package com.extbackend.chrom.Controller;

import com.extbackend.chrom.model.PrPayloadRequest;
import com.extbackend.chrom.model.ThreatReport;
import com.extbackend.chrom.repository.ThreatReportRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pr")
@CrossOrigin(origins = "*") // CRITICAL: Allows your Chrome Extension to make requests without CORS blocking
public class PrController {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ThreatReportRepository threatReportRepository;
    private final ObjectMapper objectMapper;

    public PrController(KafkaTemplate<String, String> kafkaTemplate,
                        ThreatReportRepository threatReportRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.threatReportRepository = threatReportRepository;
        this.objectMapper = new ObjectMapper();
    }

    // 1. The Trigger Endpoint
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzePullRequest(@RequestBody PrPayloadRequest request) {
        try {
            // Generate a unique tracking ID for this specific PR analysis job
            String trackingId = "NO-ID";
            String payloadJson = objectMapper.writeValueAsString(request);

            // Build a Kafka message with the tracking ID attached as a header
            Message<String> message = MessageBuilder
                    .withPayload(payloadJson)
                    .setHeader(KafkaHeaders.TOPIC, "compatibility-checks")
                    .setHeader(KafkaHeaders.KEY, trackingId) // The worker reads this header!
                    .build();

            // Fire and forget - hand it off to the worker
            kafkaTemplate.send(message);

            System.out.println("📥 Received PR from Extension. Dispatched to Kafka with ID: " + trackingId);

            // Immediately return the ID so the frontend can start polling
            return ResponseEntity.ok(Map.of("trackingId", trackingId));

        } catch (JsonProcessingException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to serialize payload"));
        }
    }

    // 2. The Polling Endpoint
    @GetMapping("/report/{trackingId}")
    public ResponseEntity<?> getAnalysisReport(@PathVariable String trackingId) {

        // 1. Grab ALL reports for this specific PR tracking ID
        java.util.List<ThreatReport> reports = threatReportRepository.findAllByTrackingId(trackingId);

        if (!reports.isEmpty()) {
            java.util.List<String> affectedServices = new java.util.ArrayList<>();
            java.util.List<String> aggregatedFindings = new java.util.ArrayList<>();
            boolean isGloballyVulnerable = false;

            // 2. Loop through every affected microservice
            for (ThreatReport report : reports) {
                String serviceName = report.getRepositoryName();
                affectedServices.add(serviceName);

                try {
                    // Crack open the JSON string the AI generated
                    com.fasterxml.jackson.databind.JsonNode aiData = objectMapper.readTree(report.getAiAnalysis());

                    // 3. Status Escalation: If even ONE service is broken, the whole PR is VULNERABLE
                    if ("VULNERABLE".equalsIgnoreCase(aiData.path("status").asText())) {
                        isGloballyVulnerable = true;
                    }

                    // 4. Tag the findings with the service name so the UI is clear
                    com.fasterxml.jackson.databind.JsonNode findingsNode = aiData.path("findings");

                    if (findingsNode.isArray()) {
                        // AI returned an array of findings
                        for (com.fasterxml.jackson.databind.JsonNode finding : findingsNode) {
                            aggregatedFindings.add("[" + serviceName + "] " + finding.asText());
                        }
                    } else if (findingsNode.isTextual()) {
                        // 🚀 NEW FIX: Catch the AI if it returns a single String instead of an Array!
                        aggregatedFindings.add("[" + serviceName + "] " + findingsNode.asText());
                    }

                } catch (Exception e) {
                    System.err.println("Failed to parse AI analysis for report: " + report.getId());
                }
            }

            // 5. Repackage everything exactly how the frontend content.js expects it
            String overallStatus = isGloballyVulnerable ? "VULNERABLE" : "SAFE";

            try {
                String mergedAiAnalysis = objectMapper.writeValueAsString(java.util.Map.of(
                        "status", overallStatus,
                        "findings", aggregatedFindings
                ));

                return ResponseEntity.ok(java.util.Map.of(
                        "status", "COMPLETED",
                        "affectedServices", affectedServices,
                        "aiAnalysis", mergedAiAnalysis
                ));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                return ResponseEntity.internalServerError().build();
            }

        } else {
            return ResponseEntity.ok(java.util.Map.of("status", "PENDING"));
        }
    }
}