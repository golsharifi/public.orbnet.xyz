package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.MigrationResult;
import com.orbvpn.api.service.UserUuidMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Controller
@RequiredArgsConstructor
public class UuidMigrationMutationResolver {
    private final UserUuidMigrationService migrationService;

    @Secured(ADMIN)
    @MutationMapping
    public Boolean migrateUserUuids() {
        migrationService.migrateExistingUsers();
        return true;
    }

    @Secured(ADMIN)
    @MutationMapping
    public MigrationResult validateUserUuids() {
        return migrationService.validateUuids();
    }
}
