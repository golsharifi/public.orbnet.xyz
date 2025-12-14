package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.TokenBalance;
import com.orbvpn.api.domain.enums.TokenTransactionType;
import com.orbvpn.api.domain.dto.DailyStats;
import com.orbvpn.api.domain.dto.GlobalTokenStats;
import com.orbvpn.api.domain.dto.RemainingLimits;
import com.orbvpn.api.domain.dto.UserTokenStats;
import java.math.BigDecimal;
import java.util.List;

public interface TokenServiceInterface {
    boolean hasValidSubscription(Integer userId);

    boolean hasDailySubscription(Integer userId);

    TokenBalance getBalance(Integer userId);

    TokenBalance earnTokens(Integer userId, String adVendor, String region);

    TokenBalance spendTokens(Integer userId, int minutes, int activeDevices);

    TokenBalance addTokens(Integer userId, BigDecimal amount, TokenTransactionType type);

    TokenBalance deductTokens(Integer userId, BigDecimal amount, TokenTransactionType type);

    DailyStats getDailyStats(Integer userId);

    RemainingLimits getRemainingLimits(Integer userId);

    GlobalTokenStats getGlobalStats();

    List<UserTokenStats> getAllUserStats(Integer page, Integer size, String sortBy, Boolean ascending);

    List<UserTokenStats> searchUserStats(String searchTerm, Integer page, Integer size);
}
