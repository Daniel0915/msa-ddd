package com.back;

import com.back.boundedContext.ocr.OcrCorrectionConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@EnableConfigurationProperties(OcrCorrectionConfig.class)
public class MsaDddApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsaDddApplication.class, args);
    }
}
