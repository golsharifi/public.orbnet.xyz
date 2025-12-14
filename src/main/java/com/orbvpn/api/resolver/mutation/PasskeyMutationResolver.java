package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.config.security.JwtTokenUtil;
import com.orbvpn.api.config.security.Unsecured;
import com.orbvpn.api.domain.dto.AuthenticatedUser;
import com.orbvpn.api.domain.dto.UserView;
import com.orbvpn.api.domain.dto.passkey.PasskeyAuthenticationRequest;
import com.orbvpn.api.domain.dto.passkey.PasskeyRegistrationRequest;
import com.orbvpn.api.domain.dto.passkey.PasskeyView;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.mapper.UserViewMapper;
import com.orbvpn.api.repository.UserRepository;
import com.orbvpn.api.service.PasskeyService;
import com.orbvpn.api.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;
import static com.orbvpn.api.domain.enums.RoleName.Constants.USER;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PasskeyMutationResolver {

    private final PasskeyService passkeyService;
    private final UserService userService;
    private final JwtTokenUtil jwtTokenUtil;
    private final UserViewMapper userViewMapper;
    private final UserRepository userRepository;

    @Secured({USER, ADMIN})
    @MutationMapping
    public PasskeyView registerPasskey(@Argument Map<String, Object> input) {
        log.info("Registering new passkey");
        User user = getCurrentUser();

        PasskeyRegistrationRequest request = mapToRegistrationRequest(input);
        return passkeyService.verifyRegistration(user, request);
    }

    @Unsecured
    @MutationMapping
    public AuthenticatedUser authenticateWithPasskey(@Argument Map<String, Object> input) {
        log.info("Authenticating with passkey");

        PasskeyAuthenticationRequest request = mapToAuthenticationRequest(input);
        User user = passkeyService.verifyAuthentication(request);

        // Generate JWT tokens
        User userWithDetails = userRepository.findByIdWithDetails(user.getId())
                .orElse(user);

        UserView userView = userViewMapper.toView(userWithDetails);
        String accessToken = jwtTokenUtil.generateAccessToken(userWithDetails);
        String refreshToken = jwtTokenUtil.generateRefreshToken(userWithDetails);

        log.info("Passkey authentication successful for user: {}", user.getEmail());
        return new AuthenticatedUser(accessToken, refreshToken, userView);
    }

    @Secured({USER, ADMIN})
    @MutationMapping
    public Boolean deletePasskey(@Argument Long id) {
        log.info("Deleting passkey with ID: {}", id);
        User user = getCurrentUser();
        return passkeyService.deletePasskey(user, id);
    }

    @Secured({USER, ADMIN})
    @MutationMapping
    public PasskeyView renamePasskey(@Argument Long id, @Argument String name) {
        log.info("Renaming passkey {} to: {}", id, name);
        User user = getCurrentUser();
        return passkeyService.renamePasskey(user, id, name);
    }

    @Secured(ADMIN)
    @MutationMapping
    public Boolean deleteUserPasskey(@Argument Integer userId, @Argument Long passkeyId) {
        log.info("Admin deleting passkey {} for user {}", passkeyId, userId);
        return passkeyService.deletePasskeyAsAdmin(userId, passkeyId);
    }

    @Secured(ADMIN)
    @MutationMapping
    public Boolean deleteUserOAuthConnection(@Argument Integer userId, @Argument Integer connectionId) {
        log.info("Admin deleting OAuth connection {} for user {}", connectionId, userId);
        return passkeyService.deleteOAuthConnectionAsAdmin(userId, connectionId);
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.getUserByUsername(username);
    }

    @SuppressWarnings("unchecked")
    private PasskeyRegistrationRequest mapToRegistrationRequest(Map<String, Object> input) {
        PasskeyRegistrationRequest request = new PasskeyRegistrationRequest();
        request.setId((String) input.get("id"));
        request.setRawId((String) input.get("rawId"));
        request.setType((String) input.get("type"));
        request.setAuthenticatorAttachment((String) input.get("authenticatorAttachment"));
        request.setPasskeyName((String) input.get("passkeyName"));

        Map<String, Object> responseMap = (Map<String, Object>) input.get("response");
        if (responseMap != null) {
            PasskeyRegistrationRequest.AuthenticatorResponse response = new PasskeyRegistrationRequest.AuthenticatorResponse();
            response.setClientDataJSON((String) responseMap.get("clientDataJSON"));
            response.setAttestationObject((String) responseMap.get("attestationObject"));
            response.setTransports((List<String>) responseMap.get("transports"));
            request.setResponse(response);
        }

        return request;
    }

    @SuppressWarnings("unchecked")
    private PasskeyAuthenticationRequest mapToAuthenticationRequest(Map<String, Object> input) {
        PasskeyAuthenticationRequest request = new PasskeyAuthenticationRequest();
        request.setId((String) input.get("id"));
        request.setRawId((String) input.get("rawId"));
        request.setType((String) input.get("type"));
        request.setAuthenticatorAttachment((String) input.get("authenticatorAttachment"));

        Map<String, Object> responseMap = (Map<String, Object>) input.get("response");
        if (responseMap != null) {
            PasskeyAuthenticationRequest.AuthenticatorAssertionResponse response = new PasskeyAuthenticationRequest.AuthenticatorAssertionResponse();
            response.setClientDataJSON((String) responseMap.get("clientDataJSON"));
            response.setAuthenticatorData((String) responseMap.get("authenticatorData"));
            response.setSignature((String) responseMap.get("signature"));
            response.setUserHandle((String) responseMap.get("userHandle"));
            request.setResponse(response);
        }

        return request;
    }
}
