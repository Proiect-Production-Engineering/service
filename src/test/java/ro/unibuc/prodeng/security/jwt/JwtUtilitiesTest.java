package ro.unibuc.prodeng.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.core.Authentication;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import ro.unibuc.prodeng.model.UserDetails;

import java.security.Key;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtUtilitiesTest {

    @InjectMocks
    private JwtUtilities jwtUtilities;

    // A valid Base64-encoded 512-bit key for HS512
    private static final String TEST_SECRET =
            "e415546a1312fe62876c3431dc8caeb604a7876238485d9d14071108ce5adcce74b3679968da87984568cfd8494976b401e1cc880c6c4c7fa706cec3a3a045c9";

    private static final int TEST_EXPIRATION_MS = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtUtilities, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtUtilities, "jwtExpirationMs", TEST_EXPIRATION_MS);
    }

    private Key testKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(TEST_SECRET));
    }

    private String buildValidToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + TEST_EXPIRATION_MS))
                .signWith(testKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    // --- generateJwtToken ---

    @Test
    void testGenerateJwtToken_validAuthentication_returnsNonNullToken() {
        // Arrange
        UserDetails userDetails = UserDetails.builder()
                .id("1")
                .username("alice")
                .email("alice@example.com")
                .password("hashed")
                .authorities(Collections.emptyList())
                .build();

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        // Act
        String token = jwtUtilities.generateJwtToken(authentication);

        // Assert
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void testGenerateJwtToken_validAuthentication_tokenContainsCorrectUsername() {
        // Arrange
        UserDetails userDetails = UserDetails.builder()
                .id("1")
                .username("alice")
                .email("alice@example.com")
                .password("hashed")
                .authorities(Collections.emptyList())
                .build();

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        // Act
        String token = jwtUtilities.generateJwtToken(authentication);

        // Assert
        String username = jwtUtilities.getUserNameFromJwtToken(token);
        assertEquals("alice", username);
    }

    @Test
    void testGenerateJwtToken_validAuthentication_tokenIsValidatable() {
        // Arrange
        UserDetails userDetails = UserDetails.builder()
                .id("1")
                .username("bob")
                .email("bob@example.com")
                .password("hashed")
                .authorities(Collections.emptyList())
                .build();

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        // Act
        String token = jwtUtilities.generateJwtToken(authentication);

        // Assert
        assertTrue(jwtUtilities.validateJwtToken(token));
    }

    // --- getUserNameFromJwtToken ---

    @Test
    void testGetUserNameFromJwtToken_validToken_returnsUsername() {
        // Arrange
        String token = buildValidToken("charlie");

        // Act
        String username = jwtUtilities.getUserNameFromJwtToken(token);

        // Assert
        assertEquals("charlie", username);
    }

    @Test
    void testGetUserNameFromJwtToken_differentUsernames_returnsCorrectUsername() {
        // Arrange & Act & Assert
        assertEquals("alice", jwtUtilities.getUserNameFromJwtToken(buildValidToken("alice")));
        assertEquals("admin", jwtUtilities.getUserNameFromJwtToken(buildValidToken("admin")));
        assertEquals("user123", jwtUtilities.getUserNameFromJwtToken(buildValidToken("user123")));
    }

    // --- validateJwtToken ---

    @Test
    void testValidateJwtToken_validToken_returnsTrue() {
        // Arrange
        String token = buildValidToken("alice");

        // Act
        boolean isValid = jwtUtilities.validateJwtToken(token);

        // Assert
        assertTrue(isValid);
    }

    @Test
    void testValidateJwtToken_malformedToken_returnsFalse() {
        // Arrange
        String malformedToken = "this.is.not.a.valid.jwt";

        // Act
        boolean isValid = jwtUtilities.validateJwtToken(malformedToken);

        // Assert
        assertFalse(isValid);
    }

    @Test
    void testValidateJwtToken_expiredToken_returnsFalse() {
        // Arrange
        String expiredToken = Jwts.builder()
                .setSubject("alice")
                .setIssuedAt(new Date(System.currentTimeMillis() - 7200000))
                .setExpiration(new Date(System.currentTimeMillis() - 3600000))
                .signWith(testKey(), SignatureAlgorithm.HS512)
                .compact();

        // Act
        boolean isValid = jwtUtilities.validateJwtToken(expiredToken);

        // Assert
        assertFalse(isValid);
    }

    @Test
    void testValidateJwtToken_tokenSignedWithDifferentKey_returnsFalse() {
        // Arrange
        Key differentKey = Keys.secretKeyFor(SignatureAlgorithm.HS512);
        String tokenWithWrongKey = Jwts.builder()
                .setSubject("alice")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + TEST_EXPIRATION_MS))
                .signWith(differentKey, SignatureAlgorithm.HS512)
                .compact();

        // Act & Assert
        assertFalse(jwtUtilities.validateJwtToken(tokenWithWrongKey));
    }

    @Test
    void testValidateJwtToken_emptyString_returnsFalse() {
        // Act
        boolean isValid = jwtUtilities.validateJwtToken("");

        // Assert
        assertFalse(isValid);
    }

    @Test
    void testValidateJwtToken_nullToken_returnsFalse() {
        // Act
        boolean isValid = jwtUtilities.validateJwtToken(null);

        // Assert
        assertFalse(isValid);
    }

    @Test
    void testValidateJwtToken_unsignedToken_returnsFalse() {
        // Arrange — create an unsigned (alg=none) token by manually constructing the string
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes());
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"alice\",\"exp\":9999999999}".getBytes());
        String unsignedToken = header + "." + payload + ".";

        // Act
        boolean isValid = jwtUtilities.validateJwtToken(unsignedToken);

        // Assert
        assertFalse(isValid);
    }
}
