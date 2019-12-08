package authserver.jwt;

import authserver.models.User;
import io.jsonwebtoken.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${app.jwtSecret}")
    private String jwtSecret;

    @Value("${app.jwtExpirationInMs}")
    private int jwtExpirationInMs;

    @Value("${app.refreshExpirationInMs}")
    private int refreshExpirationInMs;

    public String[] bothRefreshed(String header){
        String token = null;
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            token = header.substring(7);
        }
        Jws<Claims> claims = Jwts.parser().require("type","refresh").setSigningKey(jwtSecret).parseClaimsJws(token);
        if(validateRefreshToken(token)){
            return bothFromEmail(claims.getBody().getSubject());
        }
        return new String[1];
    }

    private String[] bothFromEmail(String subject) {
        String[] arr = new String[2];
        arr[0] = accessFromEmail(subject);
        arr[1] = refreshFromEmail(subject);
        return arr;
    }

    public String generateToken(Authentication authentication) {
        User userPrincipal = (User) authentication.getPrincipal();
        return accessFromEmail(userPrincipal.getEmail());
    }
    public String accessFromEmail(String email){
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);
        return Jwts.builder()
                .claim("type", "access")
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS256, jwtSecret)
                .compact();
    }

    public String generateRefreshToken(Authentication authentication) {
        User userPrincipal = (User) authentication.getPrincipal();
        return refreshFromEmail(userPrincipal.getEmail());
    }

    public String refreshFromEmail(String email){
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpirationInMs);
        return Jwts.builder()
                .claim("type", "refresh")
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS256, jwtSecret)
                .compact();
    }

    public String getEmailFromJwt(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(jwtSecret)
                .parseClaimsJws(token)
                .getBody();
        System.out.println("parsing claims good");
        System.out.println(claims.getSubject());
        return claims.getSubject();
    }

    public boolean validateToken(String authToken) {
        try {
            logger.info(authToken);
            Jws<Claims> claims = Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(authToken);
            String type = (String) claims.getBody().get("type");
            logger.info(type);
            logger.info(claims.getBody().getExpiration().toInstant().toString());
            if(!type.equals("access")){
                return false;
            }
            return claims.getBody().getExpiration().toInstant().isAfter(Instant.now());
        } catch (MalformedJwtException ex) {
            logger.error("Invalid JWT token");
        } catch (UnsupportedJwtException ex) {
            logger.error("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            logger.error("JWT claims string is empty.");
        }
        return false;
    }

    public boolean validateIgnoreExpiration(String authToken, String secret) {
        try {
            Jwts.parser().require("type","access").setSigningKey(secret).parseClaimsJws(authToken);
            return true;
        } catch (MalformedJwtException ex) {
            logger.error("Invalid JWT token");
        } catch (UnsupportedJwtException ex) {
            logger.error("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            logger.error("JWT claims string is empty.");
        }
        return false;
    }

    public boolean validateRefreshToken(String authToken) {
        try {
            Jws<Claims> claims = Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(authToken);
            String type = (String) claims.getBody().get("type");
            if(!type.equals("refresh")){
                return false;
            }
            return claims.getBody().getExpiration().toInstant().isAfter(Instant.now());
        } catch (MalformedJwtException ex) {
            logger.error("Invalid JWT token");
        } catch (UnsupportedJwtException ex) {
            logger.error("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            logger.error("JWT claims string is empty.");
        }
        return false;
    }
}