package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.Reseller;
import com.orbvpn.api.domain.entity.Role;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.enums.RoleName;
import com.orbvpn.api.exception.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccessService {

    private final UserService userService;

    /**
     * Checks if the current user has access to the specified user.
     *
     * @param user The user to check access for.
     * @throws AccessDeniedException if access is denied.
     */
    @Transactional
    public void checkResellerUserAccess(User user) {
        User accessorUser = userService.getUser();
        Reseller reseller = accessorUser.getReseller();
        Role accessorRole = accessorUser.getRole();

        // Use '!=' for primitive int comparison
        if (accessorRole.getName() != RoleName.ADMIN && user.getReseller().getId() != reseller.getId()) {
            throw new AccessDeniedException("Can't access user");
        }
    }
}