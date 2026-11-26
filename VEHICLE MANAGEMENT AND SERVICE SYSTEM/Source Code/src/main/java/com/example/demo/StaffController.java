
package com.example.demo;

import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    private static final Set<String> APPROVED_EMAILS = Set.of(
            "reena.rr@tmw.com",
            "tm.praveen@tmw.com",
            "tushar.pr@tmw.com"
    );

    public StaffController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // STAFF LOGIN
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {

        String email = request.get("email");
        String password = request.get("password");

        if (email == null || password == null
                || email.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message",
                            "Email and password are required."));
        }

        email = email.trim().toLowerCase();

        if (!APPROVED_EMAILS.contains(email)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message",
                            "Invalid email or password."));
        }

        try {
            Map<String, Object> staff = jdbcTemplate.queryForMap(
                    "SELECT id, full_name, email, password_hash, role " +
                    "FROM staff_users " +
                    "WHERE email = ? AND is_active = TRUE",
                    email
            );

            String storedHash =
                    (String) staff.get("password_hash");

            if (!passwordEncoder.matches(password, storedHash)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message",
                                "Invalid email or password."));
            }

            // Create a fresh session after successful login.
            HttpSession oldSession = httpRequest.getSession(false);

            if (oldSession != null) {
                oldSession.invalidate();
            }

            HttpSession session = httpRequest.getSession(true);

            session.setAttribute("staffId", staff.get("id"));
            session.setAttribute("staffName", staff.get("full_name"));
            session.setAttribute("staffEmail", staff.get("email"));
            session.setAttribute("staffRole", staff.get("role"));

            return ResponseEntity.ok(Map.of(
                    "message", "Login successful.",
                    "name", staff.get("full_name"),
                    "email", staff.get("email"),
                    "redirect", "/staff-dashboard.html"
            ));

        } catch (org.springframework.dao.EmptyResultDataAccessException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message",
                            "Invalid email or password."));

        }
    }

    // CHECK LOGIN SESSION
    @GetMapping("/session")
    public ResponseEntity<?> checkSession(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null ||
                session.getAttribute("staffId") == null) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("loggedIn", false));
        }

        return ResponseEntity.ok(Map.of(
                "loggedIn", true,
                "name", session.getAttribute("staffName"),
                "email", session.getAttribute("staffEmail"),
                "role", session.getAttribute("staffRole")
        ));
    }

    // STAFF LOGOUT
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        return ResponseEntity.ok(Map.of(
                "message", "Logged out successfully."
        ));
    }
}
