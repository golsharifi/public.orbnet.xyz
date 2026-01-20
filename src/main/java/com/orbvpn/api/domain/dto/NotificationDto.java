package com.orbvpn.api.domain.dto;

import com.orbvpn.api.domain.enums.NotificationCategory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private String subject;
    private String content;
    private Map<String, String> data;
    private String image;
    private NotificationCategory category;

    public static NotificationDto createSimple(String subject, String content, NotificationCategory category) {
        return NotificationDto.builder()
                .subject(subject)
                .content(content)
                .category(category)
                .build();
    }
}