package com.example.demo;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashGenerator {

    public static void main(String[] args) {

        BCryptPasswordEncoder encoder =
                new BCryptPasswordEncoder();

        System.out.println("REENA: " +
                encoder.encode("Reena@123"));

        System.out.println("PRAVEEN: " +
                encoder.encode("Praveen@123"));

        System.out.println("TUSHAR: " +
                encoder.encode("Tushar@123"));
    }
}