package com.orbvpn.api.service.notification;

import com.orbvpn.api.domain.entity.MessageDeliveryStatus;
import com.orbvpn.api.repository.MessageDeliveryStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageTrackingService {
    private final MessageDeliveryStatusRepository deliveryStatusRepository;

    @Transactional
    public MessageDeliveryStatus trackMessage(String userId, String channel) {
        MessageDeliveryStatus status = new MessageDeliveryStatus();
        status.setUserId(userId);
        status.setChannel(channel);
        status.setStatus("QUEUED");
        status.setSentAt(LocalDateTime.now());
        return deliveryStatusRepository.save(status);
    }

    @Transactional
    public MessageDeliveryStatus markSent(String messageId) {
        MessageDeliveryStatus status = getStatus(messageId);
        if (status != null) {
            status.setStatus("SENT");
            status.setSentAt(LocalDateTime.now());
            return deliveryStatusRepository.save(status);
        }
        return null;
    }

    @Transactional
    public MessageDeliveryStatus markDelivered(String messageId) {
        MessageDeliveryStatus status = getStatus(messageId);
        if (status != null) {
            status.setStatus("DELIVERED");
            status.setDeliveredAt(LocalDateTime.now());
            return deliveryStatusRepository.save(status);
        }
        return null;
    }

    @Transactional
    public MessageDeliveryStatus markRead(String messageId) {
        MessageDeliveryStatus status = getStatus(messageId);
        if (status != null) {
            status.setStatus("READ");
            status.setReadAt(LocalDateTime.now());
            return deliveryStatusRepository.save(status);
        }
        return null;
    }

    @Transactional
    public MessageDeliveryStatus markFailed(String messageId, String error) {
        MessageDeliveryStatus status = getStatus(messageId);
        if (status != null) {
            status.setStatus("FAILED");
            status.setErrorMessage(error);
            return deliveryStatusRepository.save(status);
        }
        return null;
    }

    @Transactional(readOnly = true)
    public MessageDeliveryStatus getStatus(String messageId) {
        return deliveryStatusRepository.findByMessageId(messageId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<MessageDeliveryStatus> getUserHistory(String userId) {
        return deliveryStatusRepository.findByUserIdOrderBySentAtDesc(userId);
    }
}