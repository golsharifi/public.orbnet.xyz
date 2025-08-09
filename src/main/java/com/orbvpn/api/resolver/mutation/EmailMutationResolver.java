package com.orbvpn.api.resolver.mutation;

import com.orbvpn.api.domain.dto.ScheduleEmailRequest;
import com.orbvpn.api.domain.dto.ScheduleEmailResponse;
import com.orbvpn.api.quartz.EmailJobScheduler;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class EmailMutationResolver {
    private final EmailJobScheduler emailJobScheduler;

    @Secured(ADMIN)
    @MutationMapping
    public Boolean scheduleEmail(@Argument @Valid ScheduleEmailRequest scheduleEmailRequest) {
        log.info("Scheduling email to: {}", scheduleEmailRequest.getEmail());
        try {
            ScheduleEmailResponse response = emailJobScheduler.scheduleEmail(scheduleEmailRequest);
            log.info("Email scheduled successfully: {}", response);
            return response.isSuccess();
        } catch (Exception e) {
            log.error("Error scheduling email - Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}