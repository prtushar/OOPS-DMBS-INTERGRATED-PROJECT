
package com.example.demo;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer-history")
public class CustomerHistoryController {

    private final JdbcTemplate jdbcTemplate;
    private final InvoiceController invoiceController;

    public CustomerHistoryController(
            JdbcTemplate jdbcTemplate,
            InvoiceController invoiceController) {

        this.jdbcTemplate = jdbcTemplate;
        this.invoiceController = invoiceController;
    }

    // CUSTOMER SERVICE HISTORY SEARCH
    @PostMapping("/search")
    public ResponseEntity<?> searchCustomerHistory(
            @RequestBody Map<String, String> request) {

        String vehicleNumber = request.get("vehicleNumber");
        String fullName = request.get("fullName");
        String email = request.get("email");

        if (vehicleNumber == null || vehicleNumber.isBlank()
                || fullName == null || fullName.isBlank()
                || email == null || email.isBlank()) {

            return ResponseEntity.badRequest().body(
                Map.of("message", "Please enter all three details.")
            );
        }

        try {

            List<Map<String, Object>> vehicles =
                jdbcTemplate.queryForList(
                    """
                    SELECT
                        v.id AS vehicle_id,
                        v.registration_number,
                        v.brand,
                        v.model,
                        v.manufacturing_year,
                        v.vehicle_type,
                        c.id AS customer_id,
                        c.full_name,
                        c.email AS customer_email
                    FROM vehicles v
                    INNER JOIN customers c
                        ON v.customer_id = c.id
                    WHERE
                        UPPER(REPLACE(v.registration_number, ' ', ''))
                            = UPPER(REPLACE(?, ' ', ''))
                        AND LOWER(TRIM(c.full_name)) = LOWER(TRIM(?))
                        AND LOWER(TRIM(c.email)) = LOWER(TRIM(?))
                    """,
                    vehicleNumber.trim(),
                    fullName.trim(),
                    email.trim()
                );

            if (vehicles.isEmpty()) {

                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("message",
                        "No matching vehicle or customer details found.")
                );
            }

            Map<String, Object> vehicle = vehicles.get(0);

            int vehicleId =
                ((Number) vehicle.get("vehicle_id")).intValue();

            List<Map<String, Object>> services =
                jdbcTemplate.queryForList(
                    """
                    SELECT
                        id,
                        service_date,
                        service_type,
                        service_number,
                        description,
                        service_cost,
                        status
                    FROM service_records
                    WHERE vehicle_id = ?
                        AND is_deleted = 0
                    ORDER BY service_date DESC, id DESC
                    """,
                    vehicleId
                );

            Map<String, Object> vehicleDetails = new HashMap<>();

            vehicleDetails.put(
                "registrationNumber",
                vehicle.get("registration_number")
            );

            vehicleDetails.put("brand", vehicle.get("brand"));
            vehicleDetails.put("model", vehicle.get("model"));

            vehicleDetails.put(
                "manufacturingYear",
                vehicle.get("manufacturing_year")
            );

            vehicleDetails.put(
                "vehicleType",
                vehicle.get("vehicle_type")
            );

            Map<String, Object> result = new HashMap<>();

            result.put("customerName", vehicle.get("full_name"));
            result.put("customerEmail", vehicle.get("customer_email"));
            result.put("vehicle", vehicleDetails);
            result.put("serviceRecords", services);

            return ResponseEntity.ok(result);

        } catch (Exception e) {

            System.err.println("CUSTOMER HISTORY SEARCH ERROR:");
            e.printStackTrace();

            return ResponseEntity.internalServerError().body(
                Map.of("message",
                    "Unable to retrieve service history.")
            );
        }
    }

    // CUSTOMER SERVICE PDF DOWNLOAD
    @PostMapping("/pdf")
    public ResponseEntity<?> downloadCustomerServicePdf(
            @RequestBody Map<String, Object> request) {

        String vehicleNumber =
            request.get("vehicleNumber") == null
                ? null : request.get("vehicleNumber").toString();

        String fullName =
            request.get("fullName") == null
                ? null : request.get("fullName").toString();

        String email =
            request.get("email") == null
                ? null : request.get("email").toString();

        int serviceRecordId;

        try {

            serviceRecordId =
                Integer.parseInt(
                    String.valueOf(request.get("serviceRecordId"))
                );

        } catch (Exception e) {

            return ResponseEntity.badRequest().body(
                Map.of("message", "Invalid service record.")
            );
        }

        if (serviceRecordId <= 0
                || vehicleNumber == null || vehicleNumber.isBlank()
                || fullName == null || fullName.isBlank()
                || email == null || email.isBlank()) {

            return ResponseEntity.badRequest().body(
                Map.of("message", "Please provide all required details.")
            );
        }

        try {

            // VERIFY CUSTOMER, VEHICLE AND SERVICE OWNERSHIP
            List<Map<String, Object>> records =
                jdbcTemplate.queryForList(
                    """
                    SELECT
                        sr.id,
                        sr.service_date,
                        sr.service_type,
                        sr.service_number,
                        sr.description,
                        sr.service_cost,
                        sr.status,
                        v.registration_number,
                        v.brand,
                        v.model,
                        v.vehicle_type,
                        v.manufacturing_year,
                        c.full_name,
                        c.email
                    FROM service_records sr
                    INNER JOIN vehicles v
                        ON sr.vehicle_id = v.id
                    INNER JOIN customers c
                        ON v.customer_id = c.id
                    WHERE
                        sr.id = ?
                        AND sr.is_deleted = 0
                        AND UPPER(REPLACE(v.registration_number, ' ', ''))
                            = UPPER(REPLACE(?, ' ', ''))
                        AND LOWER(TRIM(c.full_name)) = LOWER(TRIM(?))
                        AND LOWER(TRIM(c.email)) = LOWER(TRIM(?))
                    """,
                    serviceRecordId,
                    vehicleNumber.trim(),
                    fullName.trim(),
                    email.trim()
                );

            if (records.isEmpty()) {

                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("message",
                        "Service record not found for these customer details.")
                );
            }

            Map<String, Object> record = records.get(0);

            int serviceNumber =
                ((Number) record.get("service_number")).intValue();

            // PAID SERVICE: USE EXISTING INVOICE PDF GENERATOR
            if (serviceNumber >= 4) {

                List<Map<String, Object>> invoices =
                    jdbcTemplate.queryForList(
                        """
                        SELECT id
                        FROM invoices
                        WHERE service_record_id = ?
                        """,
                        serviceRecordId
                    );

                if (invoices.isEmpty()) {

                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        Map.of("message",
                            "Invoice has not been generated yet. Please contact TUSHAR MOTOR WORKS.")
                    );
                }

                int invoiceId =
                    ((Number) invoices.get(0).get("id")).intValue();

                return invoiceController.downloadPdf(invoiceId);
            }

            // FREE SERVICE: GENERATE A SEPARATE RECEIPT
            if (serviceNumber >= 1 && serviceNumber <= 3) {

                byte[] pdf = generateFreeServiceReceipt(record);

                return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=free-service-receipt-"
                            + serviceRecordId + ".pdf"
                    )
                    .body(pdf);
            }

            return ResponseEntity.badRequest().body(
                Map.of("message", "Invalid service number.")
            );

        } catch (Exception e) {

            System.err.println("CUSTOMER SERVICE PDF ERROR:");
            e.printStackTrace();

            return ResponseEntity.internalServerError().body(
                Map.of("message",
                    "Unable to generate the service PDF.")
            );
        }
    }

    // GENERATE FREE SERVICE RECEIPT PDF
    private byte[] generateFreeServiceReceipt(
            Map<String, Object> record) throws Exception {

        try (
            PDDocument document = new PDDocument();
            ByteArrayOutputStream output = new ByteArrayOutputStream()
        ) {

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDType1Font boldFont =
                new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            PDType1Font regularFont =
                new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (
                PDPageContentStream content =
                    new PDPageContentStream(document, page)
            ) {

                writeLine(content, boldFont, 22,
                    50, 780, "TUSHAR MOTOR WORKS");

                writeLine(content, regularFont, 11,
                    50, 760, "YOUR RIDE. OUR RESPONSIBILITY.");

                writeLine(content, boldFont, 17,
                    50, 710, "FREE SERVICE RECEIPT");

                writeLine(content, regularFont, 11,
                    50, 675, "Receipt No: " + record.get("id"));

                writeLine(content, regularFont, 11,
                    50, 655, "Service Date: " + record.get("service_date"));

                writeLine(content, regularFont, 11,
                    50, 615, "CUSTOMER DETAILS");

                writeLine(content, regularFont, 11,
                    50, 590, "Customer Name: "
                        + clean(record.get("full_name")));

                writeLine(content, regularFont, 11,
                    50, 570, "Registered Email: "
                        + clean(record.get("email")));

                writeLine(content, regularFont, 11,
                    50, 530, "VEHICLE DETAILS");

                writeLine(content, regularFont, 11,
                    50, 505, "Registration Number: "
                        + clean(record.get("registration_number")));

                writeLine(content, regularFont, 11,
                    50, 485, "Vehicle: "
                        + clean(record.get("brand"))
                        + " " + clean(record.get("model")));

                writeLine(content, regularFont, 11,
                    50, 465, "Vehicle Type: "
                        + clean(record.get("vehicle_type")));

                writeLine(content, regularFont, 11,
                    50, 445, "Manufacturing Year: "
                        + clean(record.get("manufacturing_year")));

                writeLine(content, regularFont, 11,
                    50, 405, "SERVICE DETAILS");

                writeLine(content, regularFont, 11,
                    50, 380, "Service Number: "
                        + clean(record.get("service_number")));

                writeLine(content, regularFont, 11,
                    50, 360, "Service Type: "
                        + clean(record.get("service_type")));

                writeLine(content, regularFont, 11,
                    50, 340, "Status: "
                        + clean(record.get("status")));

                writeLine(content, regularFont, 11,
                    50, 320, "Description: "
                        + shorten(clean(record.get("description")), 75));

                writeLine(content, boldFont, 14,
                    50, 270, "Service Cost: Rs. 0.00");

                writeLine(content, regularFont, 11,
                    50, 210, "This receipt confirms completion of a free service.");

                writeLine(content, regularFont, 11,
                    50, 185, "Thank you for choosing TUSHAR MOTOR WORKS!");

                writeLine(content, regularFont, 10,
                    50, 140, "YOUR RIDE. OUR RESPONSIBILITY.");
            }

            document.save(output);

            return output.toByteArray();
        }
    }

    // WRITE ONE LINE INTO PDF
    private void writeLine(
            PDPageContentStream content,
            PDType1Font font,
            int fontSize,
            float x,
            float y,
            String text) throws Exception {

        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(clean(text));
        content.endText();
    }

    // REMOVE CHARACTERS UNSUPPORTED BY THE STANDARD PDF FONT
    private String clean(Object value) {

        if (value == null) {
            return "-";
        }

        return value.toString()
            .replaceAll("[^\\x20-\\x7E]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    // KEEP LONG DESCRIPTIONS WITHIN THE RECEIPT WIDTH
    private String shorten(String value, int maxLength) {

        if (value == null || value.isBlank()) {
            return "-";
        }

        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength - 3) + "...";
    }
}
