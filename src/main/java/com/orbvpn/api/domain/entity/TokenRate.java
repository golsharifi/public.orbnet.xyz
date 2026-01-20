package com.orbvpn.api.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "token_rate")
public class TokenRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String region;
    private String adVendor;
    private BigDecimal tokenPerAd;
    private BigDecimal tokenPerMinute;
    private Integer dailyAdLimit;
    private Integer hourlyAdLimit;
    private Integer minDailyAds;
    private Integer minWeeklyAds;
    private Integer deviceLimit;
    private BigDecimal multiDeviceRate;
    private BigDecimal minimumBalance;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    // Other getters and setters...

    public void setId(Long id) {
        this.id = id;
    }

    // ... rest of the getters and setters

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAdVendor() {
        return adVendor;
    }

    public void setAdVendor(String adVendor) {
        this.adVendor = adVendor;
    }

    public BigDecimal getTokenPerAd() {
        return tokenPerAd;
    }

    public void setTokenPerAd(BigDecimal tokenPerAd) {
        this.tokenPerAd = tokenPerAd;
    }

    public BigDecimal getTokenPerMinute() {
        return tokenPerMinute;
    }

    public void setTokenPerMinute(BigDecimal tokenPerMinute) {
        this.tokenPerMinute = tokenPerMinute;
    }

    public Integer getDailyAdLimit() {
        return dailyAdLimit;
    }

    public void setDailyAdLimit(Integer dailyAdLimit) {
        this.dailyAdLimit = dailyAdLimit;
    }

    public Integer getHourlyAdLimit() {
        return hourlyAdLimit;
    }

    public void setHourlyAdLimit(Integer hourlyAdLimit) {
        this.hourlyAdLimit = hourlyAdLimit;
    }

    public Integer getMinDailyAds() {
        return minDailyAds;
    }

    public Integer getMinWeeklyAds() {
        return minWeeklyAds;
    }

    public void setMinWeeklyAds(Integer minWeeklyAds) {
        this.minWeeklyAds = minWeeklyAds;
    }

    public Integer getDeviceLimit() {
        return deviceLimit;
    }

    public void setDeviceLimit(Integer deviceLimit) {
        this.deviceLimit = deviceLimit;
    }

    public BigDecimal getMultiDeviceRate() {
        return multiDeviceRate;
    }

    public void setMultiDeviceRate(BigDecimal multiDeviceRate) {
        this.multiDeviceRate = multiDeviceRate;
    }

    public BigDecimal getMinimumBalance() {
        return minimumBalance;
    }

    public void setMinimumBalance(BigDecimal minimumBalance) {
        this.minimumBalance = minimumBalance;
    }

    public void setMinDailyAds(Integer minDailyAds) {
        this.minDailyAds = minDailyAds;
    }
}