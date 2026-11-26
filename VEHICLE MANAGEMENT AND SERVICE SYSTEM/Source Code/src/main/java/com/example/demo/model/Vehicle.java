package com.example.demo.model;

public class Vehicle {

    // Encapsulation: private data members
    private int id;
    private String registrationNumber;
    private String brand;
    private String model;
    private int manufacturingYear;
    private String vehicleType;

    // Constructor
    public Vehicle() {
    }

    public Vehicle(int id, String registrationNumber,
                   String brand, String model,
                   int manufacturingYear, String vehicleType) {
        this.id = id;
        this.registrationNumber = registrationNumber;
        this.brand = brand;
        this.model = model;
        this.manufacturingYear = manufacturingYear;
        this.vehicleType = vehicleType;
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getManufacturingYear() {
        return manufacturingYear;
    }

    public void setManufacturingYear(int manufacturingYear) {
        this.manufacturingYear = manufacturingYear;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }
}