package com.example.demo.service;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.demo.model.Vehicle;

@Service
public class VehicleServiceImpl implements VehicleService {

    private final JdbcTemplate jdbcTemplate;

    public VehicleServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Vehicle> getAllVehicles() {
        String sql = """
                SELECT id, registration_number, brand, model,
                       manufacturing_year, vehicle_type
                FROM vehicles
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new Vehicle(
                        rs.getInt("id"),
                        rs.getString("registration_number"),
                        rs.getString("brand"),
                        rs.getString("model"),
                        rs.getInt("manufacturing_year"),
                        rs.getString("vehicle_type")
                ));
    }

    @Override
    public Vehicle getVehicleById(int id) {
        String sql = """
                SELECT id, registration_number, brand, model,
                       manufacturing_year, vehicle_type
                FROM vehicles
                WHERE id = ?
                """;

        List<Vehicle> vehicles = jdbcTemplate.query(sql, (rs, rowNum) ->
                new Vehicle(
                        rs.getInt("id"),
                        rs.getString("registration_number"),
                        rs.getString("brand"),
                        rs.getString("model"),
                        rs.getInt("manufacturing_year"),
                        rs.getString("vehicle_type")
                ), id);

        return vehicles.isEmpty() ? null : vehicles.get(0);
    }
}