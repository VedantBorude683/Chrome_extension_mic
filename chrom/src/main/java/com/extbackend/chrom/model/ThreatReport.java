package com.extbackend.chrom.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "threat_reports")
public class ThreatReport {

    @Id
    private String id;

    private String trackingId;
    private String repositoryName;
    private String aiAnalysis;
    private Instant createdAt;

    // Default constructor for Spring
    public ThreatReport() {}

    public ThreatReport(String trackingId, String repositoryName, String aiAnalysis) {
        this.trackingId = trackingId;
        this.repositoryName = repositoryName;
        this.aiAnalysis = aiAnalysis;
        this.createdAt = Instant.now();
    }

    // Standard Getters
    public String getId() { return id; }
    public String getTrackingId() { return trackingId; }
    public String getRepositoryName() { return repositoryName; }
    public String getAiAnalysis() { return aiAnalysis; }
    public Instant getCreatedAt() { return createdAt; }
}