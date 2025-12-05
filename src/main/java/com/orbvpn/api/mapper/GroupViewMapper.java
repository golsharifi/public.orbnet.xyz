package com.orbvpn.api.mapper;

import com.orbvpn.api.domain.dto.GroupView;
import com.orbvpn.api.domain.dto.ServiceGroupView;
import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.ServiceGroup;
import org.springframework.stereotype.Component;

@Component
public class GroupViewMapper {

  public GroupView toView(Group group) {
    return toView(group, true);
  }

  public GroupView toView(Group group, boolean includeServiceGroup) {
    if (group == null) {
      return null;
    }

    GroupView view = new GroupView();
    view.setId(group.getId());
    view.setName(group.getName());
    view.setDescription(group.getDescription());
    view.setTagName(group.getTagName());
    view.setDuration(group.getDuration());
    view.setPrice(group.getPrice());
    view.setUsernamePostfix(group.getUsernamePostfix());
    view.setUsernamePostfixId(group.getUsernamePostfixId());
    view.setDailyBandwidth(group.getDailyBandwidth());
    view.setMultiLoginCount(group.getMultiLoginCount());
    view.setDownloadUpload(group.getDownloadUpload());
    view.setIp(group.getIp());

    if (includeServiceGroup) {
      ServiceGroup serviceGroup = group.getServiceGroup();
      if (serviceGroup != null) {
        view.setServiceGroup(toServiceGroupView(serviceGroup));
      }
    }

    return view;
  }

  private ServiceGroupView toServiceGroupView(ServiceGroup serviceGroup) {
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

    return view;
  }
}