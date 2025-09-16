package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.MigrationResult;
import com.orbvpn.api.service.UserUuidMigrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class UuidMigrationMutationResolver {
    private final UserUuidMigrationService migrationService;

    @Secured("ROLE_ADMIN")
    @MutationMapping
    public Boolean migrateUserUuids() {
        migrationService.migrateExistingUsers();
        return true;
    }

    @Secured("ROLE_ADMIN")
    @MutationMapping
    public MigrationResult validateUserUuids() {
        return migrationService.validateUuids();
    }
}
