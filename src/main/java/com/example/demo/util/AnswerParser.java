package com.example.demo.util;

import java.math.BigInteger;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnswerParser {

    private AnswerParser() {}

    public static class AnswerFormatException extends RuntimeException {
        public AnswerFormatException(String message) { super(message); }
    }

    // 1-a | 1->a | 1:a | 1)a | 20--5 | 21-1/4
    private static final Pattern LINE = Pattern.compile("^(\\d{1,4})\\s*(?:->|-|:|\\))\\s*(\\S+)$");
    private static final Pattern LETTER = Pattern.compile("^[a-zA-Z]$");
    private static final Pattern INTEGER = Pattern.compile("^-?\\d+$");
    private static final Pattern FRACTION = Pattern.compile("^(-?\\d+)/(\\d+)$");
    private static final Pattern DECIMAL = Pattern.compile("^-?\\d+[.,]\\d+$");

    /** Javobni yagona ko'rinishga keltiradi. Noto'g'ri bo'lsa null qaytaradi. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String v = raw.trim();

        if (LETTER.matcher(v).matches()) return v.toLowerCase();
        if (INTEGER.matcher(v).matches()) return new BigInteger(v).toString();

        Matcher f = FRACTION.matcher(v);
        if (f.matches()) {
            BigInteger n = new BigInteger(f.group(1));
            BigInteger d = new BigInteger(f.group(2));
            if (d.signum() == 0) return null;
            BigInteger g = n.gcd(d);
            n = n.divide(g);
            d = d.divide(g);
            return d.equals(BigInteger.ONE) ? n.toString() : n + "/" + d;
        }
        return null;
    }

    /** O'quvchi javobi kalitga tengmi (2/4 == 1/2, A == a). */
    public static boolean matches(String key, String studentAnswer) {
        String a = normalize(key);
        String b = normalize(studentAnswer);
        return a != null && a.equals(b);
    }

    /** Ustoz kaliti: 1..N ketma-ket bo'lishi shart. */
    public static Map<Integer, String> parse(String text) {
        return parse(text, true);
    }

    /** O'quvchi javoblari: ba'zi savollar o'tkazib yuborilishi mumkin. */
    public static Map<Integer, String> parseStudent(String text) {
        return parse(text, false);
    }

    private static Map<Integer, String> parse(String text, boolean requireSequential) {
        Map<Integer, String> result = new TreeMap<>();
        String[] lines = text.split("\\R");
        int lineNo = 0;

        for (String raw : lines) {
            lineNo++;
            String line = raw.trim();
            if (line.isEmpty()) continue;

            Matcher m = LINE.matcher(line);
            if (!m.matches()) {
                throw new AnswerFormatException(
                        lineNo + "-qator noto'g'ri: \"" + line + "\"\nFormat: 1-a yoki 20-5 yoki 21-1/4");
            }

            int number = Integer.parseInt(m.group(1));
            String rawAnswer = m.group(2);

            if (DECIMAL.matcher(rawAnswer).matches()) {
                throw new AnswerFormatException(
                        lineNo + "-qator: o'nlik kasr mumkin emas (" + rawAnswer
                                + "). Oddiy kasr ko'rinishida yozing, masalan 1/4 yoki 3/10");
            }

            String answer = normalize(rawAnswer);
            if (answer == null) {
                throw new AnswerFormatException(
                        lineNo + "-qator: javob noto'g'ri (" + rawAnswer
                                + "). Harf (a), butun son (-5) yoki kasr (1/4) bo'lishi kerak");
            }
            if (result.put(number, answer) != null) {
                throw new AnswerFormatException(lineNo + "-qator: " + number + "-savol ikki marta yozilgan");
            }
        }

        if (result.isEmpty()) {
            throw new AnswerFormatException("Javoblar bo'sh. Format: 1-a, 2-b ...");
        }

        if (requireSequential) {
            int expected = 1;
            for (int n : result.keySet()) {
                if (n != expected) {
                    throw new AnswerFormatException(expected + "-savol javobi yo'q (yoki raqamlar ketma-ket emas)");
                }
                expected++;
            }
        }
        return result;
    }

    /** Bazaga saqlash uchun: "1-a\n2-b\n20--5" */
    public static String serialize(Map<Integer, String> key) {
        StringBuilder sb = new StringBuilder();
        key.forEach((n, a) -> sb.append(n).append('-').append(a).append('\n'));
        return sb.toString().trim();
    }
}