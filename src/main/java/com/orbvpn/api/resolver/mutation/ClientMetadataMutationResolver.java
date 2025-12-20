package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.config.security.Unsecured;
import com.orbvpn.api.domain.dto.AuthenticatedUser;
import com.orbvpn.api.domain.dto.ClientMetadataInput;
import com.orbvpn.api.domain.dto.ClientMetadataView;
import com.orbvpn.api.domain.dto.SignupResponse;
import com.orbvpn.api.domain.entity.ClientMetadata;
import com.orbvpn.api.domain.entity.UnverifiedUser;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.service.ClientMetadataService;
import com.orbvpn.api.service.PasswordService;
import com.orbvpn.api.service.UserRegistrationService;
import com.orbvpn.api.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

/**
 * GraphQL mutation resolver for client metadata operations.
 * Supports recording device/location/platform info for analytics and security.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ClientMetadataMutationResolver {

    private final ClientMetadataService clientMetadataService;
    private final UserRegistrationService userRegistrationService;
    private final UserService userService;
    private final PasswordService passwordService;

    /**
     * Record client metadata for any event type.
     * Can be called with or without authentication.
     */
    @Unsecured
    @MutationMapping
    public ClientMetadataView recordClientMetadata(
            @Argument @NotBlank String eventType,
            @Argument ClientMetadataInput clientMetadata) {
        log.debug("Recording client metadata for event: {}", eventType);
        try {
            User user = getCurrentUser();
            ClientMetadata metadata = clientMetadataService.recordMetadata(user, eventType, clientMetadata);
            return metadata != null ? toView(metadata) : null;
        } catch (Exception e) {
            log.error("Failed to record client metadata: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Enhanced signup that also records client metadata.
     * Backward compatible with existing signup - clientMetadata is optional.
     */
    @Unsecured
    @MutationMapping
    public SignupResponse signupWithMetadata(
            @Argument @Valid @Email String email,
            @Argument @Valid @NotBlank String password,
            @Argument String referral,
            @Argument ClientMetadataInput clientMetadata) {
        log.info("Processing signup with metadata for email: {}", email);
        try {
            UnverifiedUser unverifiedUser = new UnverifiedUser();
            unverifiedUser.setEmail(email);
            passwordService.setPassword(unverifiedUser, password);

            SignupResponse response = userRegistrationService.register(unverifiedUser);

            // Record signup metadata (async to not slow down signup)
            if (Boolean.TRUE.equals(response.getSuccess())) {
                try {
                    // For unverified users, we record with null user - will be linked later
                    clientMetadataService.recordMetadataAsync(null, ClientMetadata.EVENT_SIGNUP, clientMetadata);
                } catch (Exception e) {
                    log.warn("Failed to record signup metadata: {}", e.getMessage());
                }
            }

            return response;
        } catch (RuntimeException re) {
            log.error("Registration failed - Error: {}", re.getMessage(), re);
            return new SignupResponse(re.getMessage(), false);
        } catch (Exception e) {
            log.error("Unexpected error during registration - Error: {}", e.getMessage(), e);
            return new SignupResponse("Registration failed.", false);
        }
    }

    /**
     * Enhanced login that also records client metadata.
     * Backward compatible with existing login - clientMetadata is optional.
     */
    @Unsecured
    @MutationMapping
    public AuthenticatedUser loginWithMetadata(
            @Argument String email,
            @Argument String password,
            @Argument ClientMetadataInput clientMetadata) {
        log.info("Processing login with metadata for email: {}", email);
        try {
            AuthenticatedUser result = userService.login(email, password);
            if (result != null) {
                // Record login metadata async
                try {
                    User user = userService.getUserByEmail(email);
                    clientMetadataService.recordMetadataAsync(user, ClientMetadata.EVENT_LOGIN, clientMetadata);
                } catch (Exception e) {
                    log.warn("Failed to record login metadata: {}", e.getMessage());
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Login failed for user: {} - Error: {}", email, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get the currently authenticated user, if any.
     */
    private User getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof User) {
                return (User) authentication.getPrincipal();
            }
        } catch (Exception e) {
            log.debug("Could not get current user: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Convert entity to view DTO
     */
    private ClientMetadataView toView(ClientMetadata metadata) {
        ClientMetadataView view = new ClientMetadataView();
        view.setId(metadata.getId());
        view.setUserId(metadata.getUser() != null ? metadata.getUser().getId() : null);
        view.setUserEmail(metadata.getUser() != null ? metadata.getUser().getEmail() : null);
        view.setEventType(metadata.getEventType());
        view.setIpAddress(metadata.getIpAddress());
        view.setCountryCode(metadata.getCountryCode());
        view.setCountryName(metadata.getCountryName());
        view.setRegion(metadata.getRegion());
        view.setCity(metadata.getCity());
        view.setLatitude(metadata.getLatitude());
        view.setLongitude(metadata.getLongitude());
        view.setTimezone(metadata.getTimezone());
        view.setIsp(metadata.getIsp());
        view.setPlatform(metadata.getPlatform());
        view.setOsName(metadata.getOsName());
        view.setOsVersion(metadata.getOsVersion());
        view.setDeviceType(metadata.getDeviceType());
        view.setDeviceManufacturer(metadata.getDeviceManufacturer());
        view.setDeviceModel(metadata.getDeviceModel());
        view.setScreenResolution(metadata.getScreenResolution());
        view.setBrowserName(metadata.getBrowserName());
        view.setBrowserVersion(metadata.getBrowserVersion());
        view.setUserAgent(metadata.getUserAgent());
        view.setAppVersion(metadata.getAppVersion());
        view.setAppBuild(metadata.getAppBuild());
        view.setAppIdentifier(metadata.getAppIdentifier());
        view.setLanguage(metadata.getLanguage());
        view.setAcceptedLanguages(metadata.getAcceptedLanguages());
        view.setLocale(metadata.getLocale());
        view.setReferrer(metadata.getReferrer());
        view.setUtmSource(metadata.getUtmSource());
        view.setUtmMedium(metadata.getUtmMedium());
        view.setUtmCampaign(metadata.getUtmCampaign());
        view.setCreatedAt(metadata.getCreatedAt());
        return view;
    }
}
