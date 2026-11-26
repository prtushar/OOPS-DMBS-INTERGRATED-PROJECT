
    package com.example.demo;

    import java.util.Map;

    import org.springframework.dao.DuplicateKeyException;
    import org.springframework.http.HttpStatus;
    import org.springframework.http.ResponseEntity;
    import org.springframework.jdbc.core.JdbcTemplate;
    import org.springframework.transaction.annotation.Transactional;
    import org.springframework.transaction.interceptor.TransactionAspectSupport;
    import org.springframework.web.bind.annotation.PostMapping;
    import org.springframework.web.bind.annotation.RequestBody;
    import org.springframework.web.bind.annotation.RestController;

    @RestController
    public class VehicleController {

        private final JdbcTemplate jdbcTemplate;

        public VehicleController(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        // ADD VEHICLE AND CUSTOMER
        @PostMapping("/api/vehicles")
        @Transactional
        public ResponseEntity<String> addVehicle(
                @RequestBody Map<String, Object> vehicle) {

            try {

                String customerName =
                    vehicle.get("customerName").toString().trim();

                String customerPhone =
                    vehicle.get("customerPhone").toString().trim();

                String customerEmail =
                    vehicle.get("customerEmail") == null
                        ? ""
                        : vehicle.get("customerEmail").toString().trim();

                String customerAddress =
                    vehicle.get("customerAddress") == null
                        ? ""
                        : vehicle.get("customerAddress").toString().trim();

                // CHECK WHETHER CUSTOMER ALREADY EXISTS
                String findCustomerSql = """
                    SELECT id FROM customers
                    WHERE phone = ?
                    """;

                Integer customerId;

                var existingCustomers = jdbcTemplate.query(
                    findCustomerSql,
                    (rs, rowNum) -> rs.getInt("id"),
                    customerPhone
                );

                if (!existingCustomers.isEmpty()) {

                    // REUSE EXISTING CUSTOMER
                    customerId = existingCustomers.get(0);

                } else {

                    // CREATE NEW CUSTOMER
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

                // SAVE VEHICLE WITH CUSTOMER ID
                String insertVehicleSql = """
                    INSERT INTO vehicles
                    (registration_number, brand, model,
                    manufacturing_year, vehicle_type, customer_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """;

                jdbcTemplate.update(
                    insertVehicleSql,
                    vehicle.get("registration"),
                    vehicle.get("brand"),
                    vehicle.get("model"),
                    Integer.parseInt(vehicle.get("year").toString()),
                    vehicle.get("type"),
                    customerId
                );

                return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body("Customer and vehicle saved successfully!");

            } catch (DuplicateKeyException e) {

                TransactionAspectSupport.currentTransactionStatus()
                    .setRollbackOnly();

                return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body("This phone number or vehicle registration already exists!");

            } catch (NumberFormatException e) {

                return ResponseEntity
                    .badRequest()
                    .body("Invalid manufacturing year!");

            } catch (Exception e) {

                TransactionAspectSupport.currentTransactionStatus()
                    .setRollbackOnly();

                return ResponseEntity
                    .internalServerError()
                    .body("Unable to save customer and vehicle: " + e.getMessage());
            }
        }
    }
