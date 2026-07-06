package com.extbackend.chrom.repository;

import com.extbackend.chrom.model.ServiceRegistry;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceRegistryRepository extends MongoRepository<ServiceRegistry, String> {

    // Finds the specific service's contract


    // Finds all services that depend on the given service (The "Blast Radius")
    List<ServiceRegistry> findByDependenciesContaining(String dependencyName);
    Optional<ServiceRegistry> findByServiceName(String serviceName);
}