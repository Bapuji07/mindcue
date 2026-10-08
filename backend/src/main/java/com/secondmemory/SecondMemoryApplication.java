package com.secondmemory;

import com.secondmemory.config.AiProperties;
import com.secondmemory.config.AuthProperties;
import com.secondmemory.config.BackupProperties;
import com.secondmemory.config.LimitsProperties;
import com.secondmemory.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({AiProperties.class, StorageProperties.class, AuthProperties.class,
        LimitsProperties.class, BackupProperties.class})
@EnableAsync
@EnableScheduling
public class SecondMemoryApplication {
    public static void main(String[] args) {
        SpringApplication.run(SecondMemoryApplication.class, args);
    }
}
