package com.back.boundedContext.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ocr.correction")
public record OcrCorrectionConfig(
        boolean enabled,
        long charBigramMinFreq,
        long wordBigramMinFreq,
        int maxEditDistance
) {
    public OcrCorrectionConfig {
        if (charBigramMinFreq <= 0) charBigramMinFreq = 10;
        if (wordBigramMinFreq <= 0) wordBigramMinFreq = 5;
        if (maxEditDistance <= 0) maxEditDistance = 1;
    }
}
