package com.orbvpn.api.service.subscription.notification;

import com.orbvpn.api.domain.dto.StripeWebhookEvent;
import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.domain.enums.GatewayName;
import com.orbvpn.api.domain.enums.SubscriptionStatus;
import com.orbvpn.api.repository.UserSubscriptionRepository;
import com.orbvpn.api.service.AsyncNotificationHelper;
import com.orbvpn.api.service.RadiusService;
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
class StripeNotificationProcessorTest {

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;
    @Mock
    private RadiusService radiusService;
    @Mock
    private AsyncNotificationHelper asyncNotificationHelper;

    private StripeNotificationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new StripeNotificationProcessor(
                userSubscriptionRepository,
                radiusService,
                asyncNotificationHelper
        );
    }

    @Test
    void processNotification_SubscriptionCreated_ProcessesSuccessfully() {
        StripeWebhookEvent event = createEvent("customer.subscription.created");
        event.setSubscriptionId("sub_test123");

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByStripeSubscriptionIdWithLock("sub_test123"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        processor.processNotification(event);

        verify(userSubscriptionRepository).save(any(UserSubscription.class));
        verify(radiusService).updateUserExpirationRadCheck(any());
        verify(asyncNotificationHelper).sendSubscriptionWebhookAsync(any(UserSubscription.class), eq("SUBSCRIPTION_CREATED"));
    }

    @Test
    void processNotification_SubscriptionCreated_WithTrial_SetsTrialFields() {
        StripeWebhookEvent event = createEvent("customer.subscription.created");
        event.setSubscriptionId("sub_test123");
        event.setIsTrialPeriod(true);
        event.setTrialEnd(LocalDateTime.now().plusDays(14));

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByStripeSubscriptionIdWithLock("sub_test123"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        processor.processNotification(event);

        verify(userSubscriptionRepository).save(argThat(sub ->
            Boolean.TRUE.equals(sub.getIsTrialPeriod()) && sub.getTrialEndDate() != null
        ));
    }

    @Test
    void processNotification_SubscriptionCreated_NoSubscription_LogsWarning() {
        StripeWebhookEvent event = createEvent("customer.subscription.created");
        event.setSubscriptionId("sub_nonexistent");

        when(userSubscriptionRepository.findByStripeSubscriptionIdWithLock("sub_nonexistent"))
                .thenReturn(Optional.empty());

        // Should not throw, just log warning
        processor.processNotification(event);

        verify(userSubscriptionRepository, never()).save(any());
    }

    @Test
    void processNotification_SubscriptionUpdated_Active_SetsActiveStatus() {
        StripeWebhookEvent event = createEvent("customer.subscription.updated");
        event.setSubscriptionId("sub_test123");
        event.setStatus("active");
        event.setCancelAtPeriodEnd(false);
        event.setExpiresAt(LocalDateTime.now().plusDays(30));

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByStripeSubscriptionIdWithLock("sub_test123"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        processor.processNotification(event);

        verify(userSubscriptionRepository).save(argThat(sub ->
            sub.getStatus() == SubscriptionStatus.ACTIVE &&
            !sub.getCanceled() &&
            sub.getAutoRenew()
        ));
    }

    @Test
    void processNotification_SubscriptionUpdated_CancelAtPeriodEnd_SetsSoftCancel() {
        StripeWebhookEvent event = createEvent("customer.subscription.updated");
        event.setSubscriptionId("sub_test123");
        event.setCancelAtPeriodEnd(true);

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByStripeSubscriptionIdWithLock("sub_test123"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        processor.processNotification(event);

        verify(userSubscriptionRepository).save(argThat(sub ->
            !sub.getAutoRenew()
            // Note: canceled should NOT be true yet - service continues until period end
        ));
    }

    @Test
    void processNotification_SubscriptionUpdated_ImmediateCancel_SetsExpired() {
        StripeWebhookEvent event = createEvent("customer.subscription.updated");
        event.setSubscriptionId("sub_test123");
        event.setStatus("canceled");

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByStripeSubscriptionIdWithLock("sub_test123"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        processor.processNotification(event);

        verify(userSubscriptionRepository).save(argThat(sub ->
            sub.getCanceled() &&
            !sub.getAutoRenew() &&
            sub.getStatus() == SubscriptionStatus.EXPIRED
        ));
    }

    @Test
    void processNotification_SubscriptionUpdated_PastDue_SetsPaymentFailed() {
        StripeWebhookEvent event = createEvent("customer.subscription.updated");
        event.setSubscriptionId("sub_test123");
        event.setStatus("past_due");

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByStripeSubscriptionIdWithLock("sub_test123"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        processor.processNotification(event);

        verify(userSubscriptionRepository).save(argThat(sub ->
            sub.getStatus() == SubscriptionStatus.PAYMENT_FAILED
        ));
    }

    @Test
    void processNotification_SubscriptionUpdated_Trialing_SetsTrialFields() {
        StripeWebhookEvent event = createEvent("customer.subscription.updated");
        event.setSubscriptionId("sub_test123");
        event.setStatus("trialing");
        event.setTrialEnd(LocalDateTime.now().plusDays(7));

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByStripeSubscriptionIdWithLock("sub_test123"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        processor.processNotification(event);

        verify(userSubscriptionRepository).save(argThat(sub ->
            sub.getStatus() == SubscriptionStatus.ACTIVE &&
            Boolean.TRUE.equals(sub.getIsTrialPeriod()) &&
            sub.getTrialEndDate() != null
        ));
    }

    @Test
    void processNotification_SubscriptionDeleted_SetsExpiredAndCanceled() {
        StripeWebhookEvent event = createEvent("customer.subscription.deleted");
        event.setSubscriptionId("sub_test123");

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByStripeSubscriptionIdWithLock("sub_test123"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        processor.processNotification(event);

        verify(userSubscriptionRepository).save(argThat(sub ->
            sub.getCanceled() &&
            !sub.getAutoRenew() &&
            sub.getStatus() == SubscriptionStatus.EXPIRED
        ));
        verify(asyncNotificationHelper).sendSubscriptionWebhookAsync(any(UserSubscription.class), eq("SUBSCRIPTION_CANCELLED"));
    }

    @Test
    void processNotification_TrialWillEnd_SendsNotification() {
        StripeWebhookEvent event = createEvent("customer.subscription.trial_will_end");
        event.setSubscriptionId("sub_test123");
        event.setTrialEnd(LocalDateTime.now().plusDays(3));

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByStripeSubscriptionIdWithLock("sub_test123"))
                .thenReturn(Optional.of(subscription));

        processor.processNotification(event);

        verify(asyncNotificationHelper).sendTrialEndingNotificationAsync(any(User.class), any(UserSubscription.class), any());
        verify(asyncNotificationHelper).sendSubscriptionWebhookAsync(any(UserSubscription.class), eq("SUBSCRIPTION_TRIAL_WILL_END"));
    }

    @Test
    void processNotification_InvoicePaymentSucceeded_UpdatesSubscription() {
        StripeWebhookEvent event = createEvent("invoice.payment_succeeded");
        event.setSubscriptionId("sub_test123");
        event.setExpiresAt(LocalDateTime.now().plusDays(30));

        UserSubscription subscription = createMockSubscription();
        subscription.setIsTrialPeriod(true);
        when(userSubscriptionRepository.findByStripeSubscriptionIdWithLock("sub_test123"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        processor.processNotification(event);

        verify(userSubscriptionRepository).save(argThat(sub ->
            sub.getStatus() == SubscriptionStatus.ACTIVE &&
            !sub.getCanceled() &&
            !Boolean.TRUE.equals(sub.getIsTrialPeriod()) // Trial should end
        ));
        verify(asyncNotificationHelper).sendSubscriptionWebhookAsync(any(UserSubscription.class), eq("PAYMENT_SUCCEEDED"));
    }

    @Test
    void processNotification_InvoicePaymentSucceeded_NoSubscriptionId_ReturnsEarly() {
        StripeWebhookEvent event = createEvent("invoice.payment_succeeded");
        // subscriptionId is null

        processor.processNotification(event);

        verify(userSubscriptionRepository, never()).findByStripeSubscriptionIdWithLock(anyString());
    }

    @Test
    void processNotification_InvoicePaymentFailed_SendsNotificationAndSetsStatus() {
        StripeWebhookEvent event = createEvent("invoice.payment_failed");
        event.setSubscriptionId("sub_test123");

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByStripeSubscriptionIdWithLock("sub_test123"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        processor.processNotification(event);

        verify(userSubscriptionRepository).save(argThat(sub ->
            sub.getStatus() == SubscriptionStatus.PAYMENT_FAILED
        ));
        verify(asyncNotificationHelper).sendPaymentFailedNotificationAsync(any(User.class), any(UserSubscription.class));
        verify(asyncNotificationHelper).sendSubscriptionWebhookAsync(any(UserSubscription.class), eq("PAYMENT_FAILED"));
    }

    @Test
    void processNotification_ChargeRefunded_LogsRefund() {
        StripeWebhookEvent event = createEvent("charge.refunded");
        event.setPaymentIntentId("pi_test123");
        event.setAmount(1000L);

        processor.processNotification(event);

        verify(asyncNotificationHelper).sendWebhookAsync(eq("CHARGE_REFUNDED"), any());
    }

    @Test
    void processNotification_DisputeCreated_LogsDispute() {
        StripeWebhookEvent event = createEvent("charge.dispute.created");
        event.setAmount(1000L);
        event.setStatus("warning_needs_response");

        processor.processNotification(event);

        verify(asyncNotificationHelper).sendWebhookAsync(eq("DISPUTE_CREATED"), any());
    }

    @Test
    void processNotification_UnhandledEventType_DoesNotThrow() {
        StripeWebhookEvent event = createEvent("some.unknown.event");

        // Should not throw, just log info
        assertDoesNotThrow(() -> processor.processNotification(event));
    }

    @Test
    void processNotification_UsesLockingQuery() {
        StripeWebhookEvent event = createEvent("customer.subscription.updated");
        event.setSubscriptionId("sub_test123");
        event.setStatus("active");

        UserSubscription subscription = createMockSubscription();
        when(userSubscriptionRepository.findByStripeSubscriptionIdWithLock("sub_test123"))
                .thenReturn(Optional.of(subscription));
        when(userSubscriptionRepository.save(any())).thenReturn(subscription);

        processor.processNotification(event);

        // Verify the locking query was used
        verify(userSubscriptionRepository).findByStripeSubscriptionIdWithLock("sub_test123");
    }

    private StripeWebhookEvent createEvent(String type) {
        StripeWebhookEvent event = new StripeWebhookEvent();
        event.setEventId("evt_test_" + System.currentTimeMillis());
        event.setType(type);
        return event;
    }

    private UserSubscription createMockSubscription() {
        User user = new User();
        user.setId(1);
        user.setEmail("test@example.com");

        Group group = new Group();
        group.setId(1);
        group.setDuration(30);

        UserSubscription subscription = new UserSubscription();
        subscription.setId(1);
        subscription.setUser(user);
        subscription.setGroup(group);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setGateway(GatewayName.STRIPE);
        subscription.setStripeSubscriptionId("sub_test123");
        subscription.setExpiresAt(LocalDateTime.now().plusDays(30));
        subscription.setAutoRenew(true);
        subscription.setCanceled(false);
        subscription.setDuration(30);
        subscription.setIsTrialPeriod(false);

        return subscription;
    }
}
