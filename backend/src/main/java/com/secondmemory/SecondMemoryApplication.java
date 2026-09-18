package com.secondmemory;

import com.secondmemory.config.AiProperties;
import com.secondmemory.config.AuthProperties;
import com.secondmemory.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties({AiProperties.class, StorageProperties.class, AuthProperties.class})
@EnableAsync
public class SecondMemoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(SecondMemoryApplication.class, args);
    }
}
