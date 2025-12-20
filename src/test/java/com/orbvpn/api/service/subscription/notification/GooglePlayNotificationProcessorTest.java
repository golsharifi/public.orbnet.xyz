package com.orbvpn.api.service.subscription.notification;

import com.orbvpn.api.domain.dto.GoogleNotification;
import com.orbvpn.api.domain.dto.GooglePlaySubscriptionInfo;
import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.ProcessedGoogleNotification;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.SubscriptionStatus;
import com.orbvpn.api.repository.ProcessedGoogleNotificationRepository;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.service.AsyncNotificationHelper;
import com.orbvpn.api.service.GroupService;
import com.orbvpn.api.service.RadiusService;
import com.orbvpn.api.service.subscription.GooglePlayService;
import com.orbvpn.api.service.subscription.utils.TransactionMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GooglePlayNotificationProcessorTest {

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;
    @Mock
    private ProcessedGoogleNotificationRepository processedNotificationRepository;
    @Mock
    private TransactionMappingService transactionMappingService;
    @Mock
    private AsyncNotificationHelper asyncNotificationHelper;
    @Mock
    private GroupService groupService;
    @Mock
    private RadiusService radiusService;
    @Mock
    private GooglePlayService googlePlayService;

    private GooglePlayNotificationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new GooglePlayNotificationProcessor(
                userSubscriptionRepository,
                processedNotificationRepository,
                transactionMappingService,
                asyncNotificationHelper,
                groupService,
                radiusService,
                googlePlayService
        );
    }

    @Test
    void processNotification_WithDuplicateMessageId_ThrowsIllegalStateException() {
        GoogleNotification notification = createValidNotification(4); // PURCHASED
        String messageId = "duplicate-message-id";

        when(processedNotificationRepository.existsByMessageId(messageId)).thenReturn(true);

        assertThrows(IllegalStateException.class, () ->
            processor.processNotification(notification, messageId)
        );

        // Should not save anything
        verify(processedNotificationRepository, never()).save(any());
    }

    @Test
    void processNotification_WithNullSubscriptionNotification_SkipsProcessing() {
        GoogleNotification notification = new GoogleNotification();
        // subscriptionNotification is null
        String messageId = "test-message-" + System.currentTimeMillis();

        when(processedNotificationRepository.existsByMessageId(messageId)).thenReturn(false);
        when(processedNotificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        processor.processNotification(notification, messageId);

        // Should save as SKIPPED
        verify(processedNotificationRepository).save(argThat(record ->
            record instanceof ProcessedGoogleNotification &&
            "SKIPPED".equals(((ProcessedGoogleNotification) record).getStatus())
        ));
    }

    @Test
    void processNotification_WithMissingPurchaseToken_ThrowsIllegalArgumentException() {
        GoogleNotification notification = new GoogleNotification();
        GoogleNotification.SubscriptionNotification subNotification = new GoogleNotification.SubscriptionNotification();
        subNotification.setSubscriptionId("sub-123");
        subNotification.setNotificationType(4);
        // purchaseToken is null
        notification.setSubscriptionNotification(subNotification);

        String messageId = "test-message-" + System.currentTimeMillis();

        when(processedNotificationRepository.existsByMessageId(messageId)).thenReturn(false);
        when(processedNotificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertThrows(IllegalArgumentException.class, () ->
            processor.processNotification(notification, messageId)
        );
    }

    @Test
    void processNotification_WithValidRenewal_ProcessesSuccessfully() {
        GoogleNotification notification = createValidNotification(2); // RENEWED
        String messageId = "test-renewal-" + System.currentTimeMillis();

        when(processedNotificationRepository.existsByMessageId(messageId)).thenReturn(false);
        when(processedNotificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserSubscription existingSubscription = createMockSubscription();
        when(userSubscriptionRepository.findByPurchaseTokenWithLock("test-purchase-token"))
                .thenReturn(Optional.of(existingSubscription));
        when(userSubscriptionRepository.save(any())).thenReturn(existingSubscription);

        GooglePlaySubscriptionInfo info = new GooglePlaySubscriptionInfo();
        info.setExpiresAt(LocalDateTime.now().plusDays(30));
        when(googlePlayService.verifyTokenWithGooglePlay(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(info);

        processor.processNotification(notification, messageId);

        // Verify subscription was updated
        verify(userSubscriptionRepository).save(any(UserSubscription.class));
        verify(radiusService).updateUserExpirationRadCheck(any());
        verify(processedNotificationRepository, atLeastOnce()).save(argThat(record ->
            "SUCCESS".equals(((ProcessedGoogleNotification) record).getStatus())
        ));
    }

    @Test
    void processNotification_WithCancellation_UpdatesSubscription() {
        GoogleNotification notification = createValidNotification(3); // CANCELED
        String messageId = "test-cancel-" + System.currentTimeMillis();

        when(processedNotificationRepository.existsByMessageId(messageId)).thenReturn(false);
        when(processedNotificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByPurchaseTokenWithLock("test-purchase-token"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        processor.processNotification(notification, messageId);

        verify(userSubscriptionRepository).save(argThat(sub ->
            sub.getCanceled() == true && sub.getAutoRenew() == false
        ));
    }

    @Test
    void processNotification_WithExpired_SetsCorrectStatus() {
        GoogleNotification notification = createValidNotification(13); // EXPIRED
        String messageId = "test-expired-" + System.currentTimeMillis();

        when(processedNotificationRepository.existsByMessageId(messageId)).thenReturn(false);
        when(processedNotificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByPurchaseTokenWithLock("test-purchase-token"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        processor.processNotification(notification, messageId);

        verify(userSubscriptionRepository).save(argThat(sub ->
            sub.getStatus() == SubscriptionStatus.EXPIRED
        ));
    }

    @Test
    void processNotification_WithRevoked_SetsCorrectStatus() {
        GoogleNotification notification = createValidNotification(12); // REVOKED
        String messageId = "test-revoked-" + System.currentTimeMillis();

        when(processedNotificationRepository.existsByMessageId(messageId)).thenReturn(false);
        when(processedNotificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByPurchaseTokenWithLock("test-purchase-token"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        processor.processNotification(notification, messageId);

        verify(userSubscriptionRepository).save(argThat(sub ->
            sub.getStatus() == SubscriptionStatus.REVOKED &&
            sub.getCanceled() == true &&
            sub.getAutoRenew() == false
        ));
    }

    @Test
    void processNotification_WithOnHold_SetsCorrectStatus() {
        GoogleNotification notification = createValidNotification(5); // ON_HOLD
        String messageId = "test-onhold-" + System.currentTimeMillis();

        when(processedNotificationRepository.existsByMessageId(messageId)).thenReturn(false);
        when(processedNotificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByPurchaseTokenWithLock("test-purchase-token"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        processor.processNotification(notification, messageId);

        verify(userSubscriptionRepository).save(argThat(sub ->
            sub.getStatus() == SubscriptionStatus.ON_HOLD
        ));
    }

    @Test
    void processNotification_WithGracePeriod_SetsCorrectStatus() {
        GoogleNotification notification = createValidNotification(6); // IN_GRACE_PERIOD
        String messageId = "test-grace-" + System.currentTimeMillis();

        when(processedNotificationRepository.existsByMessageId(messageId)).thenReturn(false);
        when(processedNotificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByPurchaseTokenWithLock("test-purchase-token"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        processor.processNotification(notification, messageId);

        verify(userSubscriptionRepository).save(argThat(sub ->
            sub.getStatus() == SubscriptionStatus.GRACE_PERIOD
        ));
    }

    @Test
    void processNotification_WithPaused_SetsCorrectStatus() {
        GoogleNotification notification = createValidNotification(10); // PAUSED
        String messageId = "test-paused-" + System.currentTimeMillis();

        when(processedNotificationRepository.existsByMessageId(messageId)).thenReturn(false);
        when(processedNotificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByPurchaseTokenWithLock("test-purchase-token"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        processor.processNotification(notification, messageId);

        verify(userSubscriptionRepository).save(argThat(sub ->
            sub.getStatus() == SubscriptionStatus.PAUSED
        ));
    }

    @Test
    void processNotification_WithPriceChangeConfirmed_ProcessesSuccessfully() {
        GoogleNotification notification = createValidNotification(8); // PRICE_CHANGE_CONFIRMED
        String messageId = "test-price-change-" + System.currentTimeMillis();

        when(processedNotificationRepository.existsByMessageId(messageId)).thenReturn(false);
        when(processedNotificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByPurchaseTokenWithLock("test-purchase-token"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        processor.processNotification(notification, messageId);

        // Should trigger webhook
        verify(asyncNotificationHelper).sendSubscriptionWebhookAsync(any(UserSubscription.class), eq("SUBSCRIPTION_PRICE_CHANGE_CONFIRMED"));
        verify(processedNotificationRepository, atLeastOnce()).save(argThat(record ->
            "SUCCESS".equals(((ProcessedGoogleNotification) record).getStatus())
        ));
    }

    @Test
    void processNotification_WithDeferred_UpdatesExpiration() {
        GoogleNotification notification = createValidNotification(9); // DEFERRED
        String messageId = "test-deferred-" + System.currentTimeMillis();

        when(processedNotificationRepository.existsByMessageId(messageId)).thenReturn(false);
        when(processedNotificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByPurchaseTokenWithLock("test-purchase-token"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        LocalDateTime newExpiry = LocalDateTime.now().plusDays(60);
        GooglePlaySubscriptionInfo info = new GooglePlaySubscriptionInfo();
        info.setExpiresAt(newExpiry);
        when(googlePlayService.verifyTokenWithGooglePlay(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(info);

        processor.processNotification(notification, messageId);

        verify(asyncNotificationHelper).sendSubscriptionWebhookAsync(any(UserSubscription.class), eq("SUBSCRIPTION_DEFERRED"));
    }

    @Test
    void processNotification_WithNoExistingSubscription_AndNoUser_ReturnsEarly() {
        GoogleNotification notification = createValidNotification(4); // PURCHASED
        String messageId = "test-no-user-" + System.currentTimeMillis();

        when(processedNotificationRepository.existsByMessageId(messageId)).thenReturn(false);
        when(processedNotificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userSubscriptionRepository.findByPurchaseTokenWithLock("test-purchase-token"))
                .thenReturn(Optional.empty());
        when(transactionMappingService.findUserByToken(anyString(), eq(GatewayName.GOOGLE_PLAY)))
                .thenReturn(null);

        processor.processNotification(notification, messageId);

        // Should save as failed since no user found
        verify(processedNotificationRepository, atLeastOnce()).save(argThat(record ->
            "FAILED".equals(((ProcessedGoogleNotification) record).getStatus())
        ));
    }

    @Test
    void processNotification_IdempotencyCheck_UsesMessageId() {
        String messageId = "unique-message-id-123";
        GoogleNotification notification = createValidNotification(4);

        when(processedNotificationRepository.existsByMessageId(messageId)).thenReturn(false);
        when(processedNotificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userSubscriptionRepository.findByPurchaseTokenWithLock(anyString()))
                .thenReturn(Optional.empty());
        when(transactionMappingService.findUserByToken(anyString(), any())).thenReturn(null);

        processor.processNotification(notification, messageId);

        // Verify idempotency check was called with correct messageId
        verify(processedNotificationRepository).existsByMessageId(messageId);
    }

    @Test
    void processNotification_UsesLockingQuery() {
        GoogleNotification notification = createValidNotification(2); // RENEWED
        String messageId = "test-locking-" + System.currentTimeMillis();

        when(processedNotificationRepository.existsByMessageId(messageId)).thenReturn(false);
        when(processedNotificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByPurchaseTokenWithLock("test-purchase-token"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        GooglePlaySubscriptionInfo info = new GooglePlaySubscriptionInfo();
        info.setExpiresAt(LocalDateTime.now().plusDays(30));
        when(googlePlayService.verifyTokenWithGooglePlay(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(info);

        processor.processNotification(notification, messageId);

        // Verify the locking query was used, not the regular one
        verify(userSubscriptionRepository).findByPurchaseTokenWithLock("test-purchase-token");
        verify(userSubscriptionRepository, never()).findByPurchaseToken(anyString());
    }

    private GoogleNotification createValidNotification(int notificationType) {
        GoogleNotification notification = new GoogleNotification();
        GoogleNotification.SubscriptionNotification subNotification = new GoogleNotification.SubscriptionNotification();
        subNotification.setPurchaseToken("test-purchase-token");
        subNotification.setSubscriptionId("monthly_subscription");
        subNotification.setNotificationType(notificationType);
        notification.setSubscriptionNotification(subNotification);
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
        subscription.setGateway(GatewayName.GOOGLE_PLAY);
        subscription.setPurchaseToken("test-purchase-token");
        subscription.setSubscriptionId("monthly_subscription");
        subscription.setExpiresAt(LocalDateTime.now().plusDays(30));
        subscription.setAutoRenew(true);
        subscription.setCanceled(false);
        subscription.setDuration(30);

        return subscription;
    }
}
