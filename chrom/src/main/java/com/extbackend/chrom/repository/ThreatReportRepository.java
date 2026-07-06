package com.extbackend.chrom.repository;

import com.extbackend.chrom.model.ThreatReport;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ThreatReportRepository extends MongoRepository<ThreatReport, String> {
    // Spring Data automatically writes the MongoDB query just based on this method name!
    List<ThreatReport> findAllByTrackingId(String trackingId);
}