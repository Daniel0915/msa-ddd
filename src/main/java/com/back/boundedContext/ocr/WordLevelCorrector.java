package com.back.boundedContext.ocr;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class WordLevelCorrector {

    private final NgramRepository ngramRepository;
    private final OcrCorrectionConfig config;

    public String correct(String text, TextSegment.Lang lang) {
        List<String> words = tokenize(text);
        if (words.size() < 2) return text;

        List<String> corrected = new ArrayList<>(words);

        for (int i = 0; i < corrected.size() - 1; i++) {
            String w1 = corrected.get(i);
            String w2 = corrected.get(i + 1);
            String bigram = w1 + " " + w2;
            long freq = ngramRepository.getWordBigramFreq(bigram, lang);

            if (freq >= config.wordBigramMinFreq()) continue;

            // w2를 교정 대상으로 우선 시도
            String bestCandidate = findBestReplacement(w1, w2, lang);
            if (bestCandidate != null) {
                corrected.set(i + 1, bestCandidate);
            }
        }

        return String.join(" ", corrected);
    }

    /**
     * edit distance 1 후보 중 w1과의 word bigram 빈도가 가장 높은 단어 반환
     */
    private String findBestReplacement(String context, String target, TextSegment.Lang lang) {
        if (ngramRepository.isKnownWord(target, lang)) return null;

        Set<String> candidates = editDistance1(target);
        String best = null;
        long bestFreq = config.wordBigramMinFreq();

        for (String candidate : candidates) {
            if (!ngramRepository.isKnownWord(candidate, lang)) continue;

            String bigram = context + " " + candidate;
            long freq = ngramRepository.getWordBigramFreq(bigram, lang);
            if (freq > bestFreq) {
                bestFreq = freq;
                best = candidate;
            }
        }

        return best;
    }

    /**
     * edit distance 1 후보 생성 (삽입, 삭제, 치환, 전치)
     */
    private Set<String> editDistance1(String word) {
        Set<String> results = new HashSet<>();
        char[] alphabet = getAlphabet(word);

        // 삭제
        for (int i = 0; i < word.length(); i++) {
            results.add(word.substring(0, i) + word.substring(i + 1));
        }

        // 전치
        for (int i = 0; i < word.length() - 1; i++) {
            results.add(word.substring(0, i) + word.charAt(i + 1) + word.charAt(i) + word.substring(i + 2));
        }

        // 치환
        for (int i = 0; i < word.length(); i++) {
            for (char c : alphabet) {
                if (c != word.charAt(i)) {
                    results.add(word.substring(0, i) + c + word.substring(i + 1));
                }
            }
        }

        // 삽입
        for (int i = 0; i <= word.length(); i++) {
            for (char c : alphabet) {
                results.add(word.substring(0, i) + c + word.substring(i));
            }
        }

        results.remove(word);
        return results;
    }

    private char[] getAlphabet(String word) {
        if (!word.isEmpty() && KoreanJamoUtils.isSyllable(word.charAt(0))) {
            // 한글: 가~힣 범위에서 자주 쓰이는 음절 일부 (전체는 너무 많으므로 제한)
            return "가나다라마바사아자차카타파하고노도로모보소오조초코토포호구누두루무부수우주추쿠투푸후".toCharArray();
        }
        return "abcdefghijklmnopqrstuvwxyz".toCharArray();
    }

    private List<String> tokenize(String text) {
        return Arrays.stream(text.split("\\s+"))
                .filter(s -> !s.isBlank())
                .toList();
    }
}
