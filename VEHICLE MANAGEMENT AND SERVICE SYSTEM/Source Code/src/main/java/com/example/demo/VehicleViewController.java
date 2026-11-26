
package com.example.demo;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.service.VehicleService;

@RestController
public class VehicleViewController {

    private final JdbcTemplate jdbcTemplate;
private final VehicleService vehicleService;

public VehicleViewController(
        JdbcTemplate jdbcTemplate,
        VehicleService vehicleService) {
    this.jdbcTemplate = jdbcTemplate;
    this.vehicleService = vehicleService;
}

    // VIEW ALL VEHICLES WITH CUSTOMER DETAILS
    @GetMapping("/api/vehicles")
    public List<Map<String, Object>> getVehicles() {

        String sql = """
            SELECT
                v.id,
                v.registration_number,
                v.brand,
                v.model,
                v.manufacturing_year,
                v.vehicle_type,
                c.full_name AS customer_name,
                c.phone AS customer_phone,
                c.email AS customer_email,
                c.address AS customer_address
            FROM vehicles v
            LEFT JOIN customers c
                ON v.customer_id = c.id
            ORDER BY v.id DESC
            """;

        return jdbcTemplate.queryForList(sql);
    }
    // GET VEHICLES USING SERVICE LAYER
@GetMapping("/api/vehicles/service")
public List<com.example.demo.model.Vehicle> getVehiclesUsingService() {
    return vehicleService.getAllVehicles();
}

// GET SINGLE VEHICLE USING SERVICE LAYER
@GetMapping("/api/vehicles/service/{id}")
public ResponseEntity<com.example.demo.model.Vehicle> getVehicleUsingService(
        @PathVariable int id) {

    var vehicle = vehicleService.getVehicleById(id);

    if (vehicle == null) {
        return ResponseEntity.notFound().build();
    }

    return ResponseEntity.ok(vehicle);
}
    // DELETE VEHICLE
    @DeleteMapping("/api/vehicles/{id}")
    public ResponseEntity<String> deleteVehicle(@PathVariable int id) {

        int rows = jdbcTemplate.update(
            "DELETE FROM vehicles WHERE id = ?", id
        );

        if (rows == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok("Vehicle deleted successfully!");
    }

    // UPDATE VEHICLE AND CUSTOMER
    @PutMapping("/api/vehicles/{id}")
    @Transactional
    public ResponseEntity<String> updateVehicle(
            @PathVariable int id,
            @RequestBody Map<String, Object> vehicle) {

        try {

            // CHECK WHETHER VEHICLE EXISTS
            List<Integer> currentCustomerIds = jdbcTemplate.query(
                "SELECT customer_id FROM vehicles WHERE id = ?",
                (rs, rowNum) ->
                    rs.getObject("customer_id", Integer.class),
                id
            );

            if (currentCustomerIds.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Integer currentCustomerId = currentCustomerIds.get(0);

            // READ AND VALIDATE VEHICLE DETAILS
            String registration = getText(vehicle, "registration");
            String brand = getText(vehicle, "brand");
            String model = getText(vehicle, "model");
            String yearText = getText(vehicle, "year");
            String type = getText(vehicle, "type");

            if (registration.isEmpty() || brand.isEmpty()
                    || model.isEmpty() || yearText.isEmpty()
                    || type.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body("Please fill in all vehicle details!");
            }

            int year = Integer.parseInt(yearText);

            // KEEP THE CURRENT CUSTOMER UNLESS NEW DETAILS ARE PROVIDED
            Integer customerId = currentCustomerId;

            String customerName = getText(vehicle, "customerName");
            String customerPhone = getText(vehicle, "customerPhone");

            String customerEmail = getText(vehicle, "customerEmail");
            String customerAddress = getText(vehicle, "customerAddress");

            // UPDATE CUSTOMER ONLY WHEN NAME AND PHONE ARE PROVIDED
            if (!customerName.isEmpty() && !customerPhone.isEmpty()) {

                List<Integer> existingCustomers = jdbcTemplate.query(
                    "SELECT id FROM customers WHERE phone = ?",
                    (rs, rowNum) -> rs.getInt("id"),
                    customerPhone
                );

                if (!existingCustomers.isEmpty()) {

                    Integer existingCustomerId = existingCustomers.get(0);

                    if (existingCustomerId.equals(currentCustomerId)) {

                        String updateCustomerSql = """
                            UPDATE customers
                            SET full_name = ?,
                                phone = ?,
                                email = ?,
                                address = ?
                            WHERE id = ?
                            """;

                        jdbcTemplate.update(
                            updateCustomerSql,
                            customerName,
                            customerPhone,
                            customerEmail,
                            customerAddress,
                            existingCustomerId
                        );

                    } else {

                        TransactionAspectSupport.currentTransactionStatus()
                            .setRollbackOnly();

                        return ResponseEntity.status(409)
                            .body("This phone number belongs to another customer!");
                    }

                } else {

                    String insertCustomerSql = """
                        INSERT INTO customers
                        (full_name, phone, email, address)
                        VALUES (?, ?, ?, ?)
                        """;

                    jdbcTemplate.update(
                        insertCustomerSql,
                        customerName,
                        customerPhone,
                        customerEmail,
                        customerAddress
                    );

                    customerId = jdbcTemplate.queryForObject(
                        "SELECT id FROM customers WHERE phone = ?",
                        Integer.class,
                        customerPhone
                    );
                }

            } else if (!customerName.isEmpty() || !customerPhone.isEmpty()) {

                return ResponseEntity.badRequest()
                    .body("Please provide both customer name and phone!");
            }

            // UPDATE VEHICLE DETAILS
            String updateVehicleSql = """
                UPDATE vehicles
                SET registration_number = ?,
                    brand = ?,
                    model = ?,
                    manufacturing_year = ?,
                    vehicle_type = ?,
                    customer_id = ?
                WHERE id = ?
                """;

            jdbcTemplate.update(
                updateVehicleSql,
                registration,
                brand,
                model,
                year,
                type,
                customerId,
                id
            );

            return ResponseEntity.ok(
                "Vehicle and customer details updated successfully!"
            );

        } catch (DuplicateKeyException e) {

            TransactionAspectSupport.currentTransactionStatus()
                .setRollbackOnly();

            return ResponseEntity.status(409)
                .body("Phone number or vehicle registration already exists!");

        } catch (NumberFormatException e) {

            return ResponseEntity.badRequest()
                .body("Please enter a valid manufacturing year!");

        } catch (Exception e) {

            TransactionAspectSupport.currentTransactionStatus()
                .setRollbackOnly();

            return ResponseEntity.internalServerError()
                .body("Unable to update vehicle: " + e.getMessage());
        }
    }

    // SAFELY READ REQUEST VALUES
    private String getText(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value == null ? "" : value.toString().trim();
    }
}
