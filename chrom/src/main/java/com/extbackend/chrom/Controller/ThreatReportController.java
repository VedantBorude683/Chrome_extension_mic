package com.extbackend.chrom.Controller;

import com.extbackend.chrom.model.PrPayloadRequest;
import com.extbackend.chrom.model.ServiceRegistry;
import com.extbackend.chrom.model.ThreatReport;
import com.extbackend.chrom.repository.ServiceRegistryRepository;
import com.extbackend.chrom.repository.ThreatReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@CrossOrigin(origins = "*")

@RestController
@RequestMapping("/api/v1/pr")
public class ThreatReportController {
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ServiceRegistryRepository registryRepository;
    private final ThreatReportRepository threatReportRepository;

    // REPLACE YOUR TWO CONSTRUCTORS WITH THIS SINGLE ONE:
    public ThreatReportController(KafkaTemplate<String, Object> kafkaTemplate,
                                  ServiceRegistryRepository registryRepository,
                                  ThreatReportRepository threatReportRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.registryRepository = registryRepository;
        this.threatReportRepository = threatReportRepository;
    }

    @GetMapping("/report/{trackingId}")
    public ResponseEntity<?> getThreatReport(@PathVariable String trackingId) {

        return threatReportRepository.findByTrackingId(trackingId)
                .<ResponseEntity<?>>map(ResponseEntity::ok) // If found, return the ThreatReport
                .orElseGet(() -> ResponseEntity.status(HttpStatus.OK).body(Map.of("status", "PENDING")));
    }
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzePR(@RequestBody PrPayloadRequest request) {
        String trackingId = UUID.randomUUID().toString();
        String repoName = request.getRepositoryName();

        try {
            // Convert object to JSON String
            String jsonPayload = objectMapper.writeValueAsString(request);

            // 1. Send the standard security scan
            kafkaTemplate.send("pr-analysis-requests", trackingId, jsonPayload);

            System.out.println("DEBUG: Searching registry for repoName: '" + repoName + "'");
            // 2. NEW: The Breaking Change Detective (Fan-Out Logic)
            List<ServiceRegistry> affectedServices = registryRepository.findByDependenciesContaining(repoName);

            System.out.println("DEBUG: Found " + affectedServices.size() + " dependencies.");

            for (ServiceRegistry affectedService : affectedServices) {
                // Also send the serialized JSON string here!
                kafkaTemplate.send("compatibility-checks", trackingId, jsonPayload);
            }

            return ResponseEntity.ok(Map.of("trackingId", trackingId));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error sending to Kafka");
        }
    }




}