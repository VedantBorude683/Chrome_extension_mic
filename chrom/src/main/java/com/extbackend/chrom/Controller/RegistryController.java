package com.extbackend.chrom.Controller;

import com.extbackend.chrom.model.ServiceRegistry;
import com.extbackend.chrom.repository.ServiceRegistryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/registry")
@CrossOrigin(origins = "*") // Allows external CI/CD pipelines to hit this safely
public class RegistryController {

    private final ServiceRegistryRepository registryRepository;

    public RegistryController(ServiceRegistryRepository registryRepository) {
        this.registryRepository = registryRepository;
    }

    @PostMapping("/sync")
    public ResponseEntity<?> syncServiceIdentity(@RequestBody ServiceRegistry incomingService) {

        // 1. Check if this microservice already exists in MongoDB
        Optional<ServiceRegistry> existingServiceOpt = registryRepository.findByServiceName(incomingService.getServiceName());

        if (existingServiceOpt.isPresent()) {
            // 2. The service exists! Update its dependencies and schema (UPSERT)
            ServiceRegistry existing = existingServiceOpt.get();
            existing.setDependencies(incomingService.getDependencies());
            existing.setApiContractSchema(incomingService.getApiContractSchema());

            registryRepository.save(existing);

            System.out.println("🔄 Auto-Discovery: UPDATED existing service -> " + existing.getServiceName());
            return ResponseEntity.ok(Map.of(
                    "message", "Service updated successfully",
                    "status", "UPDATED"
            ));
        } else {
            // 3. Brand new microservice! Insert it.
            registryRepository.save(incomingService);

            System.out.println("✨ Auto-Discovery: REGISTERED new service -> " + incomingService.getServiceName());
            return ResponseEntity.ok(Map.of(
                    "message", "New service registered successfully",
                    "status", "CREATED"
            ));
        }
    }
}