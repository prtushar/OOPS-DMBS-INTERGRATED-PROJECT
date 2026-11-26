package com.example.demo.service;

import java.util.List;
import com.example.demo.model.Vehicle;

public interface VehicleService {

    List<Vehicle> getAllVehicles();

    Vehicle getVehicleById(int id);
}