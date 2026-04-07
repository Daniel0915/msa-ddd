package com.back.boundedContext.ocr;

public record TextSegment(
        String text,
        Lang lang,
        int startIndex,
        int endIndex
) {
    public enum Lang {KOREAN, ENGLISH, OTHER}
}
