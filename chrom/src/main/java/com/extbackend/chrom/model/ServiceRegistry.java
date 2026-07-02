package com.extbackend.chrom.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Document(collection = "service_registry")
public class ServiceRegistry {

    @Id
    private String id;

    // The name of the microservice (e.g., "payments-service")
    private String serviceName;

    // A list of other services that THIS service talks to
    private List<String> dependencies;

    // The JSON or YAML string of this service's API rules
    private String apiContractSchema;

    public ServiceRegistry() {
    }

    public ServiceRegistry(String serviceName, List<String> dependencies, String apiContractSchema) {
        this.serviceName = serviceName;
        this.dependencies = dependencies;
        this.apiContractSchema = apiContractSchema;
    }

    // --- Getters and Setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public List<String> getDependencies() { return dependencies; }
    public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }

    public String getApiContractSchema() { return apiContractSchema; }
    public void setApiContractSchema(String apiContractSchema) { this.apiContractSchema = apiContractSchema; }
}