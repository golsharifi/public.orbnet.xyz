package com.orbvpn.api.service.subscription.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbvpn.api.domain.dto.AppleNotification;
import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.ProcessedAppleNotification;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.SubscriptionStatus;
import com.orbvpn.api.repository.ProcessedAppleNotificationRepository;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.service.AsyncNotificationHelper;
import com.orbvpn.api.service.GroupService;
import com.orbvpn.api.service.RadiusService;
import com.orbvpn.api.service.subscription.AppleJwtVerificationService;
import com.orbvpn.api.service.subscription.ProductGroupMapper;
import com.orbvpn.api.service.subscription.utils.TransactionMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppleNotificationProcessorTest {

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;
    @Mock
    private ProcessedAppleNotificationRepository processedNotificationRepository;
    @Mock
    private TransactionMappingService transactionMappingService;
    @Mock
    private AsyncNotificationHelper asyncNotificationHelper;
    @Mock
    private GroupService groupService;
    @Mock
    private RadiusService radiusService;
    @Mock
    private ProductGroupMapper productGroupMapper;
    @Mock
    private AppleJwtVerificationService appleJwtVerificationService;

    private ObjectMapper objectMapper;
    private AppleNotificationProcessor processor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        processor = new AppleNotificationProcessor(
                userSubscriptionRepository,
                processedNotificationRepository,
                transactionMappingService,
                asyncNotificationHelper,
                groupService,
                radiusService,
                objectMapper,
                productGroupMapper,
                appleJwtVerificationService
        );
    }

    @Test
    void processNotification_WithNullData_ReturnsEarly() {
        AppleNotification notification = new AppleNotification();
        notification.setNotificationUUID("test-uuid");
        notification.setNotificationType("DID_RENEW");
        // data is null

        when(processedNotificationRepository.existsByNotificationUUID("test-uuid")).thenReturn(false);
        when(processedNotificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        processor.processNotification(notification);

        // Should save a failed record
        verify(processedNotificationRepository, times(2)).save(any(ProcessedAppleNotification.class));
    }

    @Test
    void processNotification_WithAlreadyProcessedUUID_SkipsProcessing() {
        AppleNotification notification = new AppleNotification();
        notification.setNotificationUUID("already-processed-uuid");
        notification.setNotificationType("DID_RENEW");

        when(processedNotificationRepository.existsByNotificationUUID("already-processed-uuid"))
                .thenReturn(true);

        processor.processNotification(notification);

        // Should not save anything new - just skip
        verify(processedNotificationRepository, never()).save(any());
        verify(userSubscriptionRepository, never()).findByOriginalTransactionIdWithLock(any());
    }

    @Test
    void processNotification_WithValidRenewal_ProcessesSuccessfully() {
        // Setup notification
        AppleNotification notification = createValidNotification("DID_RENEW");

        // Setup mocks
        when(processedNotificationRepository.existsByNotificationUUID(anyString())).thenReturn(false);
        when(processedNotificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        String transactionJson = "{\"originalTransactionId\":\"orig-123\",\"productId\":\"monthly\",\"expiresDate\":1735689600000}";
        when(appleJwtVerificationService.verifyAndDecodeSignedData(anyString())).thenReturn(transactionJson);

        UserSubscription existingSubscription = createMockSubscription();
        when(userSubscriptionRepository.findByOriginalTransactionIdWithLock("orig-123"))
                .thenReturn(Optional.of(existingSubscription));
        when(userSubscriptionRepository.save(any())).thenReturn(existingSubscription);

        // Execute
        processor.processNotification(notification);

        // Verify subscription was updated
        verify(userSubscriptionRepository).save(any(UserSubscription.class));
        verify(radiusService).updateUserExpirationRadCheck(any());
    }

    @Test
    void processNotification_WithMissingOriginalTransactionId_MarksAsFailed() {
        AppleNotification notification = createValidNotification("DID_RENEW");

        when(processedNotificationRepository.existsByNotificationUUID(anyString())).thenReturn(false);
        when(processedNotificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Return JSON without originalTransactionId
        String transactionJson = "{\"productId\":\"monthly\"}";
        when(appleJwtVerificationService.verifyAndDecodeSignedData(anyString())).thenReturn(transactionJson);

        processor.processNotification(notification);

        // Should mark as failed
        verify(processedNotificationRepository, atLeast(1)).save(argThat(record ->
            record instanceof ProcessedAppleNotification &&
            "FAILED".equals(((ProcessedAppleNotification) record).getStatus())
        ));
    }

    @Test
    void processNotification_WithExpiredNotification_HandlesCorrectly() {
        AppleNotification notification = createValidNotification("EXPIRED");

        when(processedNotificationRepository.existsByNotificationUUID(anyString())).thenReturn(false);
        when(processedNotificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        String transactionJson = "{\"originalTransactionId\":\"orig-123\",\"productId\":\"monthly\"}";
        when(appleJwtVerificationService.verifyAndDecodeSignedData(anyString())).thenReturn(transactionJson);

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByOriginalTransactionIdWithLock("orig-123"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        processor.processNotification(notification);

        // Verify status was set to EXPIRED
        verify(userSubscriptionRepository).save(argThat(sub ->
            sub.getStatus() == SubscriptionStatus.EXPIRED
        ));
    }

    @Test
    void processNotification_WithRefund_SetsCorrectStatus() {
        AppleNotification notification = createValidNotification("REFUND");

        when(processedNotificationRepository.existsByNotificationUUID(anyString())).thenReturn(false);
        when(processedNotificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        String transactionJson = "{\"originalTransactionId\":\"orig-123\",\"productId\":\"monthly\"}";
        when(appleJwtVerificationService.verifyAndDecodeSignedData(anyString())).thenReturn(transactionJson);

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByOriginalTransactionIdWithLock("orig-123"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        processor.processNotification(notification);

        verify(userSubscriptionRepository).save(argThat(sub ->
            sub.getStatus() == SubscriptionStatus.REFUNDED &&
            sub.getCanceled() == true &&
            sub.getAutoRenew() == false
        ));
    }

    @Test
    void processNotification_WithCancellation_UpdatesSubscription() {
        AppleNotification notification = createValidNotification("CANCEL");

        when(processedNotificationRepository.existsByNotificationUUID(anyString())).thenReturn(false);
        when(processedNotificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        String transactionJson = "{\"originalTransactionId\":\"orig-123\",\"productId\":\"monthly\"}";
        when(appleJwtVerificationService.verifyAndDecodeSignedData(anyString())).thenReturn(transactionJson);

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByOriginalTransactionIdWithLock("orig-123"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        processor.processNotification(notification);

        verify(userSubscriptionRepository).save(argThat(sub ->
            sub.getCanceled() == true && sub.getAutoRenew() == false
        ));
    }

    private AppleNotification createValidNotification(String type) {
        AppleNotification notification = new AppleNotification();
        notification.setNotificationUUID("test-uuid-" + System.currentTimeMillis());
        notification.setNotificationType(type);
        notification.setSignedDate(System.currentTimeMillis());

        AppleNotification.Data data = new AppleNotification.Data();
        data.setBundleId("com.orbvpn.app");
        data.setEnvironment("Production");
        data.setSignedTransactionInfo("fake.jwt.token");
        data.setSignedRenewalInfo("fake.renewal.jwt");
        notification.setData(data);

        return notification;
    }

    private UserSubscription createMockSubscription() {
        User user = new User();
        user.setId(1);

        Group group = new Group();
        group.setId(1);
        group.setDuration(30);

        UserSubscription subscription = new UserSubscription();
        subscription.setId(1);
        subscription.setUser(user);
        subscription.setGroup(group);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setGateway(GatewayName.APPLE_STORE);
        subscription.setExpiresAt(LocalDateTime.now().plusDays(30));
        subscription.setAutoRenew(true);
        subscription.setCanceled(false);

        return subscription;
    }
}
