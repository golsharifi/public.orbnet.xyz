package com.orbvpn.api.service.payment;

import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.domain.entity.StripeCustomer;
import com.orbvpn.api.repository.UserRepository;
import com.orbvpn.api.repository.StripeCustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class StripeUserService {
    private final UserRepository userRepository;
    private final StripeCustomerRepository stripeCustomerRepository;

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public StripeCustomer saveStripeCustomer(StripeCustomer stripeCustomer) {
        return stripeCustomerRepository.save(stripeCustomer);
    }

    public void updateUserStripeId(User user, String stripeCustomerId) {
        user.setStripeCustomerId(stripeCustomerId);
        userRepository.save(user);
    }
}