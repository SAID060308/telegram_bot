package com.example.demo;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class Demo18Application {

    @PostConstruct
    void tz() { TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tashkent")); }

    public static void main(String[] args) {
        SpringApplication.run(Demo18Application.class, args);
    }

}
