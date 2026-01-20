package com.orbvpn.api.domain.dto;

import java.math.BigDecimal;

public class TokenRateInput {

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

    // Getters and Setters
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

    public void setMinDailyAds(Integer minDailyAds) {
        this.minDailyAds = minDailyAds;
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
}