package com.orbvpn.api.config.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.orbvpn.api.domain.entity.User;
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

  public String generateAccessToken(User user) {
    String token = JWT.create()
        .withClaim("user_id", user.getId())
        .withClaim("username", user.getUsername())
        .withClaim("email", user.getEmail())
        .withClaim("subscription_tier", getSubscriptionTier(user))
        .withClaim("type", "access")
        .withIssuedAt(new Date())
        .withExpiresAt(new Date(System.currentTimeMillis() + jwtProperties.getExpirationMillis()))
        .sign(algorithm);

    log.info("Generated access token for user: {} (ID: {})", user.getUsername(), user.getId());
    return token;
  }

  // ✅ Keep this for backward compatibility (Spring Security uses UserDetails)
  public String generateAccessToken(UserDetails userDetails) {
    String token = JWT.create()
        .withClaim("username", userDetails.getUsername())
        .withClaim("type", "access")
        .withIssuedAt(new Date())
        .withExpiresAt(new Date(System.currentTimeMillis() + jwtProperties.getExpirationMillis()))
        .sign(algorithm);

    log.info("Generated basic access token for user: {}", userDetails.getUsername());
    log.warn("Token generated without full claims (user_id, email, subscription_tier). OrbX servers may reject this token.");
    return token;
  }

  // ✅ UPDATED: Add overload for User entity
  public String generateRefreshToken(User user) {
    long refreshExpiration = 30L * 24 * 60 * 60 * 1000; // 30 days
    
    String token = JWT.create()
        .withClaim("user_id", user.getId())
        .withClaim("username", user.getUsername())
        .withClaim("email", user.getEmail())
        .withClaim("subscription_tier", getSubscriptionTier(user))
        .withClaim("type", "refresh")
        .withIssuedAt(new Date())
        .withExpiresAt(new Date(System.currentTimeMillis() + refreshExpiration))
        .sign(algorithm);

    log.info("Generated refresh token for user: {} (ID: {})", user.getUsername(), user.getId());
    return token;
  }

  // ✅ Keep this for backward compatibility
  public String generateRefreshToken(UserDetails userDetails) {
    long refreshExpiration = 30L * 24 * 60 * 60 * 1000;
    
    String token = JWT.create()
        .withClaim("username", userDetails.getUsername())
        .withClaim("type", "refresh")
        .withIssuedAt(new Date())
        .withExpiresAt(new Date(System.currentTimeMillis() + refreshExpiration))
        .sign(algorithm);

    log.info("Generated basic refresh token for user: {}", userDetails.getUsername());
    return token;
  }

  private String getSubscriptionTier(User user) {
    try {
      if (user.getCurrentSubscription() != null && 
          user.getCurrentSubscription().getGroup() != null) {
        return user.getCurrentSubscription().getGroup().getName();
      }
    } catch (Exception e) {
      log.warn("Could not get subscription tier for user {}: {}", user.getId(), e.getMessage());
    }
    return "free"; // Default tier
  }

  public String getUsername(String token) {
    DecodedJWT decodedJWT = jwtVerifier.verify(token);
    String username = decodedJWT.getClaim("username").asString();

    log.info("Extracted username: {} from token", username);
    return username;
  }

  // ✅ ADD THIS METHOD
  public Integer getUserId(String token) {
    try {
      DecodedJWT decodedJWT = jwtVerifier.verify(token);
      return decodedJWT.getClaim("user_id").asInt();
    } catch (Exception e) {
      log.error("Error extracting user_id from token: {}", e.getMessage());
      return null;
    }
  }

  public String getTokenType(String token) {
    try {
      DecodedJWT decodedJWT = jwtVerifier.verify(token);
      String type = decodedJWT.getClaim("type").asString();
      return type != null ? type : "access";
    } catch (Exception e) {
      log.error("Error extracting token type: {}", e.getMessage());
      return "access";
    }
  }

  public boolean isRefreshToken(String token) {
    return "refresh".equals(getTokenType(token));
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