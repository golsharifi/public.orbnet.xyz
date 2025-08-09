package com.orbvpn.api.config.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.orbvpn.api.properties.JWTProperties;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class JwtTokenUtil {

  private final JWTProperties jwtProperties;
  private final Algorithm algorithm;
  private final JWTVerifier jwtVerifier;

  public String generateAccessToken(UserDetails user) {
    String token = JWT.create()
        .withClaim("username", user.getUsername())
        .withIssuedAt(new Date())
        .withExpiresAt(new Date(System.currentTimeMillis() + jwtProperties.getExpirationMillis()))
        .sign(algorithm);

    log.info("Generated access token for user: {}", user.getUsername());
    return token;
  }

  public String getUsername(String token) {
    DecodedJWT decodedJWT = jwtVerifier.verify(token);
    String username = decodedJWT.getClaim("username").asString();

    log.info("Extracted username: {} from token", username);
    return username;
  }

  public Date getExpirationDate(String token) {
    DecodedJWT decodedJWT = jwtVerifier.verify(token);
    Date expirationDate = decodedJWT.getExpiresAt();

    log.info("Extracted expiration date: {} from token", expirationDate);
    return expirationDate;
  }

  public boolean isTokenExpiring(String token) {
    Date expirationDate = getExpirationDate(token);
    boolean isExpiring = expirationDate.getTime() - System.currentTimeMillis() < jwtProperties.getRefreshMillis();

    log.info("Token is expiring: {}", isExpiring);
    return isExpiring;
  }

  public boolean validate(String token) {
    try {
      jwtVerifier.verify(token);
      log.info("Token validated successfully");
      return true;
    } catch (Exception ex) {
      log.error("Invalid JWT signature - {}", ex.getMessage());
    }
    return false;
  }

  public String getEmail(String token) {
    DecodedJWT decodedJWT = jwtVerifier.verify(token);
    String email = decodedJWT.getClaim("email").asString();

    log.info("Extracted email: {} from token", email);
    return email;
  }
}