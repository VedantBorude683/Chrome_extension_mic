package com.extbackend.chrom.model;

import java.util.List;

public record ContractVerdict(String reasoning, Status status, List<String> findings) {
    public enum Status {
        SAFE, VULNERABLE, UNKNOWN
    }
}