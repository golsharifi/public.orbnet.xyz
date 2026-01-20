package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.RoleView;
import com.orbvpn.api.mapper.RoleViewMapper;
import com.orbvpn.api.service.RoleService;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.stream.Collectors;

import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RoleQueryResolver {
    private final RoleService roleService;
    private final RoleViewMapper roleViewMapper;

    @Secured(USER)
    @QueryMapping
    public List<RoleView> getAllUserRoles() {
        log.info("Fetching all user roles");
        try {
            List<RoleView> roles = roleService.findAll().stream()
                    .map(roleViewMapper::toView)
                    .collect(Collectors.toList());
            log.info("Successfully retrieved {} roles", roles.size());
            return roles;
        } catch (Exception e) {
            log.error("Error fetching user roles - Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}