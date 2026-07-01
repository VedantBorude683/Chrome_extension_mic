package com.extbackend.chrom.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true) // Prevents crashes if Kafka/GitHub adds extra fields
public class PrPayloadRequest {
    private String repositoryName;
    private String codeDiff;

    // 1. MUST HAVE: A no-argument constructor for Jackson
    public PrPayloadRequest() {
    }

    public PrPayloadRequest(String repositoryName, String codeDiff) {
        this.repositoryName = repositoryName;
        this.codeDiff = codeDiff;
    }

    // Getters and Setters
    public String getRepositoryName() { return repositoryName; }
    public void setRepositoryName(String repositoryName) { this.repositoryName = repositoryName; }

    public String getCodeDiff() { return codeDiff; }
    public void setCodeDiff(String codeDiff) { this.codeDiff = codeDiff; }
}