package com.orbvpn.api.mapper;

import com.orbvpn.api.config.LocaleConfig;
import com.orbvpn.api.domain.dto.ResellerUserCreate;
import com.orbvpn.api.domain.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Locale;

@Mapper(componentModel = "spring", imports = { LocaleConfig.class })
public abstract class ResellerUserCreateMapper {

  @Mapping(source = "language", target = "profile.language", qualifiedByName = "mapLocale")
  @Mapping(target = "createdAt", expression = "java(java.time.LocalDateTime.now())")
  @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now())")
  @Mapping(target = "enabled", constant = "true")
  @Mapping(target = "active", constant = "true")
  @Mapping(target = "profile", ignore = true)
  @Mapping(target = "username", source = "userName")
  @Mapping(target = "role", ignore = true)
  @Mapping(target = "reseller", ignore = true)
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "aesIv", ignore = true)
  @Mapping(target = "aesKey", ignore = true)
  @Mapping(target = "autoRenew", ignore = true)
  @Mapping(target = "oauthId", ignore = true)
  @Mapping(target = "passwordResetList", ignore = true)
  @Mapping(target = "paymentList", ignore = true)
  @Mapping(target = "radAccess", ignore = true)
  @Mapping(target = "radAccessClear", ignore = true)
  @Mapping(target = "referralCode", ignore = true)
  @Mapping(target = "stripeCustomer", ignore = true)
  @Mapping(target = "stripeCustomerId", ignore = true)
  @Mapping(target = "stripeData", ignore = true)
  @Mapping(target = "ticketList", ignore = true)
  @Mapping(target = "userSubscriptionList", ignore = true)
  @Mapping(target = "authorities", ignore = true)
  public abstract User create(ResellerUserCreate userCreate);

  @Named("mapLocale")
  protected Locale mapLocale(Locale locale) {
    return locale != null ? locale : LocaleConfig.DEFAULT_LOCALE;
  }
}