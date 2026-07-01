package com.extbackend.chrom.Controller;

import com.extbackend.chrom.model.IngestionResponse;
import com.extbackend.chrom.model.PrPayloadRequest;
import com.extbackend.chrom.service.PrIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pr")
@CrossOrigin(origins = "*")
public class PrIngestionGateway {

    private static final Logger log = LoggerFactory.getLogger(PrIngestionGateway.class);

    // 1. Declare the Service
    private final PrIngestionService ingestionService;

    // 2. Inject it via the constructor
    public PrIngestionGateway(PrIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<IngestionResponse> submitPrForAnalysis(@RequestBody PrPayloadRequest payload) {

        log.info("🚪 FRONT DESK WOKE UP for repo: {}", payload.getRepositoryName());

        String trackingId = UUID.randomUUID().toString();

        // 3. Hand the heavy lifting off to the Service!
        ingestionService.processIncomingPr(payload, trackingId);

        return ResponseEntity.accepted().body(new IngestionResponse(trackingId, "PROCESSING"));
    }
}