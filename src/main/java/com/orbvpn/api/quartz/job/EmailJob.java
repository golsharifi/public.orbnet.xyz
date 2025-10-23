package com.orbvpn.api.quartz.job;

import com.orbvpn.api.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class EmailJob extends QuartzJobBean {
    private final NotificationService notificationService;

    @Override
    protected void executeInternal(JobExecutionContext jobExecutionContext) {
        log.info("Executing Job with key {}", jobExecutionContext.getJobDetail().getKey());

        JobDataMap jobDataMap = jobExecutionContext.getMergedJobDataMap();
        String recipientEmail = jobDataMap.getString("email");

        Map<String, Object> variables = new HashMap<>();
        variables.put("subject", jobDataMap.getString("subject"));
        variables.put("message", jobDataMap.getString("body"));

        notificationService.sendSystemNotification( // New method in NotificationService
                recipientEmail,
                variables,
                Locale.getDefault());
    }
}
