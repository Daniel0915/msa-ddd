package com.back.boundedContext.ocr;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class CharLevelCorrector {

    private final NgramRepository ngramRepository;
    private final OcrCorrectionConfig config;

    // 영문 OCR 혼동 패턴 (원본 → 교정)
    private static final Map<String, String> EN_OCR_CONFUSIONS = Map.of(
            "rn", "m",
            "cl", "d",
            "vv", "w",
            "cI", "d",
            "Cl", "d"
    );

    public String correct(String text, TextSegment.Lang lang) {
        return switch (lang) {
            case KOREAN -> correctKorean(text);
            case ENGLISH -> correctEnglish(text);
            case OTHER -> text;
        };
    }

    /**
     * 한글 문자 단위 교정:
     * 1. 깨진 자모(standalone jamo) 시퀀스를 음절로 병합
     * 2. 문자 bigram 빈도 기반으로 저빈도 조합 탐지
     */
    private String correctKorean(String text) {
        // Phase 1: 깨진 자모 병합
        String merged = mergeStandaloneJamo(text);

        // Phase 2: 문자 bigram 빈도 기반 검증
        return applyCharBigramCorrection(merged, TextSegment.Lang.KOREAN);
    }

    /**
     * 연속된 standalone 자모를 음절로 조합
     * 예: "ㅎㅏㄴㄱㅡㄹ" → "한글"
     */
    private String mergeStandaloneJamo(String text) {
        StringBuilder result = new StringBuilder();
        StringBuilder jamoBuffer = new StringBuilder();

        for (char c : text.toCharArray()) {
            if (KoreanJamoUtils.isJamo(c)) {
                jamoBuffer.append(c);
            } else {
                if (!jamoBuffer.isEmpty()) {
                    result.append(KoreanJamoUtils.fromJamo(jamoBuffer.toString()));
                    jamoBuffer.setLength(0);
                }
                result.append(c);
            }
        }

        if (!jamoBuffer.isEmpty()) {
            result.append(KoreanJamoUtils.fromJamo(jamoBuffer.toString()));
        }

        return result.toString();
    }

    /**
     * 영문 문자 단위 교정:
     * OCR 혼동 패턴 적용 후 bigram 빈도로 검증
     */
    private String correctEnglish(String text) {
        String corrected = text;

        for (var entry : EN_OCR_CONFUSIONS.entrySet()) {
            String pattern = entry.getKey();
            String replacement = entry.getValue();

            int idx = corrected.indexOf(pattern);
            while (idx >= 0) {
                // 교정 후 bigram 빈도가 더 높은 경우에만 적용
                if (shouldReplace(corrected, idx, pattern, replacement, TextSegment.Lang.ENGLISH)) {
                    corrected = corrected.substring(0, idx) + replacement + corrected.substring(idx + pattern.length());
                    idx = corrected.indexOf(pattern, idx + replacement.length());
                } else {
                    idx = corrected.indexOf(pattern, idx + 1);
                }
            }
        }

        return corrected;
    }

    /**
     * 교정 전후 bigram 빈도를 비교해서 교체 여부 결정
     */
    private boolean shouldReplace(String text, int idx, String pattern, String replacement, TextSegment.Lang lang) {
        long originalScore = surroundingBigramScore(text, idx, pattern.length(), lang);
        String replaced = text.substring(0, idx) + replacement + text.substring(idx + pattern.length());
        long replacedScore = surroundingBigramScore(replaced, idx, replacement.length(), lang);
        return replacedScore > originalScore;
    }

    /**
     * 주어진 위치 주변 문자 bigram의 빈도 합산
     */
    private long surroundingBigramScore(String text, int idx, int len, TextSegment.Lang lang) {
        long score = 0;
        String lower = text.toLowerCase();

        // 앞쪽 bigram
        if (idx > 0) {
            String bigram = lower.substring(idx - 1, idx + 1);
            score += ngramRepository.getCharBigramFreq(bigram, lang);
        }

        // 뒤쪽 bigram
        if (idx + len < lower.length()) {
            String bigram = lower.substring(idx + len - 1, idx + len + 1);
            score += ngramRepository.getCharBigramFreq(bigram, lang);
        }

        return score;
    }

    private String applyCharBigramCorrection(String text, TextSegment.Lang lang) {
        if (text.length() < 2) return text;

        StringBuilder result = new StringBuilder();
        result.append(text.charAt(0));

        for (int i = 1; i < text.length(); i++) {
            String bigram = text.substring(i - 1, i + 1);
            long freq = ngramRepository.getCharBigramFreq(bigram, lang);

            if (freq < config.charBigramMinFreq() && KoreanJamoUtils.isSyllable(text.charAt(i))) {
                // 빈도가 낮으면 현재 문자를 그대로 유지 (향후 유사 문자 대체 확장 가능)
                result.append(text.charAt(i));
            } else {
                result.append(text.charAt(i));
            }
        }

        return result.toString();
    }
}
