package com.orbvpn.api.config.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.properties.JWTProperties;
import com.orbvpn.api.repository.TrialHistoryRepository;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
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
  private final TrialHistoryRepository trialHistoryRepository;

  public String generateAccessToken(User user) {
    String jti = UUID.randomUUID().toString();
    Date now = new Date();
    Date expiresAt = new Date(System.currentTimeMillis() + jwtProperties.getExpirationMillis());

    JWTCreator.Builder builder = JWT.create()
        // Standard JWT Claims (RFC 7519)
        .withIssuer(jwtProperties.getIssuer())
        .withSubject(String.valueOf(user.getId()))
        .withJWTId(jti)
        .withIssuedAt(now)
        .withExpiresAt(expiresAt)

        // User Identity Claims
        .withClaim("user_id", user.getId())
        .withClaim("username", user.getUsername())
        .withClaim("email", user.getEmail())
        .withClaim("type", "access")

        // Account Status Claims
        .withClaim("active", user.isActive())
        .withClaim("enabled", user.isEnabled());

    // Role/Permission Claims
    if (user.getRole() != null && user.getRole().getName() != null) {
      builder.withClaim("role", user.getRole().getName().name());
    }

    // Reseller Claims
    if (user.getReseller() != null) {
      builder.withClaim("reseller_id", user.getReseller().getId());
    }

    // Subscription Claims
    addSubscriptionClaims(builder, user);

    String token = builder.sign(algorithm);

    log.info("Generated access token for user: {} (ID: {}) with jti: {}",
        user.getUsername(), user.getId(), jti);
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
    log.warn("Token generated without full claims (user_id, email, subscription_tier). OrbMesh servers may reject this token.");
    return token;
  }

  // Refresh token with full user details
  public String generateRefreshToken(User user) {
    long refreshExpiration = 30L * 24 * 60 * 60 * 1000; // 30 days
    String jti = UUID.randomUUID().toString();
    Date now = new Date();
    Date expiresAt = new Date(System.currentTimeMillis() + refreshExpiration);

    JWTCreator.Builder builder = JWT.create()
        // Standard JWT Claims (RFC 7519)
        .withIssuer(jwtProperties.getIssuer())
        .withSubject(String.valueOf(user.getId()))
        .withJWTId(jti)
        .withIssuedAt(now)
        .withExpiresAt(expiresAt)

        // User Identity Claims
        .withClaim("user_id", user.getId())
        .withClaim("username", user.getUsername())
        .withClaim("email", user.getEmail())
        .withClaim("type", "refresh")

        // Account Status Claims
        .withClaim("active", user.isActive())
        .withClaim("enabled", user.isEnabled());

    // Role/Permission Claims
    if (user.getRole() != null && user.getRole().getName() != null) {
      builder.withClaim("role", user.getRole().getName().name());
    }

    // Reseller Claims
    if (user.getReseller() != null) {
      builder.withClaim("reseller_id", user.getReseller().getId());
    }

    // Subscription Claims
    addSubscriptionClaims(builder, user);

    String token = builder.sign(algorithm);

    log.info("Generated refresh token for user: {} (ID: {}) with jti: {}",
        user.getUsername(), user.getId(), jti);
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

  /**
   * Add comprehensive subscription claims to the JWT token.
   * This includes tier, expiration, trial status, device limits, and more.
   */
  private void addSubscriptionClaims(JWTCreator.Builder builder, User user) {
    try {
      UserSubscription subscription = user.getCurrentSubscription();

      if (subscription != null) {
        // Subscription Identity
        builder.withClaim("subscription_id", subscription.getId());

        // Subscription Tier
        if (subscription.getGroup() != null) {
          builder.withClaim("subscription_tier", subscription.getGroup().getName());
          builder.withClaim("subscription_group_id", subscription.getGroup().getId());
        } else {
          builder.withClaim("subscription_tier", "free");
        }

        // Expiration (as Unix timestamp for easy client-side handling)
        if (subscription.getExpiresAt() != null) {
          builder.withClaim("subscription_expires_at",
              subscription.getExpiresAt().toEpochSecond(ZoneOffset.UTC));
        }

        // Trial Status
        builder.withClaim("is_trial", Boolean.TRUE.equals(subscription.getIsTrialPeriod()));
        if (subscription.getTrialEndDate() != null) {
          builder.withClaim("trial_ends_at",
              subscription.getTrialEndDate().toEpochSecond(ZoneOffset.UTC));
        }

        // Device/Connection Limits
        builder.withClaim("device_limit", subscription.getMultiLoginCount());

        // Subscription Status
        if (subscription.getStatus() != null) {
          builder.withClaim("subscription_status", subscription.getStatus().name());
        }

        // Token-based subscription info (ad-supported)
        builder.withClaim("is_token_based", Boolean.TRUE.equals(subscription.getIsTokenBased()));

        // Auto-renewal status
        builder.withClaim("auto_renew", Boolean.TRUE.equals(subscription.getAutoRenew()));

        // Payment gateway (useful for managing subscriptions)
        if (subscription.getGateway() != null) {
          builder.withClaim("payment_gateway", subscription.getGateway().name());
        }

        // Subscription validity flag
        builder.withClaim("subscription_valid", subscription.isValid());

      } else {
        // No active subscription - set defaults
        builder.withClaim("subscription_tier", "free");
        builder.withClaim("subscription_valid", false);
        builder.withClaim("is_trial", false);
        builder.withClaim("is_token_based", false);
        builder.withClaim("device_limit", 1);
      }

      // Trial eligibility (check if user has EVER had a trial)
      builder.withClaim("trial_eligible", !hasUserUsedTrial(user));

    } catch (Exception e) {
      log.warn("Could not add subscription claims for user {}: {}", user.getId(), e.getMessage());
      // Add defaults on error
      builder.withClaim("subscription_tier", "free");
      builder.withClaim("subscription_valid", false);
    }
  }

  /**
   * Check if user has ever used a trial subscription.
   * Used to determine trial eligibility for new purchases.
   * Checks both UserSubscription list AND TrialHistory table.
   */
  private boolean hasUserUsedTrial(User user) {
    try {
      // Check 1: Look for trial subscriptions in user's subscription list
      if (user.getUserSubscriptionList() != null) {
        boolean hasTrialInSubscriptions = user.getUserSubscriptionList().stream()
            .anyMatch(sub -> Boolean.TRUE.equals(sub.getIsTrialPeriod()));
        if (hasTrialInSubscriptions) {
          log.debug("User {} has trial in subscription list", user.getId());
          return true;
        }
      }

      // Check 2: Look for records in TrialHistory table
      boolean hasTrialHistoryRecord = trialHistoryRepository.hasTrialHistory((long) user.getId());
      if (hasTrialHistoryRecord) {
        log.debug("User {} has trial history record", user.getId());
        return true;
      }

      return false;
    } catch (Exception e) {
      log.debug("Could not check trial history for user {}: {}", user.getId(), e.getMessage());
      // In case of error, assume they haven't used trial (allow trial)
      return false;
    }
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

  // ================== New Extraction Methods ==================

  /**
   * Get the JWT ID (jti) claim - useful for token revocation.
   */
  public String getJwtId(String token) {
    try {
      DecodedJWT decodedJWT = jwtVerifier.verify(token);
      return decodedJWT.getId();
    } catch (Exception e) {
      log.error("Error extracting jti from token: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Get the issuer (iss) claim.
   */
  public String getIssuer(String token) {
    try {
      DecodedJWT decodedJWT = jwtVerifier.verify(token);
      return decodedJWT.getIssuer();
    } catch (Exception e) {
      log.error("Error extracting issuer from token: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Get the user's role from the token.
   */
  public String getRole(String token) {
    try {
      DecodedJWT decodedJWT = jwtVerifier.verify(token);
      return decodedJWT.getClaim("role").asString();
    } catch (Exception e) {
      log.error("Error extracting role from token: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Get subscription expiration as Unix timestamp.
   */
  public Long getSubscriptionExpiresAt(String token) {
    try {
      DecodedJWT decodedJWT = jwtVerifier.verify(token);
      return decodedJWT.getClaim("subscription_expires_at").asLong();
    } catch (Exception e) {
      log.error("Error extracting subscription_expires_at from token: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Get subscription tier/plan name.
   */
  public String getSubscriptionTier(String token) {
    try {
      DecodedJWT decodedJWT = jwtVerifier.verify(token);
      return decodedJWT.getClaim("subscription_tier").asString();
    } catch (Exception e) {
      log.error("Error extracting subscription_tier from token: {}", e.getMessage());
      return "free";
    }
  }

  /**
   * Check if subscription is valid.
   */
  public Boolean isSubscriptionValid(String token) {
    try {
      DecodedJWT decodedJWT = jwtVerifier.verify(token);
      return decodedJWT.getClaim("subscription_valid").asBoolean();
    } catch (Exception e) {
      log.error("Error extracting subscription_valid from token: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Check if user is on a trial subscription.
   */
  public Boolean isTrial(String token) {
    try {
      DecodedJWT decodedJWT = jwtVerifier.verify(token);
      return decodedJWT.getClaim("is_trial").asBoolean();
    } catch (Exception e) {
      log.error("Error extracting is_trial from token: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Check if user is eligible for trial.
   */
  public Boolean isTrialEligible(String token) {
    try {
      DecodedJWT decodedJWT = jwtVerifier.verify(token);
      return decodedJWT.getClaim("trial_eligible").asBoolean();
    } catch (Exception e) {
      log.error("Error extracting trial_eligible from token: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Get device/connection limit.
   */
  public Integer getDeviceLimit(String token) {
    try {
      DecodedJWT decodedJWT = jwtVerifier.verify(token);
      return decodedJWT.getClaim("device_limit").asInt();
    } catch (Exception e) {
      log.error("Error extracting device_limit from token: {}", e.getMessage());
      return 1;
    }
  }

  /**
   * Check if account is active.
   */
  public Boolean isActive(String token) {
    try {
      DecodedJWT decodedJWT = jwtVerifier.verify(token);
      return decodedJWT.getClaim("active").asBoolean();
    } catch (Exception e) {
      log.error("Error extracting active from token: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Get reseller ID if user belongs to a reseller.
   */
  public Integer getResellerId(String token) {
    try {
      DecodedJWT decodedJWT = jwtVerifier.verify(token);
      return decodedJWT.getClaim("reseller_id").asInt();
    } catch (Exception e) {
      log.debug("No reseller_id in token (may be normal): {}", e.getMessage());
      return null;
    }
  }

  /**
   * Get subscription ID.
   */
  public Integer getSubscriptionId(String token) {
    try {
      DecodedJWT decodedJWT = jwtVerifier.verify(token);
      return decodedJWT.getClaim("subscription_id").asInt();
    } catch (Exception e) {
      log.error("Error extracting subscription_id from token: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Get payment gateway name.
   */
  public String getPaymentGateway(String token) {
    try {
      DecodedJWT decodedJWT = jwtVerifier.verify(token);
      return decodedJWT.getClaim("payment_gateway").asString();
    } catch (Exception e) {
      log.debug("No payment_gateway in token: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Check if subscription is token-based (ad-supported).
   */
  public Boolean isTokenBased(String token) {
    try {
      DecodedJWT decodedJWT = jwtVerifier.verify(token);
      return decodedJWT.getClaim("is_token_based").asBoolean();
    } catch (Exception e) {
      log.error("Error extracting is_token_based from token: {}", e.getMessage());
      return false;
    }
  }
}