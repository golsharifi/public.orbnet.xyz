package com.orbvpn.api.resolver.query;

import com.orbvpn.api.domain.dto.AdminDashboardView;
import com.orbvpn.api.domain.dto.TransactionPage;
import com.orbvpn.api.domain.dto.UserFilterInput;
import com.orbvpn.api.domain.dto.UserStatsByCountryView;
import com.orbvpn.api.domain.dto.UserView;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.mapper.UserViewMapper;
import com.orbvpn.api.mapper.UserSubscriptionViewMapper;
import com.orbvpn.api.service.AdminAnalyticsService;
import com.orbvpn.api.service.AdminPaymentService;
import com.orbvpn.api.service.AdminService;
import com.orbvpn.api.service.ConnectionAdminDashboardService;
import com.orbvpn.api.service.DeviceService;
import com.orbvpn.api.service.UserService;
import com.orbvpn.api.service.subscription.UserSubscriptionService;
import com.orbvpn.api.exception.NotFoundException;
import java.util.List;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;

import static com.orbvpn.api.domain.enums.RoleName.Constants.ADMIN;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AdminQueryResolver {
    private final AdminService adminService;
    private final UserService userService;
    private final UserSubscriptionService userSubscriptionService;
    private final DeviceService deviceService;
    private final UserViewMapper userViewMapper;
    private final UserSubscriptionViewMapper userSubscriptionViewMapper;
    private final ConnectionAdminDashboardService dashboardService;
    private final AdminPaymentService adminPaymentService;
    private final AdminAnalyticsService adminAnalyticsService;

    @Secured(ADMIN)
    @QueryMapping
    public Page<UserView> activeUsers(
            @Argument @Valid @NotNull(message = "Page number is required") Integer page,
            @Argument @Valid @NotNull(message = "Page size is required") Integer size) {
        log.info("Fetching active users - page: {}, size: {}", page, size);
        try {
            Page<UserView> users = adminService.getActiveUsers(page, size);
            log.info("Successfully retrieved page {} of active users with {} entries", users.getNumber(),
                    users.getNumberOfElements());
            return users;
        } catch (Exception e) {
            log.error("Error fetching active users - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public Page<UserView> inactiveUsers(
            @Argument @Valid @NotNull(message = "Page number is required") Integer page,
            @Argument @Valid @NotNull(message = "Page size is required") Integer size) {
        log.info("Fetching inactive users - page: {}, size: {}", page, size);
        try {
            Page<UserView> users = adminService.getInactiveUsers(page, size);
            log.info("Successfully retrieved page {} of inactive users with {} entries", users.getNumber(),
                    users.getNumberOfElements());
            return users;
        } catch (Exception e) {
            log.error("Error fetching inactive users - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public Page<UserView> allUsers(
            @Argument Boolean sort,
            @Argument @Valid @NotNull(message = "Page number is required") Integer page,
            @Argument @Valid @NotNull(message = "Page size is required") Integer size,
            @Argument String param,
            @Argument String query) {
        log.info("Fetching all users - page: {}, size: {}, sort: {}, param: {}, query: {}",
                page, size, sort, param, query);
        try {
            // Handle null parameters with defaults
            boolean sortValue = sort != null ? sort : false;
            String paramValue = param != null ? param : "email";
            String queryValue = query != null ? query : "";
            Page<UserView> users = adminService.getAllUsers(sortValue, page, size, paramValue, queryValue);
            log.info("Successfully retrieved page {} of users with {} entries", users.getNumber(),
                    users.getNumberOfElements());
            return users;
        } catch (Exception e) {
            log.error("Error fetching all users - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public Integer totalActiveUsers() {
        log.info("Fetching total active users count");
        try {
            int count = adminService.getTotalActiveUsers();
            log.info("Successfully retrieved total active users count: {}", count);
            return count;
        } catch (Exception e) {
            log.error("Error fetching total active users count - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    @Transactional(readOnly = true)
    public UserView getUserById(
            @Argument @Valid @Positive(message = "User ID must be positive") Integer id) {
        log.info("Fetching user details for ID: {}", id);
        try {
            User user = userService.getUserById(id);
            if (user == null) {
                throw new NotFoundException("User not found with id: " + id);
            }

            UserView userView = userViewMapper.toView(user);
            UserSubscription currentSubscription = userSubscriptionService.getCurrentSubscription(user);
            userView.setCurrentSubscription(currentSubscription);
            if (currentSubscription != null) {
                userView.setSubscription(userSubscriptionViewMapper.toView(currentSubscription));
            }
            userView.setUserDeviceList(deviceService.getDevices(user.getId()));

            log.info("Successfully retrieved user details for ID: {}", id);
            return userView;
        } catch (NotFoundException e) {
            log.warn("User not found - ID: {} - Error: {}", id, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error fetching user details for ID: {} - Error: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public AdminDashboardView adminDashboard() {
        log.info("Fetching admin dashboard data");
        try {
            AdminDashboardView dashboard = dashboardService.getDashboardData();
            log.info("Successfully retrieved admin dashboard data");
            return dashboard;
        } catch (Exception e) {
            log.error("Error fetching admin dashboard data - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public TransactionPage allTransactions(
            @Argument @Valid @NotNull(message = "Page number is required") Integer page,
            @Argument @Valid @NotNull(message = "Page size is required") Integer size) {
        log.info("Fetching all transactions - page: {}, size: {}", page, size);
        try {
            TransactionPage transactions = adminPaymentService.getAllTransactions(page, size);
            log.info("Successfully retrieved page {} of transactions with {} entries",
                    transactions.getNumber(), transactions.getContent().size());
            return transactions;
        } catch (Exception e) {
            log.error("Error fetching transactions - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public List<UserStatsByCountryView> userStatsByCountry() {
        log.info("Fetching user statistics by country");
        try {
            List<UserStatsByCountryView> stats = adminAnalyticsService.getUserStatsByCountry();
            log.info("Successfully retrieved user stats for {} continents", stats.size());
            return stats;
        } catch (Exception e) {
            log.error("Error fetching user stats by country - Error: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Secured(ADMIN)
    @QueryMapping
    public Page<UserView> filteredUsers(
            @Argument @Valid @NotNull(message = "Page number is required") Integer page,
            @Argument @Valid @NotNull(message = "Page size is required") Integer size,
            @Argument String sortBy,
            @Argument Boolean ascending,
            @Argument String searchQuery,
            @Argument UserFilterInput filters) {
        log.info("Fetching filtered users - page: {}, size: {}, sortBy: {}, ascending: {}, searchQuery: {}, filters: {}",
                page, size, sortBy, ascending, searchQuery, filters);
        try {
            Page<UserView> users = adminService.getFilteredUsers(page, size, sortBy, ascending, searchQuery, filters);
            log.info("Successfully retrieved page {} of filtered users with {} entries",
                    users.getNumber(), users.getNumberOfElements());
            return users;
        } catch (Exception e) {
            log.error("Error fetching filtered users - Error: {}", e.getMessage(), e);
            throw e;
        }
    }
}