package com.back.boundedContext.ocr;

public final class KoreanJamoUtils {

    private static final int HANGUL_BASE = 0xAC00;
    private static final int HANGUL_END = 0xD7A3;
    private static final int CHO_COUNT = 19;
    private static final int JUNG_COUNT = 21;
    private static final int JONG_COUNT = 28;

    private static final char[] CHO = {
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    private static final char[] JUNG = {
            'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ',
            'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ'
    };

    private static final char[] JONG = {
            '\0', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ',
            'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ',
            'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    private KoreanJamoUtils() {
    }

    public static boolean isSyllable(char c) {
        return c >= HANGUL_BASE && c <= HANGUL_END;
    }

    public static boolean isJamo(char c) {
        return (c >= 0x3130 && c <= 0x318F);
    }

    public static boolean isConsonant(char c) {
        return (c >= 'ㄱ' && c <= 'ㅎ');
    }

    public static boolean isVowel(char c) {
        return (c >= 'ㅏ' && c <= 'ㅣ');
    }

    /**
     * 한글 음절을 초성/중성/종성 인덱스로 분해
     *
     * @return [choIndex, jungIndex, jongIndex] 또는 음절이 아니면 null
     */
    public static int[] decompose(char c) {
        if (!isSyllable(c)) return null;
        int code = c - HANGUL_BASE;
        int cho = code / (JUNG_COUNT * JONG_COUNT);
        int jung = (code % (JUNG_COUNT * JONG_COUNT)) / JONG_COUNT;
        int jong = code % JONG_COUNT;
        return new int[]{cho, jung, jong};
    }

    /**
     * 초성/중성/종성 인덱스로 한글 음절 조합
     */
    public static char compose(int cho, int jung, int jong) {
        return (char) (HANGUL_BASE + (cho * JUNG_COUNT + jung) * JONG_COUNT + jong);
    }

    /**
     * 한글 음절 문자열을 자모 문자열로 분해
     * 예: "한글" → "ㅎㅏㄴㄱㅡㄹ"
     */
    public static String toJamo(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            int[] parts = decompose(c);
            if (parts != null) {
                sb.append(CHO[parts[0]]);
                sb.append(JUNG[parts[1]]);
                if (parts[2] != 0) {
                    sb.append(JONG[parts[2]]);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 자모 시퀀스를 음절로 조합 (greedy left-to-right)
     * 예: "ㅎㅏㄴㄱㅡㄹ" → "한글"
     */
    public static String fromJamo(String jamo) {
        StringBuilder sb = new StringBuilder();
        char[] chars = jamo.toCharArray();
        int i = 0;

        while (i < chars.length) {
            if (!isConsonant(chars[i])) {
                sb.append(chars[i++]);
                continue;
            }

            int choIdx = indexOfCho(chars[i]);
            if (choIdx < 0) {
                sb.append(chars[i++]);
                continue;
            }

            // 다음 글자가 모음인지 확인
            if (i + 1 >= chars.length || !isVowel(chars[i + 1])) {
                sb.append(chars[i++]);
                continue;
            }

            int jungIdx = indexOfJung(chars[i + 1]);
            if (jungIdx < 0) {
                sb.append(chars[i++]);
                continue;
            }

            // 종성 확인
            int jongIdx = 0;
            if (i + 2 < chars.length && isConsonant(chars[i + 2])) {
                int candidateJong = indexOfJong(chars[i + 2]);
                if (candidateJong > 0) {
                    // 다음에 모음이 오면 이 자음은 다음 음절의 초성이므로 종성 아님
                    if (i + 3 < chars.length && isVowel(chars[i + 3])) {
                        jongIdx = 0;
                    } else {
                        jongIdx = candidateJong;
                    }
                }
            }

            sb.append(compose(choIdx, jungIdx, jongIdx));
            i += 2 + (jongIdx > 0 ? 1 : 0);
        }

        return sb.toString();
    }

    static int indexOfCho(char c) {
        for (int i = 0; i < CHO.length; i++) {
            if (CHO[i] == c) return i;
        }
        return -1;
    }

    static int indexOfJung(char c) {
        for (int i = 0; i < JUNG.length; i++) {
            if (JUNG[i] == c) return i;
        }
        return -1;
    }

    static int indexOfJong(char c) {
        for (int i = 1; i < JONG.length; i++) {
            if (JONG[i] == c) return i;
        }
        return -1;
    }
}
