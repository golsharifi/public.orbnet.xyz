package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.MoreLoginCount;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserExtraLogins;
import com.orbvpn.api.repository.MoreLoginCountRepository;
import com.orbvpn.api.repository.UserExtraLoginsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MoreLoginCountService {
  private final MoreLoginCountRepository moreLoginCountRepository;
  private final UserExtraLoginsRepository userExtraLoginsRepository;
  private final RadiusService radiusService;

  @Transactional
  public void removeExpiredMoreLoginCount() {
    LocalDateTime now = LocalDateTime.now();

    // Handle legacy expired more login counts
    List<MoreLoginCount> expired = moreLoginCountRepository.findByExpiresAtBefore(now);
    handleExpiredLegacyLogins(expired);

    // Handle new system expired logins
    List<UserExtraLogins> expiredExtra = userExtraLoginsRepository
        .findByExpiryDateBeforeAndActive(now, true);
    handleExpiredExtraLogins(expiredExtra);
  }

  private void handleExpiredLegacyLogins(List<MoreLoginCount> expired) {
    for (MoreLoginCount moreLoginCount : expired) {
      try {
        User user = moreLoginCount.getUser();
        if (user == null) {
          moreLoginCountRepository.delete(moreLoginCount);
          continue;
        }

        int number = moreLoginCount.getNumber();
        radiusService.subUserMoreLoginCount(user, number);
        moreLoginCountRepository.delete(moreLoginCount);
      } catch (Exception ex) {
        log.error("Couldn't remove legacy more login for user {}: {}",
            moreLoginCount.getUser().getId(), ex.getMessage());
      }
    }
  }

  private void handleExpiredExtraLogins(List<UserExtraLogins> expired) {
    for (UserExtraLogins extraLogin : expired) {
      try {
        User user = extraLogin.getUser();
        if (user == null) {
          extraLogin.setActive(false);
          userExtraLoginsRepository.save(extraLogin);
          continue;
        }

        int number = extraLogin.getLoginCount();
        radiusService.subUserMoreLoginCount(user, number);
        extraLogin.setActive(false);
        userExtraLoginsRepository.save(extraLogin);

        log.info("Deactivated expired extra logins for user {}: {} logins",
            user.getId(), number);
      } catch (Exception ex) {
        log.error("Couldn't handle expired extra login for user {}: {}",
            extraLogin.getUser().getId(), ex.getMessage());
      }
    }
  }

  public void save(MoreLoginCount moreLoginCount) {
    moreLoginCountRepository.save(moreLoginCount);
  }

  // New method to migrate legacy to new system
  @Transactional
  public void migrateToNewSystem(User user) {
    List<MoreLoginCount> legacyLogins = moreLoginCountRepository.findByUser(user);

    for (MoreLoginCount legacyLogin : legacyLogins) {
      try {
        UserExtraLogins newLogin = new UserExtraLogins();
        newLogin.setUser(user);
        newLogin.setLoginCount(legacyLogin.getNumber());
        newLogin.setExpiryDate(legacyLogin.getExpiresAt());
        newLogin.setActive(true);
        newLogin.setStartDate(LocalDateTime.now());

        userExtraLoginsRepository.save(newLogin);
        moreLoginCountRepository.delete(legacyLogin);

        log.info("Migrated legacy login count for user {}: {} logins",
            user.getId(), legacyLogin.getNumber());
      } catch (Exception ex) {
        log.error("Failed to migrate legacy login count for user {}: {}",
            user.getId(), ex.getMessage());
      }
    }
  }
}