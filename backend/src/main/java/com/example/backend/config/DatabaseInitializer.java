package com.example.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DatabaseInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        jdbcTemplate.execute(
            "CREATE SEQUENCE IF NOT EXISTS exploitation_code_seq " +
            "START WITH 1 INCREMENT BY 1"
        );

        jdbcTemplate.execute(
            "CREATE SEQUENCE IF NOT EXISTS parcelle_code_seq " +
            "START WITH 1 INCREMENT BY 1"
    );
    }
}