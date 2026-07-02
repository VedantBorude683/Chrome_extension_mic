package com.extbackend.chrom.Controller;

import com.extbackend.chrom.model.ServiceRegistry;
import com.extbackend.chrom.repository.ServiceRegistryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1/registry")
public class ServiceRegistryController {

    private final ServiceRegistryRepository registryRepository;

    public ServiceRegistryController(ServiceRegistryRepository registryRepository) {
        this.registryRepository = registryRepository;
    }

    // Endpoint to register a new microservice and its dependencies
    @PostMapping("/register")
    public ResponseEntity<ServiceRegistry> registerService(@RequestBody ServiceRegistry registry) {
        ServiceRegistry saved = registryRepository.save(registry);
        return ResponseEntity.ok(saved);
    }

    // Endpoint to view all registered services
    @GetMapping("/all")
    public ResponseEntity<List<ServiceRegistry>> getAllServices() {
        return ResponseEntity.ok(registryRepository.findAll());
    }
}