
package com.example.demo;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/service-records")
public class ServiceRecordController {

    private final JdbcTemplate jdbcTemplate;

    public ServiceRecordController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // GET NEXT SERVICE NUMBER AND CHECK SERVICE HISTORY
    @GetMapping("/next-number/{vehicleId}")
    public ResponseEntity<?> getNextServiceNumber(
            @PathVariable int vehicleId) {

        try {
            Integer vehicleCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicles WHERE id = ?",
                    Integer.class,
                    vehicleId);

            if (vehicleCount == null || vehicleCount == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Vehicle not found.");
            }

            Integer lastServiceNumber = jdbcTemplate.queryForObject(
                    "SELECT MAX(service_number) FROM service_records WHERE vehicle_id = ?",
                    Integer.class,
                    vehicleId);

            boolean hasServiceHistory = lastServiceNumber != null;

            int nextServiceNumber = hasServiceHistory
                    ? lastServiceNumber + 1
                    : 1;

            String nextServiceType = nextServiceNumber <= 3
                    ? "Free"
                    : "Paid";

            return ResponseEntity.ok(Map.of(
                    "vehicleId", vehicleId,
                    "hasServiceHistory", hasServiceHistory,
                    "nextServiceNumber", nextServiceNumber,
                    "nextServiceType", nextServiceType
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Unable to calculate next service number: "
                            + e.getMessage());
        }
    }

    // ADD SERVICE RECORD
    @PostMapping
    @Transactional
    public ResponseEntity<?> addServiceRecord(
            @RequestBody Map<String, Object> record) {

        try {
            int vehicleId = Integer.parseInt(
                    record.get("vehicleId").toString());

            String serviceDate = record.get("serviceDate").toString();

            String serviceType = record.get("serviceType").toString().trim();

            int serviceNumber = Integer.parseInt(
                    record.get("serviceNumber").toString());

            String description = record.get("description") == null
                    ? ""
                    : record.get("description").toString();

            double serviceCost = Double.parseDouble(
                    record.getOrDefault("serviceCost", 0).toString());

            String status = record.getOrDefault(
                    "status", "Pending").toString();

            if (serviceNumber < 1) {
                return ResponseEntity.badRequest()
                        .body("Service number must be at least 1.");
            }

            if (!serviceType.equals("Free")
                    && !serviceType.equals("Paid")) {
                return ResponseEntity.badRequest()
                        .body("Service type must be Free or Paid.");
            }

            if (serviceCost < 0) {
                return ResponseEntity.badRequest()
                        .body("Service cost cannot be negative.");
            }

            Integer vehicleCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vehicles WHERE id = ?",
                    Integer.class,
                    vehicleId);

            if (vehicleCount == null || vehicleCount == 0) {
                return ResponseEntity.badRequest()
                        .body("Vehicle not found.");
            }

            // LOCK VEHICLE ROW TO PREVENT SIMULTANEOUS SERVICE INSERTS
            jdbcTemplate.queryForObject(
                    "SELECT id FROM vehicles WHERE id = ? FOR UPDATE",
                    Integer.class,
                    vehicleId);

            // INCLUDE DELETED RECORDS IN SERVICE HISTORY
            Integer lastServiceNumber = jdbcTemplate.queryForObject(
                    "SELECT MAX(service_number) FROM service_records WHERE vehicle_id = ?",
                    Integer.class,
                    vehicleId);

            boolean hasServiceHistory = lastServiceNumber != null;

            if (hasServiceHistory) {

                // EXISTING VEHICLE: ENFORCE AUTOMATIC SEQUENCE
                int nextServiceNumber = lastServiceNumber + 1;

                if (serviceNumber != nextServiceNumber) {
                    return ResponseEntity.badRequest().body(
                            "Invalid service number. The next service for this vehicle must be "
                                    + nextServiceNumber + ".");
                }

                String expectedType = nextServiceNumber <= 3
                        ? "Free"
                        : "Paid";

                if (!serviceType.equals(expectedType)) {
                    return ResponseEntity.badRequest().body(
                            "Service " + nextServiceNumber
                                    + " must be a " + expectedType + " service.");
                }

            } else {

                // NEW VEHICLE: ALLOW MANUAL FIRST SERVICE DETAILS
                // User can select any positive service number and Free/Paid.
            }

            if (serviceType.equals("Free")) {
                serviceCost = 0;
            }

            jdbcTemplate.update(
                    "INSERT INTO service_records " +
                    "(vehicle_id, service_date, service_type, service_number, " +
                    "description, service_cost, status) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    vehicleId,
                    serviceDate,
                    serviceType,
                    serviceNumber,
                    description,
                    serviceCost,
                    status);

            return ResponseEntity.ok(
                    "Service record added successfully!");

        } catch (DuplicateKeyException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("This service number already exists for this vehicle.");

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("Unable to add service record: " + e.getMessage());
        }
    }

    // VIEW ACTIVE SERVICE RECORDS
    @GetMapping
    public ResponseEntity<?> getServiceRecords() {

        try {
            List<Map<String, Object>> records =
                    jdbcTemplate.queryForList(
                    "SELECT sr.id, sr.vehicle_id, " +
                    "v.registration_number, v.brand, v.model, " +
                    "c.full_name AS customer_name, " +
                    "sr.service_date, sr.service_type, sr.service_number, " +
                    "sr.description, sr.service_cost, sr.status " +
                    "FROM service_records sr " +
                    "JOIN vehicles v ON sr.vehicle_id = v.id " +
                    "LEFT JOIN customers c ON v.customer_id = c.id " +
                    "WHERE sr.is_deleted = FALSE " +
                    "ORDER BY sr.id DESC");

            return ResponseEntity.ok(records);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Unable to fetch service records: " + e.getMessage());
        }
    }

    // EDIT SERVICE RECORD
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateServiceRecord(
            @PathVariable int id,
            @RequestBody Map<String, Object> record) {

        try {
            List<Map<String, Object>> existingRecords =
                    jdbcTemplate.queryForList(
                    "SELECT vehicle_id, service_type, service_number " +
                    "FROM service_records " +
                    "WHERE id = ? AND is_deleted = FALSE",
                    id);

            if (existingRecords.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Active service record not found.");
            }

            Map<String, Object> existing = existingRecords.get(0);

            int existingVehicleId =
                    ((Number) existing.get("vehicle_id")).intValue();

            int existingServiceNumber =
                    ((Number) existing.get("service_number")).intValue();

            String existingServiceType =
                    existing.get("service_type").toString();

            int vehicleId = Integer.parseInt(
                    record.get("vehicleId").toString());

            int serviceNumber = Integer.parseInt(
                    record.get("serviceNumber").toString());

            String serviceType =
                    record.get("serviceType").toString();

            if (vehicleId != existingVehicleId
                    || serviceNumber != existingServiceNumber
                    || !serviceType.equals(existingServiceType)) {

                return ResponseEntity.badRequest().body(
                        "Vehicle, service type and service number cannot be changed while editing.");
            }

            String serviceDate =
                    record.get("serviceDate").toString();

            String description = record.get("description") == null
                    ? ""
                    : record.get("description").toString();

            double serviceCost = Double.parseDouble(
                    record.getOrDefault("serviceCost", 0).toString());

            String status = record.getOrDefault(
                    "status", "Pending").toString();

            if (serviceCost < 0) {
                return ResponseEntity.badRequest()
                        .body("Service cost cannot be negative.");
            }

            if (serviceType.equals("Free")) {
                serviceCost = 0;
            }

            int rows = jdbcTemplate.update(
                    "UPDATE service_records SET " +
                    "service_date = ?, description = ?, " +
                    "service_cost = ?, status = ? " +
                    "WHERE id = ? AND is_deleted = FALSE",
                    serviceDate,
                    description,
                    serviceCost,
                    status,
                    id);

            if (rows == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Active service record not found.");
            }

            return ResponseEntity.ok(
                    "Service record updated successfully!");

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("Unable to update service record: " + e.getMessage());
        }
    }

    // SOFT DELETE SERVICE RECORD
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteServiceRecord(@PathVariable int id) {

        try {
            int rows = jdbcTemplate.update(
                    "UPDATE service_records " +
                    "SET is_deleted = TRUE " +
                    "WHERE id = ? AND is_deleted = FALSE",
                    id);

            if (rows == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Active service record not found or already deleted.");
            }

            return ResponseEntity.ok(
                    "Service record moved to deleted history successfully!");

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("Unable to delete service record: " + e.getMessage());
        }
    }
}
