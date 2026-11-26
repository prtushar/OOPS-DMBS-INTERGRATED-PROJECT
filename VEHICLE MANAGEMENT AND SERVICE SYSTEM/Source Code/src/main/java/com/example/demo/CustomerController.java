
package com.example.demo;

import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomerController {

    private final JdbcTemplate jdbcTemplate;

    public CustomerController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ADD CUSTOMER
    @PostMapping("/api/customers")
    public ResponseEntity<String> addCustomer(
            @RequestBody Map<String, Object> customer) {

        String sql = """
            INSERT INTO customers
            (full_name, phone, email, address)
            VALUES (?, ?, ?, ?)
            """;

        try {
            jdbcTemplate.update(
                sql,
                customer.get("fullName"),
                customer.get("phone"),
                customer.get("email"),
                customer.get("address")
            );

            return ResponseEntity
                .status(HttpStatus.CREATED)
                .body("Customer saved successfully!");

        } catch (DuplicateKeyException e) {
            return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body("This phone number already exists!");
        }
    }

    // VIEW CUSTOMERS WITH THEIR VEHICLES
    @GetMapping("/api/customers")
    public List<Map<String, Object>> getCustomers() {

        String sql = """
            SELECT
                c.id,
                c.full_name,
                c.phone,
                c.email,
                c.address,
                c.created_at,
                GROUP_CONCAT(
                    DISTINCT CONCAT(v.brand, ' ', v.model)
                    ORDER BY v.id
                    SEPARATOR ', '
                ) AS vehicle_names,
                GROUP_CONCAT(
                    DISTINCT v.registration_number
                    ORDER BY v.id
                    SEPARATOR ', '
                ) AS vehicle_numbers
            FROM customers c
            LEFT JOIN vehicles v
                ON c.id = v.customer_id
            GROUP BY
                c.id,
                c.full_name,
                c.phone,
                c.email,
                c.address,
                c.created_at
            ORDER BY c.id DESC
            """;

        return jdbcTemplate.queryForList(sql);
    }

    // EDIT CUSTOMER
    @PutMapping("/api/customers/{id}")
    public ResponseEntity<String> updateCustomer(
            @PathVariable int id,
            @RequestBody Map<String, Object> customer) {

        String sql = """
            UPDATE customers
            SET full_name = ?,
                phone = ?,
                email = ?,
                address = ?
            WHERE id = ?
            """;

        try {
            int rows = jdbcTemplate.update(
                sql,
                customer.get("fullName"),
                customer.get("phone"),
                customer.get("email"),
                customer.get("address"),
                id
            );

            if (rows == 0) {
                return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body("Customer not found!");
            }

            return ResponseEntity.ok("Customer updated successfully!");

        } catch (DuplicateKeyException e) {
            return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body("This phone number already exists!");
        }
    }

    // DELETE CUSTOMER
    @DeleteMapping("/api/customers/{id}")
    public ResponseEntity<String> deleteCustomer(
            @PathVariable int id) {

        int rows = jdbcTemplate.update(
            "DELETE FROM customers WHERE id = ?",
            id
        );

        if (rows == 0) {
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body("Customer not found!");
        }

        return ResponseEntity.ok(
            "Customer deleted successfully!"
        );
    }
}
