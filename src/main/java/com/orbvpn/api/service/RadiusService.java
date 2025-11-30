package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.*;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.repository.NasRepository;
import com.orbvpn.api.repository.RadAcctRepository;
import com.orbvpn.api.repository.RadCheckRepository;
import com.orbvpn.api.repository.UserExtraLoginsRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class RadiusService {
  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM dd yyyy HH:mm:ss");
  private static final Logger log = LoggerFactory.getLogger(RadiusService.class);
  private final NasRepository nasRepository;
  private final RadCheckRepository radCheckRepository;
  private final RadAcctRepository radAcctRepository;
  private final UserExtraLoginsRepository userExtraLoginsRepository;

  public void createNas(Server server) {
    Nas nas = new Nas();
    mapNas(nas, server);
    nasRepository.save(nas);
  }

  public void editNas(String nasname, Server server) {
    Nas nas = getNasByName(nasname);
    mapNas(nas, server);
    nasRepository.save(nas);
  }

  public void deleteNas(Server server) {
    Nas nas = getNasByName(server.getPublicIp());
    nasRepository.delete(nas);
  }

  public void mapNas(Nas nas, Server server) {
    nas.setNasName(server.getPublicIp());
    nas.setShortName(server.getHostName());
    nas.setType(server.getType());
    nas.setSecret(server.getSecret());
  }

  public void updateUserExpirationRadCheck(UserSubscription userSubscription) {
    User user = userSubscription.getUser();
    String username = user.getUsername();
    String updatedExpireDate = convertToExpirationString(userSubscription.getExpiresAt());

    log.debug("Updating expiration RadCheck for user: {}, new expiration: {}", username, updatedExpireDate);

    // Delete any existing Expiration entries to avoid duplicates
    radCheckRepository.deleteByUsernameAndAttribute(username, "Expiration");
    radCheckRepository.flush();

    // Create new Expiration RadCheck
    RadCheck newRadCheck = new RadCheck();
    newRadCheck.setUsername(username);
    newRadCheck.setAttribute("Expiration");
    newRadCheck.setOp("==");
    newRadCheck.setValue(updatedExpireDate);
    radCheckRepository.save(newRadCheck);

    log.info("Updated expiration RadCheck for user: {} to {}", username, updatedExpireDate);
  }

  @Transactional
  public void createUserRadChecks(UserSubscription userSubscription) {
    User user = userSubscription.getUser();
    String username = user.getUsername();

    log.info("Creating/updating RadChecks for user: {}", username);

    // Delete any existing RadCheck entries for this user to avoid duplicates
    radCheckRepository.deleteByUsername(username);
    radCheckRepository.flush();

    // Password
    String sha1Hex = user.getRadAccess();
    RadCheck passwordCheck = new RadCheck();
    passwordCheck.setUsername(username);
    passwordCheck.setAttribute("SHA-Password");
    passwordCheck.setOp(":=");
    passwordCheck.setValue(sha1Hex);

    // Simultaneous use
    RadCheck simultaneousCheck = new RadCheck();
    simultaneousCheck.setUsername(username);
    simultaneousCheck.setAttribute("Simultaneous-Use");
    simultaneousCheck.setOp(":=");
    simultaneousCheck.setValue(String.valueOf(userSubscription.getMultiLoginCount()));

    // Expiration
    RadCheck expirationCheck = new RadCheck();
    expirationCheck.setUsername(username);
    expirationCheck.setAttribute("Expiration");
    expirationCheck.setOp("==");
    expirationCheck.setValue(convertToExpirationString(userSubscription.getExpiresAt()));

    radCheckRepository.save(passwordCheck);
    radCheckRepository.save(simultaneousCheck);
    radCheckRepository.save(expirationCheck);

    log.info("Successfully created/updated RadChecks for user: {}", username);
  }

  public void deleteUserRadChecks(User user) {
    radCheckRepository.deleteByUsername(user.getUsername());
  }

  public void deleteUserRadAcct(User user) {
    radAcctRepository.deleteByUsername(user.getUsername());
  }

  /**
   * Updates or creates the SHA-Password RadCheck entry for a user
   * This method ensures the RadCheck password is always in sync with
   * user.radAccess
   * Handles duplicate entries by cleaning them up first
   */
  @Transactional
  public void editUserPassword(User user) {
    String username = user.getUsername();
    String sha1Hex = user.getRadAccess();

    log.debug("Updating RadCheck password for user: {}, SHA1: {}", username, sha1Hex);

    try {
      // Use the List method to get all SHA-Password entries for this user
      List<RadCheck> existingRadChecks = radCheckRepository
          .findByAttributeAndUsername("SHA-Password", username);

      if (existingRadChecks.size() > 1) {
        log.warn("Found {} duplicate SHA-Password entries for user: {}. Cleaning up duplicates.",
            existingRadChecks.size(), username);

        // Delete all existing entries using the specific delete method
        radCheckRepository.deleteByUsernameAndAttribute(username, "SHA-Password");
        radCheckRepository.flush();

        log.info("Cleaned up {} duplicate SHA-Password entries for user: {}",
            existingRadChecks.size(), username);

        // Create new clean entry
        RadCheck newRadCheck = new RadCheck();
        newRadCheck.setUsername(username);
        newRadCheck.setAttribute("SHA-Password");
        newRadCheck.setOp(":=");
        newRadCheck.setValue(sha1Hex);
        radCheckRepository.save(newRadCheck);
        log.info("Created new SHA-Password RadCheck after cleanup for user: {}", username);

      } else if (existingRadChecks.size() == 1) {
        // Update the single existing entry
        RadCheck radCheck = existingRadChecks.get(0);
        radCheck.setValue(sha1Hex);
        radCheckRepository.save(radCheck);
        log.debug("Updated existing SHA-Password RadCheck for user: {}", username);

      } else {
        // No existing entry - create new one
        RadCheck newRadCheck = new RadCheck();
        newRadCheck.setUsername(username);
        newRadCheck.setAttribute("SHA-Password");
        newRadCheck.setOp(":=");
        newRadCheck.setValue(sha1Hex);
        radCheckRepository.save(newRadCheck);
        log.info("Created new SHA-Password RadCheck for user: {}", username);
      }

    } catch (Exception e) {
      log.error("Error updating RadCheck password for user: {} - Error: {}", username, e.getMessage(), e);
      throw new RuntimeException("Failed to update RadCheck password for user: " + username, e);
    }
  }

  /**
   * Ensures all password-related entries are synchronized
   * This is useful for password resets and re-encryption scenarios
   */
  @Transactional
  public void synchronizeUserPassword(User user) {
    log.info("Synchronizing all password entries for user: {}", user.getUsername());

    // Update the SHA-Password RadCheck
    editUserPassword(user);

    // Verify synchronization
    String radAccess = user.getRadAccess();
    Optional<RadCheck> radCheck = radCheckRepository
        .findByUsernameAndAttribute(user.getUsername(), "SHA-Password");

    if (radCheck.isPresent()) {
      String radCheckValue = radCheck.get().getValue();
      if (!radAccess.equals(radCheckValue)) {
        log.error("Password synchronization failed for user: {}. RadAccess: {}, RadCheck: {}",
            user.getUsername(), radAccess, radCheckValue);
        throw new RuntimeException("Password synchronization failed for user: " + user.getUsername());
      } else {
        log.debug("Password synchronization verified for user: {}", user.getUsername());
      }
    } else {
      log.error("SHA-Password RadCheck not found after synchronization for user: {}", user.getUsername());
      throw new RuntimeException("SHA-Password RadCheck creation failed for user: " + user.getUsername());
    }
  }

  /**
   * Updates or creates the Simultaneous-Use RadCheck entry for a user
   * Ensures no duplicates by cleaning up existing entries first
   */
  @Transactional
  public void editUserMoreLoginCount(User user, int multiLoginCount) {
    String username = user.getUsername();

    log.debug("Updating Simultaneous-Use RadCheck for user: {}, count: {}", username, multiLoginCount);

    // Delete existing Simultaneous-Use entries to avoid duplicates
    radCheckRepository.deleteByUsernameAndAttribute(username, "Simultaneous-Use");
    radCheckRepository.flush();

    // Create new Simultaneous-Use RadCheck
    RadCheck newRadCheck = new RadCheck();
    newRadCheck.setUsername(username);
    newRadCheck.setAttribute("Simultaneous-Use");
    newRadCheck.setOp(":=");
    newRadCheck.setValue(String.valueOf(multiLoginCount));
    radCheckRepository.save(newRadCheck);

    log.info("Updated Simultaneous-Use RadCheck for user: {} to {}", username, multiLoginCount);
  }

  /**
   * Adds to the existing Simultaneous-Use count for a user
   */
  @Transactional
  public void addUserMoreLoginCount(User user, int moreLoginCount) {
    String username = user.getUsername();

    // Get current value
    List<RadCheck> radChecks = radCheckRepository.findByAttributeAndUsername("Simultaneous-Use", username);
    int currentValue = 1; // default

    if (!radChecks.isEmpty()) {
      currentValue = Integer.valueOf(radChecks.get(0).getValue());
    }

    int newValue = currentValue + moreLoginCount;

    // Update using the standard method to avoid duplicates
    editUserMoreLoginCount(user, newValue);

    log.info("Added {} to login count for user: {} (was: {}, now: {})",
        moreLoginCount, username, currentValue, newValue);
  }

  /**
   * Subtracts from the existing Simultaneous-Use count for a user
   */
  @Transactional
  public void subUserMoreLoginCount(User user, int moreLoginCount) {
    String username = user.getUsername();

    // Get current value
    List<RadCheck> radChecks = radCheckRepository.findByAttributeAndUsername("Simultaneous-Use", username);
    int currentValue = 1; // default

    if (!radChecks.isEmpty()) {
      currentValue = Integer.valueOf(radChecks.get(0).getValue());
    }

    int newValue = Math.max(1, currentValue - moreLoginCount); // minimum 1

    // Update using the standard method to avoid duplicates
    editUserMoreLoginCount(user, newValue);

    log.info("Subtracted {} from login count for user: {} (was: {}, now: {})",
        moreLoginCount, username, currentValue, newValue);
  }

  public String convertToExpirationString(LocalDateTime expiration) {
    ZonedDateTime ldtZoned = expiration.atZone(ZoneId.systemDefault());
    ZonedDateTime utcZoned = ldtZoned.withZoneSameInstant(ZoneId.of("UTC"));
    return DATE_FORMATTER.format(utcZoned);
  }

  public Nas getNasByName(String nasName) {
    return nasRepository.findByNasName(nasName)
        .orElseThrow(() -> new NotFoundException(Nas.class, nasName));
  }

  @Transactional
  public void updateUserTotalLoginCount(User user) {
    // Get base login count from user's subscription
    int baseLoginCount = user.getMultiLoginCount();

    // Get extra logins from active extra login records
    int extraLoginCount = userExtraLoginsRepository
        .findByUserAndActiveTrue(user)
        .stream()
        .filter(extra -> extra.getExpiryDate() == null ||
            extra.getExpiryDate().isAfter(LocalDateTime.now()))
        .mapToInt(UserExtraLogins::getLoginCount)
        .sum();

    // Total login count
    int totalLoginCount = baseLoginCount + extraLoginCount;

    // Update radius check
    updateUserRadCheck(user.getUsername(), "Simultaneous-Use", String.valueOf(totalLoginCount));

    log.info("Updated total login count for user {}: base={}, extra={}, total={}",
        user.getId(), baseLoginCount, extraLoginCount, totalLoginCount);
  }

  @Transactional
  public void handleExtraLoginsChange(User user, int changeAmount) {
    // Get current Simultaneous-Use value
    String currentValue = getCurrentRadCheckValue(user.getUsername(), "Simultaneous-Use");
    int currentCount = currentValue != null ? Integer.parseInt(currentValue) : 0;

    // Calculate new value
    int newCount = Math.max(0, currentCount + changeAmount);

    // Update radius check
    updateUserRadCheck(user.getUsername(), "Simultaneous-Use", String.valueOf(newCount));

    log.info("Updated login count for user {} by {}: {} -> {}",
        user.getId(), changeAmount, currentCount, newCount);
  }

  /**
   * Generic method to update any RadCheck attribute, avoiding duplicates
   */
  @Transactional
  private void updateUserRadCheck(String username, String attribute, String value) {
    log.debug("Updating RadCheck for user: {}, attribute: {}, value: {}", username, attribute, value);

    // Delete existing entries for this attribute to avoid duplicates
    radCheckRepository.deleteByUsernameAndAttribute(username, attribute);
    radCheckRepository.flush();

    // Create new entry
    RadCheck newRadCheck = new RadCheck();
    newRadCheck.setUsername(username);
    newRadCheck.setAttribute(attribute);
    newRadCheck.setOp(":=");
    newRadCheck.setValue(value);
    radCheckRepository.save(newRadCheck);

    log.debug("Successfully updated RadCheck for user: {}, attribute: {}", username, attribute);
  }

  private String getCurrentRadCheckValue(String username, String attribute) {
    List<RadCheck> radChecks = radCheckRepository.findByAttributeAndUsername(attribute, username);
    return radChecks.isEmpty() ? null : radChecks.get(0).getValue();
  }

  /**
   * Cleans up duplicate RadCheck entries for a specific user and attribute
   * Keeps only the most recent entry
   */
  @Transactional
  public void cleanupDuplicateRadChecks(String username, String attribute) {
    List<RadCheck> duplicates = radCheckRepository.findByAttributeAndUsername(attribute, username);

    if (duplicates.size() > 1) {
      log.warn("Found {} duplicate {} entries for user: {}, cleaning up",
          duplicates.size(), attribute, username);

      // Delete all and recreate with the latest value
      String latestValue = duplicates.get(duplicates.size() - 1).getValue();
      radCheckRepository.deleteByUsernameAndAttribute(username, attribute);
      radCheckRepository.flush();

      // Recreate with latest value
      RadCheck newRadCheck = new RadCheck();
      newRadCheck.setUsername(username);
      newRadCheck.setAttribute(attribute);
      newRadCheck.setOp(attribute.equals("Expiration") ? "==" : ":=");
      newRadCheck.setValue(latestValue);
      radCheckRepository.save(newRadCheck);

      log.info("Cleaned up duplicates for user: {}, attribute: {}", username, attribute);
    }
  }

  /**
   * Cleans up all duplicate RadCheck entries for a user
   */
  @Transactional
  public void cleanupAllUserRadCheckDuplicates(String username) {
    log.info("Cleaning up all RadCheck duplicates for user: {}", username);

    cleanupDuplicateRadChecks(username, "SHA-Password");
    cleanupDuplicateRadChecks(username, "Simultaneous-Use");
    cleanupDuplicateRadChecks(username, "Expiration");

    log.info("Completed RadCheck cleanup for user: {}", username);
  }

  /**
   * Checks if a user has a valid SHA-Password RadCheck entry that matches their
   * radAccess
   */
  public boolean hasValidRadCheckPassword(User user) {
    String username = user.getUsername();
    String expectedRadAccess = user.getRadAccess();

    if (StringUtils.isEmpty(expectedRadAccess)) {
      log.debug("User {} has no radAccess value", username);
      return false;
    }

    // Use the List method to avoid the "unique result" exception
    List<RadCheck> radChecks = radCheckRepository
        .findByAttributeAndUsername("SHA-Password", username);

    if (radChecks.isEmpty()) {
      log.debug("User {} has no SHA-Password RadCheck entry", username);
      return false;
    }

    if (radChecks.size() > 1) {
      log.warn("User {} has {} duplicate SHA-Password RadCheck entries", username, radChecks.size());
      // Return false to trigger cleanup
      return false;
    }

    String radCheckValue = radChecks.get(0).getValue();
    boolean isValid = expectedRadAccess.equals(radCheckValue);

    if (!isValid) {
      log.debug("User {} has mismatched password values. RadAccess: {}, RadCheck: {}",
          username, expectedRadAccess, radCheckValue);
    }

    return isValid;
  }
}