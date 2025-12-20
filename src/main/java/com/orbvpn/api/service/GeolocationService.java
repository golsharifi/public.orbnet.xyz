package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.GeolocationView;
import com.orbvpn.api.domain.entity.Geolocation;
import com.orbvpn.api.mapper.GeolocationViewMapper;
import com.orbvpn.api.repository.GeolocationRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Named; // ADD THIS IMPORT
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeolocationService {

  private final GeolocationRepository geolocationRepository;
  private final GeolocationViewMapper geolocationViewMapper;

  // These methods are fine - they don't return String
  public List<GeolocationView> getGeolocations() {
    return geolocationRepository.findAll()
        .stream()
        .map(geolocationViewMapper::toView)
        .collect(Collectors.toList());
  }

  public List<Geolocation> findAllById(List<Integer> ids) {
    return geolocationRepository.findAllById(ids);
  }

  public GeolocationView getGeolocationByName(String name) {
    Geolocation geolocation = geolocationRepository.findByName(name);
    return geolocationViewMapper.toView(geolocation);
  }

  public Geolocation getGeolocationByCountryCode(String countryCode) {
    log.debug("Looking up geolocation for country code: {}", countryCode);
    Geolocation geolocation = geolocationRepository.findByCountryCode(countryCode);
    if (geolocation != null) {
      log.debug("Found geolocation: {} for country: {}", geolocation.getName(), countryCode);
      return geolocation;
    }
    log.warn("No geolocation found for country code: {}", countryCode);
    return null;
  }

  // Add @Named to String-returning methods to prevent MapStruct auto-use
  @Named("getRegionByCountryCode") // ADD THIS
  public String getRegionByCountryCode(String countryCode) {
    if (countryCode == null || countryCode.isEmpty()) {
      return "Europe";
    }
    Geolocation geolocation = getGeolocationByCountryCode(countryCode);
    if (geolocation != null && geolocation.getRegion() != null) {
      return geolocation.getRegion();
    }
    return mapCountryToRegion(countryCode);
  }

  @Named("getCurrentUserIP") // ADD THIS
  public String getCurrentUserIP() {
    try {
      ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attributes != null) {
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
          return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
          return ip;
        }
        ip = request.getHeader("X-Azure-ClientIP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
          return ip;
        }
        String remoteAddr = request.getRemoteAddr();
        log.debug("Current user IP: {}", remoteAddr);
        return remoteAddr;
      }
    } catch (Exception e) {
      log.error("Error getting current user IP", e);
    }
    return null;
  }

  @Named("getCountryCodeFromIP") // ADD THIS
  public String getCountryCodeFromIP(String ipAddress) {
    if (ipAddress == null || ipAddress.isEmpty()) {
      return null;
    }
    log.debug("Getting country code for IP: {}", ipAddress);
    if (isPrivateIP(ipAddress)) {
      log.debug("Private IP detected, defaulting to IR");
      return "IR";
    }
    return "IR";
  }

  @Named("getCurrentUserRegion") // ADD THIS
  public String getCurrentUserRegion() {
    try {
      String ip = getCurrentUserIP();
      if (ip != null) {
        String countryCode = getCountryCodeFromIP(ip);
        if (countryCode != null) {
          return getRegionByCountryCode(countryCode);
        }
      }
    } catch (Exception e) {
      log.error("Error getting current user region", e);
    }
    return "Europe";
  }

  private boolean isPrivateIP(String ip) {
    return ip.startsWith("127.") || ip.startsWith("10.") ||
        ip.startsWith("192.168.") || ip.startsWith("172.16.") ||
        ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1");
  }

  private String mapCountryToRegion(String countryCode) {
    if (countryCode == null || countryCode.isEmpty()) {
      return "Europe";
    }
    String code = countryCode.toUpperCase();
    if (code.matches(
        "AT|BE|BG|HR|CY|CZ|DK|EE|FI|FR|DE|GR|HU|IE|IT|LV|LT|LU|MT|NL|PL|PT|RO|SK|SI|ES|SE|GB|UK|CH|NO|IS|TR|RU|UA")) {
      return "Europe";
    }
    if (code.matches("US|CA|MX|BR|AR|CL|CO|PE|VE")) {
      return "Americas";
    }
    if (code.matches("CN|JP|KR|IN|ID|TH|VN|MY|SG|PH|PK|BD|IR|IQ|SA|AE|IL|KW|QA|OM|BH|JO|LB|YE")) {
      return "Asia";
    }
    if (code.matches("ZA|EG|NG|KE|GH|TN|MA|DZ")) {
      return "Africa";
    }
    if (code.matches("AU|NZ|FJ")) {
      return "Oceania";
    }
    return "Europe";
  }
}