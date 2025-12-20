package com.orbvpn.api.mapper;

import com.orbvpn.api.domain.dto.ServiceGroupView;
import com.orbvpn.api.domain.entity.ServiceGroup;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ServiceGroupViewMapper {

  private final GatewayViewMapper gatewayViewMapper;
  private final GeolocationViewMapper geolocationViewMapper;
  private final GroupViewMapper groupViewMapper;

  public ServiceGroupView toView(ServiceGroup serviceGroup) {
    if (serviceGroup == null) {
      return null;
    }

    ServiceGroupView view = new ServiceGroupView();
    view.setId(serviceGroup.getId());
    view.setName(serviceGroup.getName());
    view.setDescription(serviceGroup.getDescription());
    view.setLanguage(serviceGroup.getLanguage());
    view.setDiscount(serviceGroup.getDiscount());
    view.setDiscount3(serviceGroup.getDiscount3());
    view.setDiscount6(serviceGroup.getDiscount6());
    view.setDiscount12(serviceGroup.getDiscount12());
    view.setDiscount24(serviceGroup.getDiscount24());
    view.setDiscount36(serviceGroup.getDiscount36());
    view.setDiscountLifetime(serviceGroup.getDiscountLifetime());

    // Map groups without including serviceGroup to prevent circular reference
    if (serviceGroup.getGroups() != null) {
      view.setGroups(serviceGroup.getGroups().stream()
          .map(group -> groupViewMapper.toView(group, false))
          .collect(Collectors.toList()));
    }

    // Map gateways
    if (serviceGroup.getGateways() != null) {
      view.setGateways(serviceGroup.getGateways().stream()
          .map(gatewayViewMapper::toView)
          .collect(Collectors.toList()));
    }

    // Map geolocations
    if (serviceGroup.getAllowedGeolocations() != null) {
      view.setAllowedGeolocations(serviceGroup.getAllowedGeolocations().stream()
          .map(geolocationViewMapper::toView)
          .collect(Collectors.toList()));
    }

    if (serviceGroup.getDisAllowedGeolocations() != null) {
      view.setDisAllowedGeolocations(serviceGroup.getDisAllowedGeolocations().stream()
          .map(geolocationViewMapper::toView)
          .collect(Collectors.toList()));
    }

    return view;
  }
}