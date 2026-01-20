package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.common.AesUtil;
import com.orbvpn.api.exception.NotFoundException;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

@Slf4j
@Controller
@RequiredArgsConstructor
public class EncryptionQueryResolver {
    private final UserService userService;

    // Legacy endpoint for old app - TODO: Remove when old app is retired
    @Secured(USER)
    @QueryMapping
    public UserLoginDetail userLoginDetail(
            @Argument @Valid @Positive(message = "User ID must be positive") Integer userId) {
        log.info("Fetching login details for user: {} (legacy endpoint)", userId);
        return fetchUserLoginDetail(userId);
    }

    // Admin endpoint for admin panel
    @Secured(ADMIN)
    @QueryMapping
    public UserLoginDetail adminGetUserLoginDetail(
            @Argument @Valid @Positive(message = "User ID must be positive") Integer userId) {
        log.info("Fetching login details for user: {} (admin endpoint)", userId);
        return fetchUserLoginDetail(userId);
    }

    private UserLoginDetail fetchUserLoginDetail(Integer userId) {
        try {
            User user = userService.getUserById(userId);
            if (user == null) {
                throw new NotFoundException("User not found with id: " + userId);
            }

            String aesKey = user.getAesKey();
            String aesIv = user.getAesIv();

            log.debug("Decrypting password for user: {}", userId);
            String decryptedPassword = AesUtil.decrypt(user.getPassword(), aesKey, aesIv);
            log.info("Successfully retrieved login details for user: {}", userId);

            return new UserLoginDetail(user.getId(), user.getUsername(), decryptedPassword);
        } catch (NotFoundException e) {
            log.warn("User not found - ID: {} - Error: {}", userId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error decrypting password for user: {} - Error: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Error decrypting password", e);
        }
    }

    public static class UserLoginDetail {
        private final int id;
        private final String username;
        private final String decryptedPassword;

        public UserLoginDetail(int id, String username, String decryptedPassword) {
            this.id = id;
            this.username = username;
            this.decryptedPassword = decryptedPassword;
        }

        public int getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public String getDecryptedPassword() {
            return decryptedPassword;
        }
    }
}