package com.back.boundedContext.ocr;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class NgramRepository {

    private Map<String, Long> koCharBigrams;
    private Map<String, Long> koWordBigrams;
    private Map<String, Long> enCharBigrams;
    private Map<String, Long> enWordBigrams;
    private Set<String> koWordSet;
    private Set<String> enWordSet;

    @PostConstruct
    void init() {
        koCharBigrams = loadTsv("ocr/ngram/ko_char_bigram.tsv");
        koWordBigrams = loadTsv("ocr/ngram/ko_word_bigram.tsv");
        enCharBigrams = loadTsv("ocr/ngram/en_char_bigram.tsv");
        enWordBigrams = loadTsv("ocr/ngram/en_word_bigram.tsv");
        koWordSet = extractWords(koWordBigrams);
        enWordSet = extractWords(enWordBigrams);
        log.info("N-gram 데이터 로드 완료: ko_char={}, ko_word={}, en_char={}, en_word={}",
                koCharBigrams.size(), koWordBigrams.size(), enCharBigrams.size(), enWordBigrams.size());
    }

    private Map<String, Long> loadTsv(String resourcePath) {
        Map<String, Long> map = new HashMap<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new ClassPathResource(resourcePath).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length == 2) {
                    map.put(parts[0], Long.parseLong(parts[1].strip()));
                }
            }
        } catch (IOException e) {
            log.warn("N-gram 파일 로드 실패: {} — 빈 사전으로 진행", resourcePath);
        }
        return Map.copyOf(map);
    }

    private Set<String> extractWords(Map<String, Long> wordBigrams) {
        var words = new java.util.HashSet<String>();
        for (String key : wordBigrams.keySet()) {
            String[] parts = key.split(" ");
            for (String word : parts) {
                if (!word.isBlank()) words.add(word);
            }
        }
        return Set.copyOf(words);
    }

    public long getCharBigramFreq(String bigram, TextSegment.Lang lang) {
        return switch (lang) {
            case KOREAN -> koCharBigrams.getOrDefault(bigram, 0L);
            case ENGLISH -> enCharBigrams.getOrDefault(bigram, 0L);
            default -> 0L;
        };
    }

    public long getWordBigramFreq(String wordBigram, TextSegment.Lang lang) {
        return switch (lang) {
            case KOREAN -> koWordBigrams.getOrDefault(wordBigram, 0L);
            case ENGLISH -> enWordBigrams.getOrDefault(wordBigram, 0L);
            default -> 0L;
        };
    }

    public boolean isKnownWord(String word, TextSegment.Lang lang) {
        return switch (lang) {
            case KOREAN -> koWordSet.contains(word);
            case ENGLISH -> enWordSet.contains(word);
            default -> false;
        };
    }
}
