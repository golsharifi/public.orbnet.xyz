package com.orbvpn.api.service;

import static com.orbvpn.api.config.AppConstants.DEFAULT_SORT_NATIVE;

import com.orbvpn.api.domain.dto.UserFilterInput;
import com.orbvpn.api.domain.dto.UserFilterInput.SubscriptionStatusFilter;
import com.orbvpn.api.domain.dto.UserView;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.UserSubscription;
import com.orbvpn.api.mapper.UserViewMapper;
import com.orbvpn.api.mapper.UserSubscriptionViewMapper;
import com.orbvpn.api.repository.UserRepository;
import com.orbvpn.api.service.subscription.UserSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.orbvpn.api.domain.entity.UserDevice;
import com.orbvpn.api.domain.entity.UserPasskey;
import com.orbvpn.api.domain.entity.OauthToken;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final UserViewMapper userViewMapper;
    private final UserSubscriptionViewMapper userSubscriptionViewMapper;
    private final UserSubscriptionService userSubscriptionService;
    private final EntityManager entityManager;

    public int getTotalActiveUsers() {
        return userRepository.getTotalActiveUsers();
    }

    @Transactional(readOnly = true)
    public Page<UserView> getActiveUsers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(DEFAULT_SORT_NATIVE));

        return userRepository.findAllActiveUsers(pageable)
                .map(user -> {
                    UserView userView = userViewMapper.toView(user);
                    UserSubscription currentSubscription = userSubscriptionService.getCurrentSubscription(user);
                    if (currentSubscription != null) {
                        userView.setSubscription(userSubscriptionViewMapper.toView(currentSubscription));
                    }
                    return userView;
                });
    }

    @Transactional(readOnly = true)
    public Page<UserView> getInactiveUsers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(DEFAULT_SORT_NATIVE));

        return userRepository.findAllNotActiveUsers(pageable)
                .map(user -> {
                    UserView userView = userViewMapper.toView(user);
                    UserSubscription currentSubscription = userSubscriptionService.getCurrentSubscription(user);
                    if (currentSubscription != null) {
                        userView.setSubscription(userSubscriptionViewMapper.toView(currentSubscription));
                    }
                    return userView;
                });
    }

    @Transactional(readOnly = true)
    public Page<UserView> getAllUsers(boolean sort, int page, int size, String param, String query) {
        Direction dir = sort ? Direction.ASC : Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, "id"));

        Page<User> results;

        if (param.equals("email")) {
            results = userRepository.findByRoleIdAndEmailContaining(3, query, pageable);
        } else {
            results = userRepository.findByRoleIdAndUsernameContaining(3, query, pageable);
        }

        return results.map(user -> {
            UserView userView = userViewMapper.toView(user);
            UserSubscription currentSubscription = userSubscriptionService.getCurrentSubscription(user);
            if (currentSubscription != null) {
                userView.setSubscription(userSubscriptionViewMapper.toView(currentSubscription));
            }
            return userView;
        });
    }

    @Transactional(readOnly = true)
    public Page<UserView> getFilteredUsers(int page, int size, String sortBy, Boolean ascending,
                                           String searchQuery, UserFilterInput filters) {
        log.info("Fetching filtered users - page: {}, size: {}, sortBy: {}, ascending: {}, searchQuery: {}",
                page, size, sortBy, ascending, searchQuery);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<User> query = cb.createQuery(User.class);
        Root<User> root = query.from(User.class);

        List<Predicate> predicates = buildFilterPredicates(cb, root, searchQuery, filters);

        query.where(predicates.toArray(new Predicate[0]));

        // Sorting
        String sortField = sortBy != null ? sortBy : "createdAt";
        if ("expiresAt".equals(sortField)) {
            // Sort by subscription expiration date - need to join to userSubscriptionList
            Join<Object, Object> subscriptionJoin = root.join("userSubscriptionList", JoinType.LEFT);
            if (Boolean.TRUE.equals(ascending)) {
                query.orderBy(cb.asc(subscriptionJoin.get("expiresAt")));
            } else {
                query.orderBy(cb.desc(subscriptionJoin.get("expiresAt")));
            }
        } else {
            if (Boolean.TRUE.equals(ascending)) {
                query.orderBy(cb.asc(root.get(sortField)));
            } else {
                query.orderBy(cb.desc(root.get(sortField)));
            }
        }

        // Execute query with pagination
        TypedQuery<User> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult(page * size);
        typedQuery.setMaxResults(size);
        List<User> users = typedQuery.getResultList();

        // Count query
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<User> countRoot = countQuery.from(User.class);
        countQuery.select(cb.count(countRoot));
        List<Predicate> countPredicates = buildFilterPredicates(cb, countRoot, searchQuery, filters);
        countQuery.where(countPredicates.toArray(new Predicate[0]));
        Long total = entityManager.createQuery(countQuery).getSingleResult();

        // Map to UserView
        List<UserView> userViews = users.stream().map(user -> {
            UserView userView = userViewMapper.toView(user);
            UserSubscription currentSubscription = userSubscriptionService.getCurrentSubscription(user);
            if (currentSubscription != null) {
                userView.setSubscription(userSubscriptionViewMapper.toView(currentSubscription));
            }
            return userView;
        }).toList();

        return new PageImpl<>(userViews, PageRequest.of(page, size), total);
    }

    private List<Predicate> buildFilterPredicates(CriteriaBuilder cb, Root<User> root,
                                                   String searchQuery, UserFilterInput filters) {
        List<Predicate> predicates = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // Search query - search in email, username, firstName, lastName
        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            String pattern = "%" + searchQuery.toLowerCase() + "%";
            Predicate emailPred = cb.like(cb.lower(root.get("email")), pattern);
            Predicate usernamePred = cb.like(cb.lower(root.get("username")), pattern);

            // Join to profile for name search
            Join<Object, Object> profileJoin = root.join("profile", JoinType.LEFT);
            Predicate firstNamePred = cb.like(cb.lower(profileJoin.get("firstName")), pattern);
            Predicate lastNamePred = cb.like(cb.lower(profileJoin.get("lastName")), pattern);

            predicates.add(cb.or(emailPred, usernamePred, firstNamePred, lastNamePred));
        }

        if (filters == null) {
            return predicates;
        }

        // Account enabled filter
        if (filters.getAccountEnabled() != null) {
            predicates.add(cb.equal(root.get("enabled"), filters.getAccountEnabled()));
        }

        // Role filters
        if (filters.getRoles() != null && !filters.getRoles().isEmpty()) {
            Join<Object, Object> roleJoin = root.join("role", JoinType.INNER);
            predicates.add(roleJoin.get("name").as(String.class).in(filters.getRoles()));
        }

        // Subscription status filters
        if (filters.getSubscriptionStatuses() != null && !filters.getSubscriptionStatuses().isEmpty()) {
            List<Predicate> statusPredicates = new ArrayList<>();

            // Left join to userSubscriptionList to handle users without subscriptions
            Join<Object, Object> subscriptionJoin = root.join("userSubscriptionList", JoinType.LEFT);

            for (SubscriptionStatusFilter status : filters.getSubscriptionStatuses()) {
                switch (status) {
                    case ACTIVE:
                        statusPredicates.add(cb.and(
                            cb.isNotNull(subscriptionJoin.get("expiresAt")),
                            cb.greaterThan(subscriptionJoin.get("expiresAt"), now)
                        ));
                        break;
                    case EXPIRED:
                        statusPredicates.add(cb.and(
                            cb.isNotNull(subscriptionJoin.get("expiresAt")),
                            cb.lessThan(subscriptionJoin.get("expiresAt"), now)
                        ));
                        break;
                    case EXPIRING_SOON:
                        LocalDateTime sevenDaysFromNow = now.plusDays(7);
                        statusPredicates.add(cb.and(
                            cb.isNotNull(subscriptionJoin.get("expiresAt")),
                            cb.greaterThan(subscriptionJoin.get("expiresAt"), now),
                            cb.lessThan(subscriptionJoin.get("expiresAt"), sevenDaysFromNow)
                        ));
                        break;
                    case NO_SUBSCRIPTION:
                        statusPredicates.add(cb.isNull(subscriptionJoin.get("id")));
                        break;
                    case TRIAL:
                        // Assuming trial is indicated by a specific duration or group
                        statusPredicates.add(cb.and(
                            cb.isNotNull(subscriptionJoin.get("expiresAt")),
                            cb.lessThanOrEqualTo(subscriptionJoin.get("duration"), 7)
                        ));
                        break;
                }
            }

            if (!statusPredicates.isEmpty()) {
                predicates.add(cb.or(statusPredicates.toArray(new Predicate[0])));
            }
        }

        // Has devices filter
        if (filters.getHasDevices() != null) {
            Subquery<Long> deviceSubquery = cb.createQuery().subquery(Long.class);
            Root<UserDevice> deviceRoot = deviceSubquery.from(UserDevice.class);
            deviceSubquery.select(cb.count(deviceRoot));
            deviceSubquery.where(cb.equal(deviceRoot.get("user"), root));

            if (filters.getHasDevices()) {
                predicates.add(cb.greaterThan(deviceSubquery, 0L));
            } else {
                predicates.add(cb.equal(deviceSubquery, 0L));
            }
        }

        // Has telegram filter
        if (filters.getHasTelegram() != null) {
            Join<Object, Object> profileJoin = root.join("profile", JoinType.LEFT);
            if (filters.getHasTelegram()) {
                predicates.add(cb.and(
                    cb.isNotNull(profileJoin.get("telegramChatId")),
                    cb.notEqual(profileJoin.get("telegramChatId"), "")
                ));
            } else {
                predicates.add(cb.or(
                    cb.isNull(profileJoin.get("telegramChatId")),
                    cb.equal(profileJoin.get("telegramChatId"), "")
                ));
            }
        }

        // Has phone filter
        if (filters.getHasPhone() != null) {
            Join<Object, Object> profileJoin = root.join("profile", JoinType.LEFT);
            if (filters.getHasPhone()) {
                predicates.add(cb.and(
                    cb.isNotNull(profileJoin.get("phone")),
                    cb.notEqual(profileJoin.get("phone"), "")
                ));
            } else {
                predicates.add(cb.or(
                    cb.isNull(profileJoin.get("phone")),
                    cb.equal(profileJoin.get("phone"), "")
                ));
            }
        }

        // Country filter
        if (filters.getCountries() != null && !filters.getCountries().isEmpty()) {
            Join<Object, Object> profileJoin = root.join("profile", JoinType.LEFT);
            predicates.add(profileJoin.get("country").in(filters.getCountries()));
        }

        // Group ID filter
        if (filters.getGroupIds() != null && !filters.getGroupIds().isEmpty()) {
            Join<Object, Object> subscriptionJoin = root.join("userSubscriptionList", JoinType.LEFT);
            Join<Object, Object> groupJoin = subscriptionJoin.join("group", JoinType.LEFT);
            predicates.add(groupJoin.get("id").in(filters.getGroupIds()));
        }

        // Service Group ID filter
        if (filters.getServiceGroupIds() != null && !filters.getServiceGroupIds().isEmpty()) {
            Join<Object, Object> subscriptionJoin = root.join("userSubscriptionList", JoinType.LEFT);
            Join<Object, Object> groupJoin = subscriptionJoin.join("group", JoinType.LEFT);
            Join<Object, Object> serviceGroupJoin = groupJoin.join("serviceGroup", JoinType.LEFT);
            predicates.add(serviceGroupJoin.get("id").in(filters.getServiceGroupIds()));
        }

        // Reseller filter
        if (filters.getResellerId() != null) {
            predicates.add(cb.equal(root.get("reseller").get("id"), filters.getResellerId()));
        }

        // Date range filters
        if (filters.getCreatedAfter() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filters.getCreatedAfter()));
        }
        if (filters.getCreatedBefore() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filters.getCreatedBefore()));
        }
        if (filters.getExpiresAfter() != null) {
            Join<Object, Object> subscriptionJoin = root.join("userSubscriptionList", JoinType.LEFT);
            predicates.add(cb.greaterThanOrEqualTo(subscriptionJoin.get("expiresAt"), filters.getExpiresAfter()));
        }
        if (filters.getExpiresBefore() != null) {
            Join<Object, Object> subscriptionJoin = root.join("userSubscriptionList", JoinType.LEFT);
            predicates.add(cb.lessThanOrEqualTo(subscriptionJoin.get("expiresAt"), filters.getExpiresBefore()));
        }

        // Payment gateway filter
        if (filters.getPaymentGateways() != null && !filters.getPaymentGateways().isEmpty()) {
            Join<Object, Object> subscriptionJoin = root.join("userSubscriptionList", JoinType.LEFT);
            predicates.add(subscriptionJoin.get("gatewayName").in(filters.getPaymentGateways()));
        }

        // OAuth provider filter
        if (filters.getOauthProviders() != null && !filters.getOauthProviders().isEmpty()) {
            Subquery<Long> oauthSubquery = cb.createQuery().subquery(Long.class);
            Root<OauthToken> oauthRoot = oauthSubquery.from(OauthToken.class);
            oauthSubquery.select(cb.count(oauthRoot));
            oauthSubquery.where(
                cb.equal(oauthRoot.get("user"), root),
                oauthRoot.get("socialMedia").in(filters.getOauthProviders())
            );
            predicates.add(cb.greaterThan(oauthSubquery, 0L));
        }

        // Has passkeys filter
        if (filters.getHasPasskeys() != null) {
            Subquery<Long> passkeySubquery = cb.createQuery().subquery(Long.class);
            Root<UserPasskey> passkeyRoot = passkeySubquery.from(UserPasskey.class);
            passkeySubquery.select(cb.count(passkeyRoot));
            passkeySubquery.where(cb.equal(passkeyRoot.get("user"), root));

            if (filters.getHasPasskeys()) {
                predicates.add(cb.greaterThan(passkeySubquery, 0L));
            } else {
                predicates.add(cb.equal(passkeySubquery, 0L));
            }
        }

        // Has OAuth filter
        if (filters.getHasOauth() != null) {
            Subquery<Long> oauthSubquery = cb.createQuery().subquery(Long.class);
            Root<OauthToken> oauthRoot = oauthSubquery.from(OauthToken.class);
            oauthSubquery.select(cb.count(oauthRoot));
            oauthSubquery.where(cb.equal(oauthRoot.get("user"), root));

            if (filters.getHasOauth()) {
                predicates.add(cb.greaterThan(oauthSubquery, 0L));
            } else {
                predicates.add(cb.equal(oauthSubquery, 0L));
            }
        }

        return predicates;
    }

}
