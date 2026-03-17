package ro.unibuc.prodeng.service;

import org.springframework.stereotype.Service;

import java.util.Random;

import static java.lang.Character.isDigit;
import static java.lang.Character.isUpperCase;
import static java.lang.Character.isLowerCase;
import static java.lang.Character.isAlphabetic;

/**
 * Service for IBAN generation and validation.
 * Ported from the PAOJ reference project's Country class.
 *
 * IBAN pattern characters:
 *   'a' = uppercase letter (A-Z)
 *   'n' = digit (0-9)
 *   'c' = alphanumeric (a-z mixed case + digits)
 *
 * Example patterns (from seed data):
 *   RO: "aaaacccccccccccccccc" (4 uppercase + 16 alphanumeric)
 *   FR: "nnnnnnnnnncccccccccccnn" (10 digits + 11 alphanumeric + 2 digits)
 *   GB: "aaaannnnnnnnnnnnnn" (4 uppercase + 14 digits)
 */
@Service
public class IBANService {

    private final Random random = new Random();

    /**
     * Generates a valid IBAN for the given country code and IBAN pattern.
     * Uses the MOD-97-10 algorithm (ISO 13616) for check digit computation.
     */
    public String generateIBAN(String countryCode, String ibanPattern) {
        if (countryCode == null || countryCode.length() != 2) {
            throw new IllegalArgumentException("Country code must be exactly 2 characters");
        }
        if (ibanPattern == null || ibanPattern.length() < 11 || ibanPattern.length() > 30) {
            throw new IllegalArgumentException("IBAN pattern must be between 11 and 30 characters (body only)");
        }

        StringBuilder partialIBAN = new StringBuilder();
        for (int index = 0; index < ibanPattern.length(); index++) {
            switch (ibanPattern.charAt(index)) {
                case 'a':
                    partialIBAN.append((char) ('A' + random.nextInt(26)));
                    break;
                case 'n':
                    partialIBAN.append((char) ('0' + random.nextInt(10)));
                    break;
                case 'c':
                    partialIBAN.append((char) ('a' + random.nextInt(26)));
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Invalid pattern character: " + ibanPattern.charAt(index) + ". Use 'a', 'n', or 'c'.");
            }
        }

        // Compute check digits using MOD-97-10
        String prefixAddedIBAN = partialIBAN + countryCode.toUpperCase() + "00";
        long checkValue = 98 - getChecksum(prefixAddedIBAN) % 97;
        String checkDigits = String.valueOf(checkValue);
        if (checkDigits.length() < 2) {
            checkDigits = "0" + checkDigits;
        }

        return countryCode.toUpperCase() + checkDigits + partialIBAN;
    }

    /**
     * Validates an IBAN against a country code and IBAN pattern.
     * Checks pattern matching and MOD-97-10 checksum.
     */
    public boolean isIBANValid(String iban, String countryCode, String ibanPattern) {
        if (iban == null || iban.length() < 15 || iban.length() > 34) {
            return false;
        }

        if (!ibanMatchesPattern(iban, countryCode, ibanPattern)) {
            return false;
        }

        // Move first 4 chars to end and verify MOD-97-10
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        long checksum = getChecksum(rearranged);
        return (checksum % 97 == 1);
    }

    private boolean ibanMatchesPattern(String iban, String countryCode, String ibanPattern) {
        // Check country code prefix
        if (!iban.substring(0, 2).equals(countryCode.toUpperCase())) {
            return false;
        }

        // Check positions 2 and 3 are digits (check digits)
        if (!isDigit(iban.charAt(2)) || !isDigit(iban.charAt(3))) {
            return false;
        }

        // Check body length matches pattern length
        String body = iban.substring(4);
        if (body.length() != ibanPattern.length()) {
            return false;
        }

        // Check each character matches the pattern
        for (int i = 0; i < ibanPattern.length(); i++) {
            char patternChar = ibanPattern.charAt(i);
            char ibanChar = body.charAt(i);

            if (patternChar == 'a' && !isUpperCase(ibanChar)) {
                return false;
            }
            if (patternChar == 'n' && !isDigit(ibanChar)) {
                return false;
            }
            if (patternChar == 'c' && (!isAlphabetic(ibanChar) && !isDigit(ibanChar))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Computes MOD-97 checksum using the segmented approach.
     * Letters are converted to their numeric equivalents (A=10, B=11, ... Z=35).
     */
    private long getChecksum(String input) {
        StringBuilder numberString = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (isDigit(c)) {
                numberString.append(c);
            } else if (isUpperCase(c)) {
                numberString.append(c - 'A' + 10);
            } else if (isLowerCase(c)) {
                numberString.append(c - 'a' + 10);
            }
        }

        // Process in segments to avoid overflow
        int segmentStart = 0;
        int step = 9;
        String prepended = "";
        long number;

        while (segmentStart < numberString.length() - step) {
            number = Long.parseLong(prepended + numberString.substring(segmentStart, segmentStart + step));
            long remainder = number % 97;
            prepended = Long.toString(remainder);
            if (remainder < 10) {
                prepended = "0" + prepended;
            }
            segmentStart += step;
            step = 7;
        }

        number = Long.parseLong(prepended + numberString.substring(segmentStart));
        return number;
    }
}
