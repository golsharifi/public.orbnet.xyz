package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.TokenBalance;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.repository.TokenBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class TokenBalanceService {
    private final TokenBalanceRepository tokenBalanceRepository;

    public Optional<TokenBalance> findByUserId(Integer userId) {
        return tokenBalanceRepository.findByUser_Id(userId);
    }

    public TokenBalance getOrCreateForUser(User user) {
        return tokenBalanceRepository.findByUser_Id(user.getId())
                .orElseGet(() -> {
                    TokenBalance newBalance = new TokenBalance();
                    newBalance.setUser(user);
                    newBalance.setBalance(BigDecimal.ZERO);
                    return tokenBalanceRepository.save(newBalance);
                });
    }
}