package com.extbackend.chrom.service;

import com.extbackend.chrom.model.PrPayloadRequest;
import com.extbackend.chrom.model.ServiceRegistry;
import com.extbackend.chrom.model.ThreatReport;
import com.extbackend.chrom.repository.ServiceRegistryRepository;
import com.extbackend.chrom.repository.ThreatReportRepository; // Added import
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CompatibilityWorker {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatClient chatClient;
    private final ServiceRegistryRepository registryRepository;
    private final ThreatReportRepository threatReportRepository; // Added field

    // Updated constructor to include ThreatReportRepository
    public CompatibilityWorker(ChatClient.Builder chatClientBuilder,
                               ServiceRegistryRepository registryRepository,
                               ThreatReportRepository threatReportRepository) {
        this.chatClient = chatClientBuilder.build();
        this.registryRepository = registryRepository;
        this.threatReportRepository = threatReportRepository;
    }

    @KafkaListener(topics = "compatibility-checks", groupId = "blast-radius-group")
    public void checkCompatibility(@Payload String jsonPayload, @Header(KafkaHeaders.RECEIVED_KEY) String trackingId) {
        try {
            // 1. Parse the incoming PR request
            PrPayloadRequest payload = objectMapper.readValue(jsonPayload, PrPayloadRequest.class);
            String sourceRepo = payload.getRepositoryName();
            String codeDiff = payload.getCodeDiff();

            // 2. Find all services that depend on this source repo
            List<ServiceRegistry> affectedServices = registryRepository.findByDependenciesContaining(sourceRepo);

            // 3. Run a compatibility check for EACH affected service
            for (ServiceRegistry targetService : affectedServices) {
                System.out.println("🔍 Analyzing compatibility between " + sourceRepo + " and " + targetService.getServiceName());

                // The strict system instruction for the AI
                String systemInstruction = "You are a Strict Contract Enforcer. " +
                        "Compare the 'PR_STATE' against the 'REQUIRED_CONTRACT'. " +
                        "REQUIRED_CONTRACT: " + targetService.getApiContractSchema() +
                        "PR_STATE: " + codeDiff +
                        "If any field required by the contract is missing or renamed in the PR_STATE, return JSON status 'VULNERABLE'. " +
                        "Return ONLY: {\"status\": \"VULNERABLE\", \"findings\": [\"Breaking change: field missing\"]} " +
                        "or {\"status\": \"SAFE\", \"findings\": [\"Contract intact\"]}. " +
                        "DO NOT use markdown formatting.";

                // Combine the PR diff and the database contract
                String userPrompt = "Source Code Diff:\n" +
                        (codeDiff != null ? codeDiff.substring(0, Math.min(codeDiff.length(), 1000)) : "") +
                        "\n\nTarget Service API Contract:\n" + targetService.getApiContractSchema();

                // Call DeepSeek
                String aiResponse = chatClient.prompt()
                        .system(systemInstruction)
                        .user(userPrompt)
                        .call()
                        .content();

                System.out.println("🤖 AI Compatibility Verdict for " + targetService.getServiceName() + ": " + aiResponse);

                // 4. Save to Database
                ThreatReport report = new ThreatReport(
                        trackingId,
                        targetService.getServiceName(),
                        aiResponse
                );
                threatReportRepository.save(report);

                System.out.println("💾 Verdict saved to DB for tracking ID: " + trackingId);
            }

        } catch (Exception e) {
            System.err.println("❌ Critical failure in Compatibility Worker");
            e.printStackTrace();
        }
    }
}