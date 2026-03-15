package ro.unibuc.prodeng.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import ro.unibuc.prodeng.model.UserDetails;

import java.security.Key;
import java.util.Date;

import static ro.unibuc.prodeng.security.jwt.AuthenticationTokenFilter.HEADER_PREFIX;

@Slf4j
@Component
public class JwtUtilities {
    @Value("${prodeng.jwtSecret}")
    private String jwtSecret;

    @Value("${prodeng.jwtExpirationMs}")
    private int jwtExpirationMs;

    public String generateJwtToken(Authentication authentication) {

        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();

        return Jwts.builder()
                .setSubject((userPrincipal.getUsername()))
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(key(), SignatureAlgorithm.HS512)
                .compact();
    }

    private Key key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    public String getUserNameFromJwtToken(String token) {
        if (token.startsWith(HEADER_PREFIX)) {
            token = token.substring(HEADER_PREFIX.length());
        }

        if (validateJwtToken(token)) {
            return Jwts.parserBuilder().setSigningKey(key()).build()
                    .parseClaimsJws(token).getBody().getSubject();
        }
        return null;
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(key()).build().parse(authToken);
            return true;
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }

        return false;
    }
}
