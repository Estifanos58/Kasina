package com.schoolproject.ChatAPP.controllers;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);

    @Autowired(required = false)
    private MongoTemplate mongoTemplate;

    @Value("${file.upload-dir:uploads/files}")
    private String uploadDir;

    /**
     * Liveness probe: returns UP as long as the Spring Boot web server is responsive.
     */
    @GetMapping("/api/health/live")
    public ResponseEntity<Map<String, Object>> liveness() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(body);
    }

    /**
     * Readiness probe: verifies MongoDB connectivity and attachment storage directory.
     */
    @GetMapping({"/api/health/ready", "/api/health"})
    public ResponseEntity<Map<String, Object>> readiness() {
        boolean dbUp = checkMongo();
        boolean storageUp = checkStorage();
        boolean allUp = dbUp && storageUp;

        Map<String, String> checks = new LinkedHashMap<>();
        checks.put("mongodb", dbUp ? "UP" : "DOWN");
        checks.put("storage", storageUp ? "UP" : "DOWN");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", allUp ? "UP" : "DOWN");
        body.put("checks", checks);
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(allUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private boolean checkMongo() {
        if (mongoTemplate == null) {
            return false;
        }
        try {
            Document result = mongoTemplate.executeCommand(new Document("ping", 1));
            if (result == null) {
                return false;
            }
            Object ok = result.get("ok");
            return ok instanceof Number num && num.doubleValue() >= 1.0;
        } catch (Exception e) {
            logger.warn("Readiness check failed for MongoDB: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkStorage() {
        try {
            Path path = Paths.get(uploadDir).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            return Files.isDirectory(path) && Files.isWritable(path);
        } catch (Exception e) {
            logger.warn("Readiness check failed for storage '{}': {}", uploadDir, e.getMessage());
            return false;
        }
    }
}
