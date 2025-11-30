package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Controller
@RequiredArgsConstructor
public class ProfileMigrationMutationResolver {
    private final UserService userService;

    /**
     * Migration mutation to create profiles for users that don't have one.
     * This is an admin-only operation.
     * @return the number of profiles created
     */
    @Secured(ADMIN)
    @MutationMapping
    public int fixUsersWithoutProfiles() {
        return userService.fixUsersWithoutProfiles();
    }

    /**
     * Query to count users without profiles.
     * This is an admin-only operation.
     * @return the number of users without profiles
     */
    @Secured(ADMIN)
    @MutationMapping
    public int countUsersWithoutProfiles() {
        return userService.countUsersWithoutProfiles();
    }
}
