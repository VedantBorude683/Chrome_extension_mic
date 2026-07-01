package com.extbackend.chrom.Controller;

import com.extbackend.chrom.model.ThreatReport;
import com.extbackend.chrom.repository.ThreatReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
@CrossOrigin(origins = "*")

@RestController
@RequestMapping("/api/v1/pr")
public class ThreatReportController {

    private final ThreatReportRepository threatReportRepository;

    public ThreatReportController(ThreatReportRepository threatReportRepository) {
        this.threatReportRepository = threatReportRepository;
    }

    @GetMapping("/report/{trackingId}")
    public ResponseEntity<?> getThreatReport(@PathVariable String trackingId) {

        Optional<ThreatReport> report = threatReportRepository.findByTrackingId(trackingId);

        // If DeepSeek finished and MongoDB has it, return the 200 OK with the payload
        if (report.isPresent()) {
            return ResponseEntity.ok(report.get());
        }

        // If DeepSeek is still thinking, it won't be in the DB yet.
        // Return a 404 to tell the Chrome Extension to check back again in a few seconds.
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                Map.of(
                        "status", "PENDING",
                        "message", "The AI is still analyzing the code or the tracking ID is invalid. Try again shortly."
                )
        );
    }

}