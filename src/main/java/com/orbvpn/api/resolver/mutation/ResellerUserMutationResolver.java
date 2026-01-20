package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.AdminPasswordResetResult;
import com.orbvpn.api.domain.dto.ResellerUserCreate;
import com.orbvpn.api.domain.dto.ResellerUserEdit;
import com.orbvpn.api.domain.dto.UserView;
import com.orbvpn.api.service.reseller.ResellerUserService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.stream.Collectors;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.RESELLER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ResellerUserMutationResolver {

    private final ResellerUserService resellerUserService;

    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    public UserView resellerCreateUser(@Argument("user") @Valid ResellerUserCreate userCreate) {
        log.info("Creating reseller user");
        try {
            return resellerUserService.createUser(userCreate);
        } catch (Exception e) {
            log.error("Error creating reseller user - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    public List<UserView> resellerDeleteUsers(
            @Argument @NotEmpty(message = "User IDs list cannot be empty") List<@Valid @Min(1) Integer> ids) {
        log.info("Deleting users: {}", ids);
        try {
            return ids.stream()
                    .map(id -> resellerUserService.deleteUserById(id))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error deleting users - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    public List<UserView> resellerDeleteUsersByEmails(
            @Argument @NotEmpty(message = "Email list cannot be empty") List<@Valid @Email String> emails) {
        log.info("Deleting users by emails: {}", emails);
        try {
            return emails.stream()
                    .map(email -> resellerUserService.deleteUserByEmail(email))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error deleting users by emails - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    public UserView resellerEditUser(
            @Argument @Valid @Min(1) int id,
            @Argument @Valid ResellerUserEdit userEdit) {
        log.info("Editing reseller user: {}", id);
        try {
            return resellerUserService.editUserById(id, userEdit);
        } catch (Exception e) {
            log.error("Error editing reseller user - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    public UserView resellerEditUserByEmail(
            @Argument("email") @Valid @Email String email,
            @Argument("resellerUserEdit") @Valid ResellerUserEdit userEdit) {
        log.info("Editing reseller user by email: {} with edits: {}", email, userEdit);
        try {
            if (userEdit == null) {
                throw new IllegalArgumentException("User edit data cannot be null");
            }
            return resellerUserService.editUserByEmail(email, userEdit);
        } catch (Exception e) {
            log.error("Error editing reseller user by email - Email: {} - Error: {}",
                    email, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Reset a user's password by admin/reseller.
     * Generates a new random password, sets it on the user's account,
     * and sends notifications to the user via all configured channels.
     * Returns the new password to the admin so they can communicate it if needed.
     */
    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    public AdminPasswordResetResult adminResetUserPassword(@Argument @Valid @Min(1) int userId) {
        log.info("Admin/Reseller resetting password for user ID: {}", userId);
        try {
            return resellerUserService.adminResetUserPassword(userId);
        } catch (Exception e) {
            log.error("Error resetting password for user ID: {} - Error: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Reset a user's password by admin/reseller using email.
     * Generates a new random password, sets it on the user's account,
     * and sends notifications to the user via all configured channels.
     * Returns the new password to the admin so they can communicate it if needed.
     */
    @Secured({ ADMIN, RESELLER })
    @MutationMapping
    public AdminPasswordResetResult adminResetUserPasswordByEmail(@Argument @Valid @Email String email) {
        log.info("Admin/Reseller resetting password for user email: {}", email);
        try {
            return resellerUserService.adminResetUserPasswordByEmail(email);
        } catch (Exception e) {
            log.error("Error resetting password for user email: {} - Error: {}", email, e.getMessage(), e);
            throw e;
        }
    }
}