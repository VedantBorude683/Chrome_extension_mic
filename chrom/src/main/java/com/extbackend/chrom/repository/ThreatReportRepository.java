package com.extbackend.chrom.repository;

import com.extbackend.chrom.model.ThreatReport;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ThreatReportRepository extends MongoRepository<ThreatReport, String> {
    // Spring automatically generates the MongoDB query just based on this method name!
    Optional<ThreatReport> findByTrackingId(String trackingId);
}