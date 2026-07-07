package com.extbackend.chrom.Controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Autowired;

@RestController
@RequestMapping("/api/webhooks")
public class GitHubWebhookController {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @PostMapping("/github")
    public ResponseEntity<String> handleGitHubWebhook(@RequestBody String payload) {
        // 1. Send the raw GitHub PR payload to Kafka
        kafkaTemplate.send("pr-analysis-requests", payload);

        // 2. Immediately tell GitHub we got it (prevents 404/timeouts)
        return ResponseEntity.ok("Webhook Received & Queued");
    }
}