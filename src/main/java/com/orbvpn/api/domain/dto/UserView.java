package com.orbvpn.api.domain.dto;

import com.orbvpn.api.domain.entity.UserDeviceInfo;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.RoleName;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserView {
  private int id;
  private String email;
  private int resellerId;
  private String uuid;
  private String username;
  private String password;
  private RoleName role;
  private String radAccess;
  private String radAccessClear;
  private boolean enabled;
  private UserProfileView profile;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private UserSubscription currentSubscription;

  private List<DeviceView> userDeviceList;
  private List<UserDeviceInfo> userDevicesInfo;

  private UserSubscriptionView subscription;
  private List<UserSubscriptionView> userSubscriptionList;

  public void setCurrentSubscription(UserSubscription currentSubscription) {
    this.currentSubscription = currentSubscription;
  }

  public UserSubscription getCurrentSubscription() {
    return this.currentSubscription;
  }

}
