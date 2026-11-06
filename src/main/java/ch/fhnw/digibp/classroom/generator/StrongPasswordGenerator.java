package ch.fhnw.digibp.classroom.generator;

import java.security.SecureRandom;

public final class StrongPasswordGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String SYMBOLS = "!#$%&*+-=?@_";
    private static final String ALPHABET = LOWER + UPPER + DIGITS + SYMBOLS;
    private static final int LENGTH = 24;

    private StrongPasswordGenerator() {
    }

    public static String generate() {
        // Rejection sampling keeps every accepted password equally likely.
        while (true) {
            StringBuilder password = new StringBuilder(LENGTH);
            boolean lower = false, upper = false, digit = false, symbol = false;
            for (int i = 0; i < LENGTH; i++) {
                char character = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
                password.append(character);
                lower |= LOWER.indexOf(character) >= 0;
                upper |= UPPER.indexOf(character) >= 0;
                digit |= DIGITS.indexOf(character) >= 0;
                symbol |= SYMBOLS.indexOf(character) >= 0;
            }
            if (lower && upper && digit && symbol) {
                return password.toString();
            }
        }
    }
}
