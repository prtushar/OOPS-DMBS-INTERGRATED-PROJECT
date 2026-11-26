
package com.example.demo;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class StaffAuthFilter extends OncePerRequestFilter {

    // PAGES ACCESSIBLE WITHOUT STAFF LOGIN
    private static final Set<String> PUBLIC_PAGES = Set.of(
        "/",
        "/index.html",
        "/staff-login.html",
        "/customer-login.html",
        "/customer-history.html",
        "/favicon.ico"
    );

    // PUBLIC APIs
    private static final Set<String> PUBLIC_APIS = Set.of(
        "/api/staff/login",
        "/api/staff/session",
        "/api/staff/logout",
        "/api/customer-history/search",
        "/api/customer-history/pdf"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String path = request.getRequestURI()
                .substring(request.getContextPath().length());

        if (PUBLIC_PAGES.contains(path)) {
            return true;
        }

        if (PUBLIC_APIS.contains(path)) {
            return true;
        }

        // PUBLIC STATIC RESOURCES
        if (path.matches(".*\\.(css|js|png|jpg|jpeg|svg|ico|woff|woff2)$")) {
            return true;
        }

        // OTHER API REQUESTS REQUIRE STAFF LOGIN
        if (path.startsWith("/api/")) {
            return false;
        }

        // OTHER HTML PAGES REQUIRE STAFF LOGIN
        return !path.endsWith(".html");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI()
                .substring(request.getContextPath().length());

        HttpSession session = request.getSession(false);

        boolean isStaff = false;

        if (session != null) {

            isStaff =
                session.getAttribute("staffId") != null
                && "STAFF".equals(session.getAttribute("staffRole"));
        }

        if (!isStaff) {

            if (path.startsWith("/api/")) {

                response.setStatus(
                    HttpServletResponse.SC_UNAUTHORIZED
                );

                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");

                response.getWriter().write(
                    "{\"message\":\"Staff login required.\"}"
                );

            } else {

                response.sendRedirect("/staff-login.html");
            }

            return;
        }

        filterChain.doFilter(request, response);
    }
}
