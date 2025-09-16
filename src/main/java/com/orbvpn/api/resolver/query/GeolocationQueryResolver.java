package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.GeolocationView;
import com.orbvpn.api.service.GeolocationService;
import com.orbvpn.api.exception.NotFoundException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class GeolocationQueryResolver {
  private final GeolocationService geolocationService;

  @Secured(USER)
  @QueryMapping
  public List<GeolocationView> geolocations() {
    log.info("Fetching all geolocations");
    try {
      List<GeolocationView> locations = geolocationService.getGeolocations();
      log.info("Successfully retrieved {} geolocations", locations.size());
      return locations;
    } catch (Exception e) {
      log.error("Error fetching geolocations - Error: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Secured(USER)
  @QueryMapping
  public GeolocationView geolocationByName(
      @Argument @Valid @NotBlank(message = "Geolocation name cannot be empty") String name) {
    log.info("Fetching geolocation by name: {}", name);
    try {
      GeolocationView location = geolocationService.getGeolocationByName(name);
      if (location == null) {
        throw new NotFoundException("Geolocation not found with name: " + name);
      }
      log.info("Successfully retrieved geolocation: {}", name);
      return location;
    } catch (NotFoundException e) {
      log.warn("Geolocation not found - Name: {} - Error: {}", name, e.getMessage());
      throw e;
    } catch (Exception e) {
      log.error("Error fetching geolocation by name: {} - Error: {}", name, e.getMessage(), e);
      throw e;
    }
  }
}