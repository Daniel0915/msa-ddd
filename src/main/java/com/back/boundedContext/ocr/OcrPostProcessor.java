package com.back.boundedContext.ocr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrPostProcessor {

    private final CharLevelCorrector charLevelCorrector;
    private final WordLevelCorrector wordLevelCorrector;
    private final OcrCorrectionConfig config;

    public String process(String rawOcrText) {
        if (!config.enabled() || rawOcrText == null || rawOcrText.isBlank()) {
            return rawOcrText;
        }

        List<TextSegment> segments = splitByLanguage(rawOcrText);

        StringBuilder result = new StringBuilder();
        for (TextSegment seg : segments) {
            String text = seg.text();

            // Phase 1: 문자 단위 교정
            text = charLevelCorrector.correct(text, seg.lang());

            // Phase 2: 단어 단위 교정
            text = wordLevelCorrector.correct(text, seg.lang());

            result.append(text);
        }

        String corrected = result.toString();
        if (!corrected.equals(rawOcrText)) {
            log.debug("OCR 후처리 교정 적용: '{}' → '{}'", truncate(rawOcrText), truncate(corrected));
        }

        return corrected;
    }

    /**
     * 텍스트를 언어별 세그먼트로 분리
     * - 한글 음절/자모 → KOREAN
     * - 영문 a-zA-Z → ENGLISH
     * - 그 외 (숫자, 공백, 특수문자) → 인접 세그먼트에 병합
     */
    List<TextSegment> splitByLanguage(String text) {
        List<TextSegment> segments = new ArrayList<>();
        if (text == null || text.isEmpty()) return segments;

        StringBuilder buffer = new StringBuilder();
        TextSegment.Lang currentLang = detectLang(text.charAt(0));
        int startIdx = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            TextSegment.Lang charLang = detectLang(c);

            if (charLang == TextSegment.Lang.OTHER) {
                // 숫자/공백/특수문자는 현재 세그먼트에 포함
                buffer.append(c);
                continue;
            }

            if (charLang != currentLang && !buffer.isEmpty() && currentLang != TextSegment.Lang.OTHER) {
                segments.add(new TextSegment(buffer.toString(), currentLang, startIdx, i));
                buffer.setLength(0);
                startIdx = i;
            }

            if (currentLang == TextSegment.Lang.OTHER) {
                currentLang = charLang;
            }

            currentLang = charLang;
            buffer.append(c);
        }

        if (!buffer.isEmpty()) {
            segments.add(new TextSegment(buffer.toString(), currentLang, startIdx, text.length()));
        }

        return segments;
    }

    private TextSegment.Lang detectLang(char c) {
        if (KoreanJamoUtils.isSyllable(c) || KoreanJamoUtils.isJamo(c)) {
            return TextSegment.Lang.KOREAN;
        }
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
            return TextSegment.Lang.ENGLISH;
        }
        return TextSegment.Lang.OTHER;
    }

    private String truncate(String text) {
        return text.length() > 100 ? text.substring(0, 100) + "..." : text;
    }
}
