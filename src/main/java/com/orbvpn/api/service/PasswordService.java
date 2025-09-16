package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UnverifiedUser;
import com.orbvpn.api.service.common.AesUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Slf4j
@Service
public class PasswordService {

    private final RadiusService radiusService;

    // Method for setting the password for the User entity
    public void setPassword(User user, String password) {
        try {
            // Generate key and IV for encryption
            String aesKey = AesUtil.generateKey();
            String aesIv = AesUtil.convertIvParameterSpecToString(AesUtil.generateIv());

            // Encrypt the password
            String encryptedPassword = AesUtil.encrypt(password, aesKey, aesIv);

            // Set encrypted password, aesKey, and aesIv to the User entity
            user.setPassword(encryptedPassword);
            user.setAesKey(aesKey);
            user.setAesIv(aesIv);

            // Store additional details (e.g., radAccess)
            user.setRadAccess(DigestUtils.sha1Hex(password));

            // Save the updated password to the Radius service (if needed)
            radiusService.editUserPassword(user);
        } catch (Exception e) {
            log.error("Error encrypting password", e);
        }
    }

    // Method to decrypt the password for the User entity
    public String getPassword(User user) {
        try {
            // Fetch aesKey and aesIv from the User entity
            String aesKey = user.getAesKey();
            String aesIv = user.getAesIv();

            // Decrypt the password
            return AesUtil.decrypt(user.getPassword(), aesKey, aesIv);
        } catch (Exception e) {
            log.error("Error decrypting password", e);
            return null;
        }
    }

    // Method for setting the password for the UnverifiedUser entity
    public void setPassword(UnverifiedUser unverifiedUser, String password) {
        try {
            // Generate key and IV for encryption
            String aesKey = AesUtil.generateKey();
            String aesIv = AesUtil.convertIvParameterSpecToString(AesUtil.generateIv());

            // Encrypt the password
            String encryptedPassword = AesUtil.encrypt(password, aesKey, aesIv);

            // Set encrypted password, aesKey, and aesIv to the UnverifiedUser entity
            unverifiedUser.setPassword(encryptedPassword);
            unverifiedUser.setAesKey(aesKey);
            unverifiedUser.setAesIv(aesIv);
        } catch (Exception e) {
            log.error("Error encrypting password", e);
        }
    }

    // Method to decrypt the password for the UnverifiedUser entity
    public String getPassword(UnverifiedUser unverifiedUser) {
        try {
            // Fetch aesKey and aesIv from the UnverifiedUser entity
            String aesKey = unverifiedUser.getAesKey();
            String aesIv = unverifiedUser.getAesIv();

            // Decrypt the password
            return AesUtil.decrypt(unverifiedUser.getPassword(), aesKey, aesIv);
        } catch (Exception e) {
            log.error("Error decrypting password", e);
            return null;
        }
    }
}