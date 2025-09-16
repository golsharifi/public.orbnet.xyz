package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.common.AesUtil;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class EncryptionMutationResolver {

    private final UserService userService;

    @Secured(ADMIN)
    @MutationMapping
    public String reEncryptUserPasswordsByRange(
            @Argument @Valid @Min(value = 1, message = "From user ID must be positive") int fromUserId,
            @Argument @Valid @Min(value = 1, message = "To user ID must be positive") int toUserId,
            @Argument boolean sendEmail) {
        log.info("Re-encrypting passwords for users from ID {} to {}, sendEmail: {}", fromUserId, toUserId, sendEmail);
        try {
            userService.reEncryptPasswordsInRange(fromUserId, toUserId, sendEmail);
            log.info("Successfully re-encrypted passwords for user range");
            return "Passwords re-encrypted successfully for users in range [" + fromUserId + " to " + toUserId + "]";
        } catch (Exception e) {
            log.error("Error re-encrypting passwords - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public String encryptData(
            @Argument @Valid @Min(value = 1, message = "User ID must be positive") int userId,
            @Argument @Valid @NotBlank(message = "Data cannot be empty") String data) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        log.info("Encrypting data for user: {}", userId);
        try {
            User user = userService.getUserById(userId);
            return AesUtil.encrypt(data, user.getAesKey(), user.getAesIv());
        } catch (Exception e) {
            log.error("Error encrypting data for user: {} - Error: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @MutationMapping
    public String decryptData(
            @Argument @Valid @Min(value = 1, message = "User ID must be positive") int userId,
            @Argument @Valid @NotBlank(message = "Encrypted data cannot be empty") String encryptedData) throws InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeyException {
        log.info("Decrypting data for user: {}", userId);
        try {
            User user = userService.getUserById(userId);
            return AesUtil.decrypt(encryptedData, user.getAesKey(), user.getAesIv());
        } catch (Exception e) {
            log.error("Error decrypting data for user: {} - Error: {}", userId, e.getMessage(), e);
            throw e;
        }
    }
}