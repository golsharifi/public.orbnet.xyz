package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserDevice;
import com.orbvpn.api.exception.AccessDeniedException;
import com.orbvpn.api.exception.NotFoundException;
import com.orbvpn.api.exception.UnauthenticatedAccessException;
import com.orbvpn.api.mapper.UserDeviceMapper;
import com.orbvpn.api.repository.UserDeviceRepository;
import com.orbvpn.api.repository.UserRepository;
import com.orbvpn.api.service.notification.FCMService;
import com.orbvpn.api.service.subscription.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.Collections;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class UserDeviceService {

    private final UserDeviceRepository userDeviceRepository;
    private final UserRepository userRepository;
    private final AccessService accessService; // Use AccessService
    private final FCMService fcmService;
    private final UserDeviceMapper userDeviceMapper;
    private final UserSubscriptionService userSubscriptionService;

    private final Lock lock = new ReentrantLock(true);

    @Transactional
    public void deleteUserDevices(User user) {
        log.info("Deleting all devices for user {}", user.getId());

        // Retrieve the list of devices associated with the user
        List<UserDevice> userDevices = userDeviceRepository.getUserDeviceByUser(user);

        // Logout each device and send FCM notification
        for (UserDevice userDevice : userDevices) {
            logoutDevice(userDevice);
        }
        userDeviceRepository.deleteByUser(user);
    }

    public UserDeviceView loginDevice(UserDeviceDto userDeviceDto) {
        if (userDeviceDto == null || userDeviceDto.getDeviceId() == null) {
            throw new IllegalArgumentException("UserDeviceDto or deviceId cannot be null");
        }
        // Retrieve the current user from the security context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();

        // Retrieve the user's subscription directly using injected service
        UserSubscriptionView subscription = userSubscriptionService.getUserSubscription(user);

        lock.lock();
        try {
            List<UserDevice> userDevices = userDeviceRepository.getUserDeviceByUser(user);
            List<UserDevice> activeDevices = userDevices.stream()
                    .filter(UserDevice::getIsActive)
                    .collect(Collectors.toList());

            UserDevice currentDevice = userDevices.stream()
                    .filter(device -> device.getDeviceId().equals(userDeviceDto.getDeviceId()))
                    .findAny()
                    .orElse(null);

            if (currentDevice == null) {
                if (subscription.getMultiLoginCount() <= activeDevices.size()) {
                    throw new UnauthenticatedAccessException("You've reached the number of devices you can login to!");
                } else {
                    currentDevice = userDeviceMapper.toUserDevice(userDeviceDto);
                    currentDevice.setUser(user);
                }
            } else {
                if (Boolean.TRUE.equals(currentDevice.getIsBlocked())) {
                    throw new AccessDeniedException("This device is blocked!");
                }

                int activeDeviceCount = currentDevice.getIsActive() ? activeDevices.size() - 1 : activeDevices.size();
                if (subscription.getMultiLoginCount() <= activeDeviceCount) {
                    throw new UnauthenticatedAccessException("You've reached the number of devices you can login to!");
                }

                currentDevice.setAppVersion(userDeviceDto.getAppVersion());
                currentDevice.setDeviceName(userDeviceDto.getDeviceName());
                currentDevice.setDeviceModel(userDeviceDto.getDeviceModel());
                currentDevice.setOs(userDeviceDto.getOs()); // Assuming this was intended
            }

            currentDevice.setFcmToken(userDeviceDto.getFcmToken());
            currentDevice.setLoginDate(LocalDateTime.now());
            currentDevice.setLogoutDate(null);
            currentDevice.setIsActive(true);
            currentDevice = userDeviceRepository.save(currentDevice);
            return userDeviceMapper.toUserDeviceView(currentDevice);
        } finally {
            lock.unlock();
        }
    }

    public UserDeviceView logoutDevice(Long userDeviceId) {
        UserDevice userDevice = userDeviceRepository.getUserDeviceById(userDeviceId)
                .orElseThrow(() -> new NotFoundException(UserDevice.class, userDeviceId));
        return logoutDevice(userDevice);
    }

    public UserDeviceView logoutDevice(String deviceId) {
        UserDevice userDevice = userDeviceRepository.findFirstByDeviceId(deviceId)
                .orElseThrow(() -> new NotFoundException(UserDevice.class, deviceId));
        return logoutDevice(userDevice);
    }

    public UserDeviceView logoutDevice(UserDevice userDevice) {
        userDevice.setLogoutDate(LocalDateTime.now());
        userDevice.setIsActive(false);
        userDevice = userDeviceRepository.save(userDevice);

        fcmService.sendLogoutNotification(userDevice.getFcmToken());
        return userDeviceMapper.toUserDeviceView(userDevice);
    }

    public UserDeviceView resellerLogoutDevice(int userId, String deviceId) {
        // Retrieve the user directly using UserRepository
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(User.class, userId));

        accessService.checkResellerUserAccess(user); // Use AccessService
        return logoutDevice(deviceId);
    }

    public List<UserDeviceView> getActiveDevices() {
        // Retrieve the current user from the security context
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) authentication.getPrincipal();

        return userDeviceRepository.getUserDeviceByUser(user).stream()
                .filter(UserDevice::getIsActive)
                .map(userDeviceMapper::toUserDeviceView)
                .collect(Collectors.toList());
    }

    public List<UserDeviceView> resellerGetActiveDevices(int userId) {
        // Retrieve the user directly using UserRepository
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(User.class, userId));

        accessService.checkResellerUserAccess(user); // Use AccessService
        return userDeviceRepository.getUserDeviceByUser(user).stream()
                .filter(UserDevice::getIsActive)
                .map(userDeviceMapper::toUserDeviceView)
                .collect(Collectors.toList());
    }

    public UserDeviceView blockDevice(String deviceId) {
        UserDevice userDevice = userDeviceRepository.findFirstByDeviceId(deviceId)
                .orElseThrow(() -> new NotFoundException(UserDevice.class, deviceId));

        accessService.checkResellerUserAccess(userDevice.getUser()); // Use AccessService
        userDevice.setIsBlocked(true);
        return logoutDevice(userDevice);
    }

    public UserDeviceView unblockDevice(String deviceId) {
        UserDevice userDevice = userDeviceRepository.findFirstByDeviceId(deviceId)
                .orElseThrow(() -> new NotFoundException(UserDevice.class, deviceId));

        accessService.checkResellerUserAccess(userDevice.getUser()); // Use AccessService
        userDevice.setIsBlocked(false);
        userDevice = userDeviceRepository.save(userDevice);
        return userDeviceMapper.toUserDeviceView(userDevice);
    }

    public FcmNotificationDto sendNotificationByDeviceId(String deviceId, NotificationDto notificationDto) {
        log.info("Sending notification to device: {}", deviceId);
        try {
            UserDevice userDevice = userDeviceRepository.findFirstByDeviceId(deviceId)
                    .orElseThrow(() -> new NotFoundException(UserDevice.class, deviceId));

            String fcmToken = userDevice.getFcmToken();
            if (fcmToken == null || fcmToken.trim().isEmpty()) {
                log.warn("FCM token not found for device: {}", deviceId);
                return new FcmNotificationDto("FCM token not found for device");
            }

            // Create notification if it's null
            if (notificationDto == null) {
                log.warn("NotificationDto is null, creating empty notification for device: {}", deviceId);
                notificationDto = NotificationDto.builder()
                        .subject("")
                        .content("")
                        .build();
            }

            log.debug("Sending notification with subject: '{}' to token: {}",
                    notificationDto.getSubject(), fcmToken);

            return fcmService.sendNotification(notificationDto, fcmToken);
        } catch (Exception e) {
            log.error("Error sending notification to device: {}", deviceId, e);
            return new FcmNotificationDto(e.getMessage());
        }
    }

    public FcmNotificationDto adminSendNotificationByToken(String fcmToken, NotificationDto notificationDto) {
        log.info("Admin sending notification to token");
        try {
            if (fcmToken == null || fcmToken.trim().isEmpty()) {
                log.warn("Invalid FCM token provided");
                return new FcmNotificationDto("Invalid FCM token");
            }

            if (notificationDto == null) {
                log.warn("NotificationDto is null, creating empty notification");
                notificationDto = NotificationDto.builder()
                        .subject("")
                        .content("")
                        .build();
            }

            return fcmService.sendNotification(notificationDto, fcmToken);
        } catch (Exception e) {
            log.error("Error sending admin notification with token - Error: {}", e.getMessage(), e);
            return new FcmNotificationDto(e.getMessage());
        }
    }

    public List<UserDeviceView> getAllActiveDevices() {
        try {
            List<UserDeviceView> devices = userDeviceRepository.findAll().stream()
                    .filter(userDevice -> !userDevice.getIsBlocked())
                    .filter(userDevice -> userDevice.getFcmToken() != null
                            && !userDevice.getFcmToken().trim().isEmpty())
                    .map(userDeviceMapper::toUserDeviceView)
                    .collect(Collectors.toList());

            log.debug("Found {} active devices", devices.size());
            return devices;
        } catch (Exception e) {
            log.error("Error retrieving active devices: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public FcmNotificationDto adminSendNotificationToAll(NotificationDto notificationDto) {
        log.info("Sending notification to all devices");
        try {
            if (notificationDto == null) {
                log.warn("NotificationDto is null");
                return new FcmNotificationDto("Notification data cannot be null");
            }

            List<String> tokens = getAllActiveDevices().stream()
                    .map(UserDeviceView::getFcmToken)
                    .filter(token -> token != null && !token.trim().isEmpty())
                    .collect(Collectors.toList());

            if (tokens.isEmpty()) {
                log.warn("No valid FCM tokens found");
                return new FcmNotificationDto("No valid devices found to send notification");
            }

            log.info("Sending bulk notification to {} devices", tokens.size());
            return fcmService.sendBulkNotification(notificationDto, tokens);
        } catch (Exception e) {
            log.error("Error sending bulk notification - Error: {}", e.getMessage(), e);
            return new FcmNotificationDto(e.getMessage());
        }
    }

    public List<UserDevice> getActiveDevicesByUserId(int userId) {
        // Fetch the user entity by userId using UserRepository
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(User.class, userId));

        // Fetch all active devices associated with the user
        return userDeviceRepository.getUserDeviceByUser(user).stream()
                .filter(UserDevice::getIsActive)
                .collect(Collectors.toList());
    }

    public void logoutAllDevices(User user) {
        List<UserDevice> userDevices = userDeviceRepository.getUserDeviceByUser(user);
        for (UserDevice userDevice : userDevices) {
            logoutDevice(userDevice);
        }
    }
}