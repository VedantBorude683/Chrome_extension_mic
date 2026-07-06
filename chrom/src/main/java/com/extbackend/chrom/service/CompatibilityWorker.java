package com.extbackend.chrom.service;

import com.extbackend.chrom.model.PrPayloadRequest;
import com.extbackend.chrom.model.ServiceRegistry;
import com.extbackend.chrom.model.ThreatReport;
import com.extbackend.chrom.model.ContractVerdict; // The new strict record
import com.extbackend.chrom.repository.ServiceRegistryRepository;
import com.extbackend.chrom.repository.ThreatReportRepository;
import com.fasterxml.jackson.core.JsonParser;
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

    private final ObjectMapper objectMapper;
    private final ChatClient chatClient;
    private final ServiceRegistryRepository registryRepository;
    private final ThreatReportRepository threatReportRepository;

    public CompatibilityWorker(ChatClient.Builder chatClientBuilder,
                               ServiceRegistryRepository registryRepository,
                               ThreatReportRepository threatReportRepository) {

        // 1. Initialize and configure the ObjectMapper to tolerate AI comments!
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);

        this.chatClient = chatClientBuilder.build();
        this.registryRepository = registryRepository;
        this.threatReportRepository = threatReportRepository;
    }

    @KafkaListener(topics = "compatibility-checks", groupId = "blast-radius-group")
    public void checkCompatibility(@Payload String jsonPayload, @Header(KafkaHeaders.RECEIVED_KEY) String trackingId) {
        try {
            PrPayloadRequest payload = objectMapper.readValue(jsonPayload, PrPayloadRequest.class);
            String sourceRepo = payload.getRepositoryName();
            String codeDiff = payload.getCodeDiff();

            List<ServiceRegistry> affectedServices = registryRepository.findByDependenciesContaining(sourceRepo);

            for (ServiceRegistry targetService : affectedServices) {
                System.out.println("🔍 Analyzing compatibility between " + sourceRepo + " and " + targetService.getServiceName());

                // 1. The Strict Linter Rules
                // 1. A basic system prompt
                // 1. Strip the system prompt of any "AI" identity
                String systemInstruction = "You are a JSON-generating REST API. You cannot speak English. You can only output raw, valid JSON. " +
                        "NEVER include comments (// or /*) inside the JSON. " +
                        "NEVER output conversational text before or after the JSON.";
// 2. Remove "evaluate" and force the first character
                String userPrompt = "REQUIRED_CONTRACT:\n" + targetService.getApiContractSchema() +
                        "\n\nPR_STATE:\n" + codeDiff +
                        "\n\n---\n" +
                        "Generate a JSON response comparing these two. Do not explain. Do not use markdown.\n" +
                        "Use EXACTLY this format:\n" +
                        "{\n" +
                        "  \"reasoning\": \"1 sentence explanation\",\n" +
                        "  \"status\": \"VULNERABLE\",\n" +
                        "  \"findings\": [\n" +
                        "    \"ISSUE: [Name the broken field]\",\n" +
                        "    \"FIX: [How to fix it]\"\n" +
                        "  ]\n" +
                        "}\n\n" +
                        "START YOUR RESPONSE IMMEDIATELY WITH THE { CHARACTER. DO NOT TYPE ANYTHING ELSE.";
                try {
                    // 1. Get the raw string
                    String rawResponse = chatClient.prompt()
                            .system(systemInstruction)
                            .user(userPrompt)
                            .call()
                            .content();

                    System.out.println("🤖 DeepSeek RAW Output:\n" + rawResponse);

                    // 2. The Chokehold Parser: Rip out ONLY the JSON
                    int startIndex = rawResponse.indexOf("{");
                    int endIndex = rawResponse.lastIndexOf("}");

                    if (startIndex == -1 || endIndex == -1 || endIndex < startIndex) {
                        throw new IllegalStateException("DeepSeek completely failed to generate JSON brackets.");
                    }
                    String cleanJson = rawResponse.substring(startIndex, endIndex + 1);

                    // 3. The Dynamic Tree Parser (Bulletproof)
                    com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(cleanJson);
                    String parsedStatus = rootNode.path("status").asText("UNKNOWN");

                    java.util.List<String> safeFindings = new java.util.ArrayList<>();
                    com.fasterxml.jackson.databind.JsonNode findingsNode = rootNode.path("findings");

                    if (findingsNode.isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode element : findingsNode) {
                            if (element.isObject()) {
                                // If DeepSeek gave Objects: {"issue": "...", "fixes": "..."}
                                if (element.has("issue")) safeFindings.add("ISSUE: " + element.path("issue").asText());
                                if (element.has("fixes")) safeFindings.add("FIX: " + element.path("fixes").asText());
                            } else {
                                // If DeepSeek followed instructions and gave Strings
                                safeFindings.add(element.asText());
                            }
                        }
                    }

                    // 4. Repackage it cleanly for the Chrome Extension
                    String safeJsonForFrontend = objectMapper.writeValueAsString(java.util.Map.of(
                            "status", parsedStatus.toUpperCase(),
                            "findings", safeFindings
                    ));

                    // 5. Save to Database
                    ThreatReport report = new ThreatReport(trackingId, targetService.getServiceName(), safeJsonForFrontend);
                    threatReportRepository.save(report);

                    System.out.println("✅ Bulletproof Verdict saved to DB for tracking ID: " + trackingId);

                } catch (Exception aiException) {
                    System.err.println("🚨 AI Parsing Failed: " + aiException.getMessage());
                    String fallbackJson = "{\"status\": \"UNKNOWN\", \"findings\": [\"AI analysis failed due to malformed model output.\"]}";
                    threatReportRepository.save(new ThreatReport(trackingId, targetService.getServiceName(), fallbackJson));
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Critical failure in Compatibility Worker");
            e.printStackTrace();
        }
    }
}