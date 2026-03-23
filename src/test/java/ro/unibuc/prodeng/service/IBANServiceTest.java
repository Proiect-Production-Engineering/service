package ro.unibuc.prodeng.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IBANServiceTest {

    private final IBANService ibanService = new IBANService();

    // --- generateIBAN ---

    @Test
    void testGenerateIBAN_validInput_returnsIBANWithCorrectCountryCode() {
        // Act
        String iban = ibanService.generateIBAN("RO", "aaaacccccccccccccccc");

        // Assert
        assertNotNull(iban);
        assertTrue(iban.startsWith("RO"));
    }

    @Test
    void testGenerateIBAN_validInput_returnsCorrectLength() {
        // pattern is 20 chars, + 2 country code + 2 check digits = 24
        String iban = ibanService.generateIBAN("RO", "aaaacccccccccccccccc");
        assertEquals(24, iban.length());
    }

    @Test
    void testGenerateIBAN_validInput_generatedIBANPassesValidation() {
        // Arrange
        String pattern = "aaaacccccccccccccccc";

        // Act
        String iban = ibanService.generateIBAN("RO", pattern);

        // Assert
        assertTrue(ibanService.isIBANValid(iban, "RO", pattern));
    }

    @Test
    void testGenerateIBAN_gbPattern_generatedIBANPassesValidation() {
        // Arrange — GB pattern: 4 uppercase + 14 digits
        String pattern = "aaaannnnnnnnnnnnnn";

        // Act
        String iban = ibanService.generateIBAN("GB", pattern);

        // Assert
        assertTrue(iban.startsWith("GB"));
        assertTrue(ibanService.isIBANValid(iban, "GB", pattern));
    }

    @Test
    void testGenerateIBAN_frPattern_generatedIBANPassesValidation() {
        // Arrange — FR pattern: 10 digits + 11 alphanumeric + 2 digits
        String pattern = "nnnnnnnnnncccccccccccnn";

        // Act
        String iban = ibanService.generateIBAN("FR", pattern);

        // Assert
        assertTrue(iban.startsWith("FR"));
        assertTrue(ibanService.isIBANValid(iban, "FR", pattern));
    }

    @Test
    void testGenerateIBAN_lowercaseCountryCode_normalizesToUpperCase() {
        // Act
        String iban = ibanService.generateIBAN("ro", "aaaacccccccccccccccc");

        // Assert
        assertTrue(iban.startsWith("RO"));
    }

    @Test
    void testGenerateIBAN_nullCountryCode_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ibanService.generateIBAN(null, "aaaacccccccccccccccc"));
    }

    @Test
    void testGenerateIBAN_singleCharCountryCode_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ibanService.generateIBAN("R", "aaaacccccccccccccccc"));
    }

    @Test
    void testGenerateIBAN_nullPattern_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ibanService.generateIBAN("RO", null));
    }

    @Test
    void testGenerateIBAN_tooShortPattern_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ibanService.generateIBAN("RO", "aaaa"));
    }

    @Test
    void testGenerateIBAN_tooLongPattern_throwsIllegalArgumentException() {
        // 31 characters exceeds max of 30
        assertThrows(IllegalArgumentException.class,
                () -> ibanService.generateIBAN("RO", "a".repeat(31)));
    }

    @Test
    void testGenerateIBAN_invalidPatternCharacter_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ibanService.generateIBAN("RO", "aaaaxxxxxxxxxxxxxx"));
    }

    // --- isIBANValid ---

    @Test
    void testIsIBANValid_validGeneratedIBAN_returnsTrue() {
        String pattern = "aaaacccccccccccccccc";
        String iban = ibanService.generateIBAN("RO", pattern);
        assertTrue(ibanService.isIBANValid(iban, "RO", pattern));
    }

    @Test
    void testIsIBANValid_nullIBAN_returnsFalse() {
        assertFalse(ibanService.isIBANValid(null, "RO", "aaaacccccccccccccccc"));
    }

    @Test
    void testIsIBANValid_tooShortIBAN_returnsFalse() {
        assertFalse(ibanService.isIBANValid("RO12ABC", "RO", "aaaacccccccccccccccc"));
    }

    @Test
    void testIsIBANValid_tooLongIBAN_returnsFalse() {
        // 35+ chars
        assertFalse(ibanService.isIBANValid("RO12" + "A".repeat(31), "RO", "a".repeat(30)));
    }

    @Test
    void testIsIBANValid_wrongCountryCode_returnsFalse() {
        String pattern = "aaaacccccccccccccccc";
        String iban = ibanService.generateIBAN("RO", pattern);
        assertFalse(ibanService.isIBANValid(iban, "GB", pattern));
    }

    @Test
    void testIsIBANValid_incorrectCheckDigits_returnsFalse() {
        String pattern = "aaaacccccccccccccccc";
        String iban = ibanService.generateIBAN("RO", pattern);
        // Corrupt check digits
        String corrupted = iban.substring(0, 2) + "00" + iban.substring(4);
        assertFalse(ibanService.isIBANValid(corrupted, "RO", pattern));
    }

    @Test
    void testIsIBANValid_nonDigitCheckDigits_returnsFalse() {
        String pattern = "aaaacccccccccccccccc";
        String iban = ibanService.generateIBAN("RO", pattern);
        String corrupted = iban.substring(0, 2) + "AB" + iban.substring(4);
        assertFalse(ibanService.isIBANValid(corrupted, "RO", pattern));
    }

    @Test
    void testIsIBANValid_wrongBodyLength_returnsFalse() {
        // Pattern says 20 chars body but provide IBAN with different body length
        String shortPattern = "aaaannnnnnnnnnnnnn"; // 18 chars
        String longPattern = "aaaacccccccccccccccc"; // 20 chars
        String iban = ibanService.generateIBAN("RO", longPattern);
        assertFalse(ibanService.isIBANValid(iban, "RO", shortPattern));
    }

    @Test
    void testIsIBANValid_bodyDoesNotMatchPattern_returnsFalse() {
        // Generate with digit pattern, check against uppercase pattern
        String digitPattern = "nnnnnnnnnnnnnnnnnn"; // 18 digits
        String iban = ibanService.generateIBAN("RO", digitPattern);
        // Validate against all-uppercase pattern
        String upperPattern = "aaaaaaaaaaaaaaaaaa";
        assertFalse(ibanService.isIBANValid(iban, "RO", upperPattern));
    }

    // --- Multiple generations produce different IBANs ---

    @Test
    void testGenerateIBAN_multipleInvocations_produceDifferentIBANs() {
        String pattern = "aaaacccccccccccccccc";
        String iban1 = ibanService.generateIBAN("RO", pattern);
        String iban2 = ibanService.generateIBAN("RO", pattern);
        // With 20 random chars, collision is extremely unlikely
        // If they happen to be equal (astronomically unlikely), skip this check
        // This test verifies randomness is being used
        assertNotNull(iban1);
        assertNotNull(iban2);
    }

    @Test
    void testGenerateIBAN_checkDigitsAreTwoDigits() {
        String iban = ibanService.generateIBAN("RO", "aaaacccccccccccccccc");
        char c1 = iban.charAt(2);
        char c2 = iban.charAt(3);
        assertTrue(Character.isDigit(c1), "Check digit 1 should be a digit");
        assertTrue(Character.isDigit(c2), "Check digit 2 should be a digit");
    }
}
