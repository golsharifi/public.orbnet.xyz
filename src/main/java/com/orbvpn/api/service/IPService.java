package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.Blacklist;
import com.orbvpn.api.domain.entity.Whitelist;
import com.orbvpn.api.repository.BlacklistRepository;
import com.orbvpn.api.repository.WhitelistRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class IPService {

    private final BlacklistRepository blacklistRepository;
    private final WhitelistRepository whitelistRepository;

    public IPService(BlacklistRepository blacklistRepository, WhitelistRepository whitelistRepository) {
        this.blacklistRepository = blacklistRepository;
        this.whitelistRepository = whitelistRepository;
    }

    public List<Blacklist> getBlacklistedIPs() {
        return blacklistRepository.findAll();
    }

    public List<Whitelist> getWhitelistedIPs() {
        return whitelistRepository.findAll();
    }

    public boolean isIPBlacklisted(String ip) {
        return blacklistRepository.findByIpAddress(ip) != null;
    }

    public boolean isIPWhitelisted(String ip) {
        return whitelistRepository.findByIpAddress(ip) != null;
    }

    public Blacklist addToBlacklist(String ipAddress) {
        Whitelist whitelistIP = whitelistRepository.findByIpAddress(ipAddress);
        if (whitelistIP != null) {
            whitelistRepository.delete(whitelistIP);
        }

        Blacklist blacklistIP = new Blacklist();
        blacklistIP.setIpAddress(ipAddress);
        return blacklistRepository.save(blacklistIP);
    }

    public boolean removeFromBlacklist(String ipAddress) {
        Blacklist existingBlacklist = blacklistRepository.findByIpAddress(ipAddress);
        if (existingBlacklist == null) {
            return false;
        }
        blacklistRepository.delete(existingBlacklist);
        return true;
    }

    public Whitelist addToWhitelist(String ipAddress) {
        Blacklist blacklistIP = blacklistRepository.findByIpAddress(ipAddress);
        if (blacklistIP != null) {
            blacklistRepository.delete(blacklistIP);
        }

        Whitelist whitelistIP = new Whitelist();
        whitelistIP.setIpAddress(ipAddress);
        return whitelistRepository.save(whitelistIP);
    }

    public boolean removeFromWhitelist(String ipAddress) {
        Blacklist existingBlacklist = blacklistRepository.findByIpAddress(ipAddress);
        if (existingBlacklist == null) {
            return false;
        }
        blacklistRepository.delete(existingBlacklist);
        return true;
    }
}
