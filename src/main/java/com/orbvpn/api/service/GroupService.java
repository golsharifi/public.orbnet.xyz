package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.GroupEdit;
import com.orbvpn.api.domain.dto.GroupView;
import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.ServiceGroup;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.mapper.GroupEditMapper;
import com.orbvpn.api.mapper.GroupViewMapper;
import com.orbvpn.api.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class GroupService {

  private final GroupRepository groupRepository;
  private final GroupViewMapper groupViewMapper;
  private final GroupEditMapper groupEditMapper;
  private final ServiceGroupService serviceGroupService;

  public GroupView createGroup(GroupEdit groupEdit) {
    Group group = groupEditMapper.create(groupEdit);

    // Add validation for ServiceGroup
    if (group.getServiceGroup() == null) {
      throw new IllegalArgumentException("ServiceGroup is required for Group creation");
    }

    groupRepository.save(group);
    return groupViewMapper.toView(group);
  }

  public GroupView editGroup(int id, GroupEdit groupEdit) {
    Group group = getById(id);

    Group edited = groupEditMapper.edit(group, groupEdit);

    groupRepository.save(edited);

    return groupViewMapper.toView(edited);
  }

  public GroupView deleteGroup(int id) {
    Group group = getById(id);

    groupRepository.delete(group);

    return groupViewMapper.toView(group);
  }

  public List<GroupView> getRegistrationGroups() {
    return groupRepository.findAllByRegistrationGroupIsTrue()
        .stream()
        .map(groupViewMapper::toView)
        .collect(Collectors.toList());
  }

  public List<GroupView> getAllGroups() {
    log.debug("Fetching all groups from repository");
    List<Group> groups = groupRepository.findAll();
    log.debug("Found {} groups", groups.size());

    try {
      List<GroupView> groupViews = groups.stream()
          .map(group -> {
            try {
              return groupViewMapper.toView(group);
            } catch (Exception e) {
              log.error("Error mapping group with id {}: {}", group.getId(), e.getMessage());
              return null;
            }
          })
          .filter(view -> view != null)
          .collect(Collectors.toList());

      log.debug("Successfully mapped {} groups to views", groupViews.size());
      return groupViews;
    } catch (Exception e) {
      log.error("Error while mapping groups to views: {}", e.getMessage());
      throw e;
    }
  }

  public List<GroupView> getGroups(int serviceGroupId) {
    ServiceGroup serviceGroup = serviceGroupService.getById(serviceGroupId);
    return groupRepository.findAllByServiceGroup(serviceGroup)
        .stream()
        .map(groupViewMapper::toView)
        .collect(Collectors.toList());
  }

  public GroupView getGroup(int id) {
    Group group = getById(id);

    return groupViewMapper.toView(group);
  }

  public Group getById(int id) {
    return groupRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("Group not found"));
  }

  public Group getGroupIgnoreDelete(int id) {
    return groupRepository.getGroupIgnoreDelete(id);
  }
}
