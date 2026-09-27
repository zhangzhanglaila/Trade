package com.example.tdproject;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan({"com.example.tdproject.generator.mapper", "com.example.tdproject.User"})
public class TdProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(TdProjectApplication.class, args);
    }

}
