
package com.example.demo;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

@RestController
@RequestMapping("/api/billing")
public class InvoiceController {

    private final JdbcTemplate jdbc;

    public InvoiceController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // 1. List service records that do not have invoices yet
    @GetMapping("/available-services")
    public List<Map<String, Object>> availableServices() {
        return jdbc.queryForList("""
            SELECT
                sr.id AS service_record_id,
                sr.service_date,
                sr.service_type,
                sr.service_number,
                sr.service_cost,
                v.id AS vehicle_id,
                v.registration_number,
                v.brand,
                v.model,
                v.vehicle_type,
                c.id AS customer_id,
                COALESCE(c.full_name, 'Walk-in Customer') AS customer_name,
                c.phone,
                c.email,
                c.address
            FROM service_records sr
            JOIN vehicles v ON sr.vehicle_id = v.id
            LEFT JOIN customers c ON v.customer_id = c.id
            LEFT JOIN invoices i ON i.service_record_id = sr.id
            WHERE sr.is_deleted = 0
              AND i.id IS NULL
            ORDER BY sr.id DESC
            """);
    }

    // 2. Get details of one service record
    @GetMapping("/service-records/{id}")
    public ResponseEntity<?> getServiceDetails(@PathVariable int id) {

        List<Map<String, Object>> result = jdbc.queryForList("""
            SELECT
                sr.id AS service_record_id,
                sr.service_date,
                sr.service_type,
                sr.service_number,
                v.id AS vehicle_id,
                v.registration_number,
                v.brand,
                v.model,
                v.vehicle_type,
                c.id AS customer_id,
                COALESCE(c.full_name, 'Walk-in Customer') AS customer_name,
                c.phone,
                c.email,
                c.address
            FROM service_records sr
            JOIN vehicles v ON sr.vehicle_id = v.id
            LEFT JOIN customers c ON v.customer_id = c.id
            WHERE sr.id = ?
              AND sr.is_deleted = 0
            """, id);

        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(result.get(0));
    }

    // 3. Create invoice
    @PostMapping("/invoices")
    @Transactional
    public ResponseEntity<?> createInvoice(@RequestBody Map<String, Object> data) {

        try {
            int serviceRecordId = Integer.parseInt(
                data.get("serviceRecordId").toString()
            );

            List<Map<String, Object>> serviceRows = jdbc.queryForList("""
                SELECT
                    sr.id,
                    sr.service_number,
                    sr.service_date,
                    sr.service_type,
                    v.id AS vehicle_id,
                    v.registration_number,
                    v.brand,
                    v.model,
                    v.vehicle_type,
                    c.id AS customer_id,
                    COALESCE(c.full_name, 'Walk-in Customer') AS customer_name,
                    c.phone,
                    c.email,
                    c.address
                FROM service_records sr
                JOIN vehicles v ON sr.vehicle_id = v.id
                LEFT JOIN customers c ON v.customer_id = c.id
                WHERE sr.id = ?
                  AND sr.is_deleted = 0
                FOR UPDATE
                """, serviceRecordId);

            if (serviceRows.isEmpty()) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "Service record not found or deleted.")
                );
            }

            Map<String, Object> service = serviceRows.get(0);

            Integer existingInvoice = jdbc.query(
                "SELECT id FROM invoices WHERE service_record_id = ?",
                rs -> rs.next() ? rs.getInt("id") : null,
                serviceRecordId
            );

            if (existingInvoice != null) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "An invoice already exists for this service record.")
                );
            }

            int serviceNumber = ((Number) service.get("service_number")).intValue();
            boolean freeService = serviceNumber <= 3;

            String labourCategory = freeService
                ? "Free Service"
                : String.valueOf(data.getOrDefault("labourCategory", ""));

            if (!freeService &&
                !List.of("General Service", "Full Service").contains(labourCategory)) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "Choose General Service or Full Service.")
                );
            }

            BigDecimal labourPrice = freeService
                ? BigDecimal.ZERO
                : money(data.get("labourPrice"));

            if (labourPrice.compareTo(BigDecimal.ZERO) < 0) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "Labour price cannot be negative.")
                );
            }

            BigDecimal partsTotal = BigDecimal.ZERO;
            BigDecimal subtotal = labourPrice;
            BigDecimal additionalCharges = money(
                data.getOrDefault("additionalCharges", 0)
            );

            if (additionalCharges.compareTo(BigDecimal.ZERO) < 0) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "Additional charges cannot be negative.")
                );
            }

            String invoiceNumber = "INV-" +
                LocalDate.now().toString().replace("-", "") +
                "-" + System.currentTimeMillis();

            jdbc.update("""
                INSERT INTO invoices (
                    invoice_number, service_record_id, customer_id,
                    vehicle_id, invoice_date, customer_name,
                    customer_phone, customer_email, customer_address,
                    vehicle_registration, vehicle_brand, vehicle_model,
                    vehicle_type, service_type, service_number,
                    service_date, subtotal, additional_charges, grand_total
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 0)
                """,
                invoiceNumber,
                serviceRecordId,
                service.get("customer_id"),
                service.get("vehicle_id"),
                LocalDate.now(),
                service.get("customer_name"),
                service.get("phone"),
                service.get("email"),
                service.get("address"),
                service.get("registration_number"),
                service.get("brand"),
                service.get("model"),
                service.get("vehicle_type"),
                service.get("service_type"),
                serviceNumber,
                service.get("service_date"),
                additionalCharges
            );

            Integer invoiceId = jdbc.queryForObject(
                "SELECT id FROM invoices WHERE invoice_number = ?",
                Integer.class,
                invoiceNumber
            );

            // Add labour as the first invoice item
            addItem(
                invoiceId,
                labourCategory,
                1,
                labourPrice
            );

            Object rawItems = data.get("items");

            if (rawItems instanceof List<?> items) {
                for (Object rawItem : items) {
                    if (!(rawItem instanceof Map<?, ?> item)) {
                        throw new IllegalArgumentException("Invalid invoice item.");
                    }

                    String description = String.valueOf(item.get("description"));
                    int quantity = Integer.parseInt(item.get("quantity").toString());
                    BigDecimal unitPrice = money(item.get("unitPrice"));

                    if (description.isBlank() || quantity <= 0 ||
                        unitPrice.compareTo(BigDecimal.ZERO) < 0) {
                        throw new IllegalArgumentException("Invalid item details.");
                    }

                    BigDecimal amount = unitPrice
                        .multiply(BigDecimal.valueOf(quantity))
                        .setScale(2, RoundingMode.HALF_UP);

                    addItem(invoiceId, description, quantity, unitPrice);
                    partsTotal = partsTotal.add(amount);
                }
            }

            subtotal = subtotal.add(partsTotal)
                .setScale(2, RoundingMode.HALF_UP);

            BigDecimal grandTotal = subtotal.add(additionalCharges)
                .setScale(2, RoundingMode.HALF_UP);

            jdbc.update("""
                UPDATE invoices
                SET subtotal = ?, grand_total = ?
                WHERE id = ?
                """,
                subtotal,
                grandTotal,
                invoiceId
            );

            return ResponseEntity.ok(Map.of(
                "message", "Invoice created successfully.",
                "invoiceId", invoiceId,
                "invoiceNumber", invoiceNumber,
                "subtotal", subtotal,
                "additionalCharges", additionalCharges,
                "grandTotal", grandTotal
            ));

        } catch (DuplicateKeyException e) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "An invoice already exists for this service record.")
            );
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                Map.of("error", "Could not create invoice: " + e.getMessage())
            );
        }
    }

    private void addItem(
        int invoiceId,
        String description,
        int quantity,
        BigDecimal unitPrice
    ) {
        BigDecimal amount = unitPrice
            .multiply(BigDecimal.valueOf(quantity))
            .setScale(2, RoundingMode.HALF_UP);

        jdbc.update("""
            INSERT INTO invoice_items (
                invoice_id, item_description, quantity, unit_price, amount
            )
            VALUES (?, ?, ?, ?, ?)
            """,
            invoiceId, description, quantity, unitPrice, amount
        );
    }

    private BigDecimal money(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(value.toString())
            .setScale(2, RoundingMode.HALF_UP);
    }

    // 4. Invoice history
    @GetMapping("/invoices")
    public List<Map<String, Object>> invoiceHistory() {
        return jdbc.queryForList("""
            SELECT id, invoice_number, invoice_date,
                   customer_name, vehicle_registration,
                   vehicle_brand, vehicle_model, grand_total
            FROM invoices
            ORDER BY id DESC
            """);
    }

    // 5. Download invoice PDF
    @GetMapping("/invoices/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable int id) {

        try {
            List<Map<String, Object>> invoices = jdbc.queryForList(
                "SELECT * FROM invoices WHERE id = ?", id
            );

            if (invoices.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> invoice = invoices.get(0);

            List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT * FROM invoice_items WHERE invoice_id = ? ORDER BY id",
                id
            );

            byte[] pdfBytes;

            try (PDDocument document = new PDDocument()) {

                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);

                PDType1Font bold = new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA_BOLD
                );
                PDType1Font regular = new PDType1Font(
                    Standard14Fonts.FontName.HELVETICA
                );

                try (PDPageContentStream content =
                    new PDPageContentStream(document, page)) {

                    // Outer border
                    content.setLineWidth(1);
                    content.addRect(35, 35, 525, 772);
                    content.stroke();

                    write(content, bold, 22, 55, 770,
                        "VEHICLE SERVICE INVOICE");

                    write(content, regular, 10, 55, 748,
                        "Invoice: " + invoice.get("invoice_number"));

                    write(content, regular, 10, 55, 732,
                        "Date: " + invoice.get("invoice_date"));

                    content.moveTo(50, 715);
                    content.lineTo(545, 715);
                    content.stroke();

                    write(content, bold, 12, 55, 695, "CUSTOMER DETAILS");

                    write(content, regular, 10, 55, 677,
                        "Name: " + invoice.get("customer_name"));

                    write(content, regular, 10, 55, 661,
                        "Phone: " + safe(invoice.get("customer_phone")));

                    write(content, regular, 10, 55, 645,
                        "Email: " + safe(invoice.get("customer_email")));

                    write(content, regular, 10, 55, 629,
                        "Address: " + safe(invoice.get("customer_address")));

                    write(content, bold, 12, 55, 600, "VEHICLE DETAILS");

                    write(content, regular, 10, 55, 582,
                        "Vehicle: " + invoice.get("vehicle_brand") +
                        " " + invoice.get("vehicle_model"));

                    write(content, regular, 10, 55, 566,
                        "Registration: " + invoice.get("vehicle_registration"));

                    write(content, regular, 10, 55, 550,
                        "Type: " + invoice.get("vehicle_type"));

                    write(content, bold, 12, 55, 520, "SERVICE DETAILS");

                    write(content, regular, 10, 55, 502,
                        "Service: " + invoice.get("service_type"));

                    write(content, regular, 10, 55, 486,
                        "Service number: " + invoice.get("service_number"));

                    write(content, regular, 10, 55, 470,
                        "Service date: " + invoice.get("service_date"));

                    // Item table
                    float top = 435;
                    float rowHeight = 25;
                    float[] columns = {55, 310, 365, 420, 490, 545};

                    content.addRect(55, top - rowHeight, 490, rowHeight);
                    content.stroke();

                    for (int i = 1; i < columns.length - 1; i++) {
                        content.moveTo(columns[i], top - rowHeight);
                        content.lineTo(columns[i], top);
                        content.stroke();
                    }

                    write(content, bold, 9, 60, top - 17, "Description");
                    write(content, bold, 9, 315, top - 17, "Qty");
                    write(content, bold, 9, 370, top - 17, "Rate");
                    write(content, bold, 9, 425, top - 17, "Amount");

                    float y = top - rowHeight;

                    for (Map<String, Object> item : items) {
                        y -= rowHeight;

                        if (y < 130) {
                            break;
                        }

                        content.addRect(55, y, 490, rowHeight);
                        content.stroke();

                        for (int i = 1; i < columns.length - 1; i++) {
                            content.moveTo(columns[i], y);
                            content.lineTo(columns[i], y + rowHeight);
                            content.stroke();
                        }

                        write(content, regular, 8, 60, y + 9,
                            shorten(String.valueOf(item.get("item_description")), 42));

                        write(content, regular, 9, 320, y + 9,
                            String.valueOf(item.get("quantity")));

                        write(content, regular, 9, 370, y + 9,
                            "Rs. " + item.get("unit_price"));

                        write(content, regular, 9, 425, y + 9,
                            "Rs. " + item.get("amount"));
                    }

                    float totalY = Math.max(y - 35, 85);

                    write(content, bold, 11, 350, totalY,
                        "Subtotal: Rs. " + invoice.get("subtotal"));

                    write(content, regular, 10, 350, totalY - 18,
                        "Additional charges: Rs. " +
                        invoice.get("additional_charges"));

                    write(content, bold, 14, 350, totalY - 43,
                        "Grand Total: Rs. " + invoice.get("grand_total"));

                    write(content, regular, 9, 55, 60,
                        "Thank you for choosing our service!");
                }

                ByteArrayOutputStream output = new ByteArrayOutputStream();
                document.save(output);
                pdfBytes = output.toByteArray();
            }

            return ResponseEntity.ok()
                .header(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"invoice-" + id + ".pdf\""
                )
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private void write(
        PDPageContentStream content,
        PDType1Font font,
        int size,
        float x,
        float y,
        String text
    ) throws Exception {

        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(clean(text));
        content.endText();
    }

    private String clean(String text) {
        return text.replaceAll("[^\\x20-\\x7E]", " ");
    }

    private String safe(Object value) {
        return value == null ? "N/A" : String.valueOf(value);
    }

    private String shorten(String value, int max) {
        return value.length() > max
            ? value.substring(0, max - 3) + "..."
            : value;
    }
}
