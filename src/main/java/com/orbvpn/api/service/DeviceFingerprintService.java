package com.orbvpn.api.service;

import com.orbvpn.api.domain.entity.DeviceFingerprint;
import com.orbvpn.api.domain.entity.MacOuiEntry;
import com.orbvpn.api.domain.enums.DeviceType;
import com.orbvpn.api.repository.DeviceFingerprintRepository;
import com.orbvpn.api.repository.MacOuiEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for device fingerprinting and identification.
 * Combines MAC OUI lookup with crowdsourced fingerprint data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceFingerprintService {

    private final MacOuiEntryRepository macOuiRepository;
    private final DeviceFingerprintRepository fingerprintRepository;

    // IEEE OUI database URL
    private static final String IEEE_OUI_URL = "https://standards-oui.ieee.org/oui/oui.txt";

    /**
     * Identify a device based on its characteristics.
     * Returns the best match with device type, manufacturer, and confidence.
     */
    @Transactional
    public DeviceIdentificationResult identifyDevice(DeviceIdentificationRequest request) {
        DeviceIdentificationResult result = new DeviceIdentificationResult();
        result.setConfidence(0);

        // Step 1: MAC OUI lookup
        if (request.getMacAddress() != null && !request.getMacAddress().isEmpty()) {
            String ouiPrefix = normalizeToOui(request.getMacAddress());
            if (ouiPrefix != null && ouiPrefix.length() >= 6) {
                Optional<MacOuiEntry> ouiEntry = macOuiRepository.findByOuiPrefixIgnoreCase(ouiPrefix);
                if (ouiEntry.isPresent()) {
                    MacOuiEntry entry = ouiEntry.get();
                    result.setVendor(entry.getVendorShort() != null ? entry.getVendorShort() : entry.getVendorName());
                    result.setManufacturer(entry.getVendorName());
                    result.setConfidence(40);

                    // If we have a common device type from OUI, use it
                    if (entry.getCommonDeviceType() != null) {
                        try {
                            result.setDeviceType(DeviceType.valueOf(entry.getCommonDeviceType()));
                            result.setConfidence(50);
                        } catch (Exception e) {
                            // Invalid device type
                        }
                    }

                    // Increment seen count asynchronously
                    incrementOuiSeenCount(ouiPrefix);
                }
            }
        }

        // Step 2: Fingerprint matching
        List<DeviceFingerprint> fingerprints = fingerprintRepository.findByIsActiveTrueOrderByPriorityDescConfidenceScoreDesc();

        for (DeviceFingerprint fp : fingerprints) {
            if (matchesFingerprint(fp, request)) {
                // Found a match
                if (fp.getDeviceType() != null) {
                    result.setDeviceType(fp.getDeviceType());
                }
                if (fp.getManufacturer() != null && !fp.getManufacturer().isEmpty()) {
                    result.setManufacturer(fp.getManufacturer());
                }
                if (fp.getDeviceModel() != null) {
                    result.setDeviceModel(fp.getDeviceModel());
                }
                if (fp.getOperatingSystem() != null) {
                    result.setOperatingSystem(fp.getOperatingSystem());
                }

                // Update confidence based on fingerprint confidence
                result.setConfidence(Math.max(result.getConfidence(), fp.getConfidenceScore()));

                // Increment match count asynchronously
                incrementFingerprintMatchCount(fp.getId());

                // Found best match
                break;
            }
        }

        // Step 3: Heuristic detection if no match found
        if (result.getDeviceType() == null || result.getDeviceType() == DeviceType.UNKNOWN) {
            result.setDeviceType(guessDeviceType(request));
        }

        return result;
    }

    /**
     * Look up vendor by MAC address.
     */
    public Optional<MacOuiEntry> lookupMac(String macAddress) {
        String ouiPrefix = normalizeToOui(macAddress);
        if (ouiPrefix == null || ouiPrefix.length() < 6) {
            return Optional.empty();
        }
        return macOuiRepository.findByOuiPrefixIgnoreCase(ouiPrefix);
    }

    /**
     * Submit device fingerprint data from client.
     * Used for crowdsourcing device identification.
     */
    @Transactional
    public void submitDeviceData(DeviceDataSubmission submission) {
        String ouiPrefix = normalizeToOui(submission.getMacAddress());

        // Check if we have an existing fingerprint that matches
        String portSignature = normalizePortSignature(submission.getOpenPorts());

        Optional<DeviceFingerprint> existing = fingerprintRepository
            .findByPortSignatureAndMacPrefix(portSignature, ouiPrefix);

        if (existing.isPresent()) {
            // Confirm existing fingerprint
            fingerprintRepository.confirmFingerprint(existing.get().getId());
        } else if (submission.getDeviceType() != null) {
            // Create new fingerprint from user data
            DeviceFingerprint newFp = DeviceFingerprint.builder()
                .macPrefix(ouiPrefix)
                .portSignature(portSignature)
                .hostnamePattern(createHostnamePattern(submission.getHostname()))
                .ssdpServerPattern(submission.getSsdpServer())
                .mdnsServiceType(submission.getMdnsService())
                .deviceType(submission.getDeviceType())
                .manufacturer(submission.getManufacturer())
                .deviceModel(submission.getDeviceModel())
                .confidenceScore(20) // Low initial confidence for crowdsourced data
                .confirmedByUsers(1L)
                .priority(0)
                .isActive(true)
                .notes("Crowdsourced from user submission")
                .build();

            fingerprintRepository.save(newFp);
            log.info("Created new device fingerprint from user submission: {}", submission.getDeviceType());
        }

        // Update OUI entry if we have new device type info
        if (ouiPrefix != null && submission.getDeviceType() != null) {
            macOuiRepository.findByOuiPrefixIgnoreCase(ouiPrefix).ifPresent(oui -> {
                if (oui.getCommonDeviceType() == null) {
                    oui.setCommonDeviceType(submission.getDeviceType().name());
                    oui.setSource("CROWDSOURCED");
                    macOuiRepository.save(oui);
                }
            });
        }
    }

    /**
     * Import IEEE OUI database.
     * This should be run periodically to keep the database up to date.
     */
    @Transactional
    public ImportResult importIeeeOuiDatabase() {
        ImportResult result = new ImportResult();
        result.setStartTime(System.currentTimeMillis());

        try {
            URL url = new URL(IEEE_OUI_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                String currentOui = null;
                String currentVendor = null;

                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    // Look for lines like: "00-00-00   (hex)		XEROX CORPORATION"
                    if (line.contains("(hex)")) {
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 3) {
                            currentOui = parts[0].replace("-", "").toUpperCase();

                            // Extract vendor name (everything after "(hex)")
                            int hexIdx = line.indexOf("(hex)");
                            if (hexIdx > 0 && line.length() > hexIdx + 6) {
                                currentVendor = line.substring(hexIdx + 5).trim();
                            }

                            if (currentOui.length() == 6 && currentVendor != null && !currentVendor.isEmpty()) {
                                // Check if exists
                                if (!macOuiRepository.existsByOuiPrefix(currentOui)) {
                                    MacOuiEntry entry = MacOuiEntry.builder()
                                        .ouiPrefix(currentOui)
                                        .vendorName(currentVendor)
                                        .vendorShort(shortenVendorName(currentVendor))
                                        .source("IEEE")
                                        .seenCount(0L)
                                        .build();
                                    macOuiRepository.save(entry);
                                    result.setImported(result.getImported() + 1);
                                } else {
                                    result.setSkipped(result.getSkipped() + 1);
                                }
                            }
                        }
                    }
                }
            }

            result.setSuccess(true);
        } catch (Exception e) {
            log.error("Failed to import IEEE OUI database", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }

        result.setEndTime(System.currentTimeMillis());
        log.info("IEEE OUI import completed: {} imported, {} skipped", result.getImported(), result.getSkipped());
        return result;
    }

    /**
     * Seed the database with common device fingerprints.
     */
    @Transactional
    public void seedDefaultFingerprints() {
        List<DeviceFingerprint> defaults = createDefaultFingerprints();
        for (DeviceFingerprint fp : defaults) {
            // Check if similar fingerprint exists
            if (fp.getMacPrefix() != null) {
                List<DeviceFingerprint> existing = fingerprintRepository.findByMacPrefix(fp.getMacPrefix());
                if (!existing.isEmpty()) continue;
            }
            fingerprintRepository.save(fp);
        }
        log.info("Seeded {} default fingerprints", defaults.size());
    }

    /**
     * Get statistics about the fingerprint database.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalOuiEntries", macOuiRepository.count());
        stats.put("ieeeEntries", macOuiRepository.countBySource("IEEE"));
        stats.put("crowdsourcedEntries", macOuiRepository.countBySource("CROWDSOURCED"));
        stats.put("totalFingerprints", fingerprintRepository.count());
        stats.put("activeFingerprints", fingerprintRepository.countActive());

        // Device type distribution
        List<Object[]> typeCounts = fingerprintRepository.countByDeviceType();
        Map<String, Long> typeDistribution = new HashMap<>();
        for (Object[] row : typeCounts) {
            if (row[0] != null) {
                typeDistribution.put(row[0].toString(), (Long) row[1]);
            }
        }
        stats.put("fingerprintsByType", typeDistribution);

        return stats;
    }

    // Private helper methods

    private boolean matchesFingerprint(DeviceFingerprint fp, DeviceIdentificationRequest req) {
        int matchScore = 0;

        // MAC prefix match (30 points)
        if (fp.getMacPrefix() != null && req.getMacAddress() != null) {
            String deviceOui = normalizeToOui(req.getMacAddress());
            if (deviceOui != null && deviceOui.startsWith(fp.getMacPrefix())) {
                matchScore += 30;
            }
        }

        // Port signature match (25 points)
        if (fp.getPortSignature() != null && req.getOpenPorts() != null) {
            String devicePorts = normalizePortSignature(req.getOpenPorts());
            if (portsOverlap(fp.getPortSignature(), devicePorts)) {
                matchScore += 25;
            }
        }

        // Hostname pattern match (20 points)
        if (fp.getHostnamePattern() != null && req.getHostname() != null) {
            try {
                if (req.getHostname().matches(fp.getHostnamePattern())) {
                    matchScore += 20;
                }
            } catch (Exception e) {
                // Invalid regex
            }
        }

        // SSDP server match (20 points)
        if (fp.getSsdpServerPattern() != null && req.getSsdpServer() != null) {
            try {
                if (req.getSsdpServer().matches(".*" + fp.getSsdpServerPattern() + ".*")) {
                    matchScore += 20;
                }
            } catch (Exception e) {
                // Invalid regex
            }
        }

        // mDNS service match (25 points)
        if (fp.getMdnsServiceType() != null && req.getMdnsServices() != null) {
            for (String service : req.getMdnsServices()) {
                if (service.contains(fp.getMdnsServiceType())) {
                    matchScore += 25;
                    break;
                }
            }
        }

        // TTL match (10 points)
        if (fp.getTtlValue() != null && req.getTtl() != null) {
            if (fp.getTtlValue().equals(req.getTtl())) {
                matchScore += 10;
            }
        }

        return matchScore >= 20;
    }

    private DeviceType guessDeviceType(DeviceIdentificationRequest req) {
        Set<Integer> ports = new HashSet<>();
        if (req.getOpenPorts() != null) {
            for (Integer p : req.getOpenPorts()) {
                ports.add(p);
            }
        }

        String hostname = req.getHostname() != null ? req.getHostname().toLowerCase() : "";
        String vendor = req.getManufacturer() != null ? req.getManufacturer().toLowerCase() : "";

        // Hostname-based detection
        if (hostname.contains("iphone") || hostname.contains("ipad")) return DeviceType.PHONE;
        if (hostname.contains("macbook") || hostname.contains("imac")) return DeviceType.COMPUTER;
        if (hostname.contains("apple-tv") || hostname.contains("appletv")) return DeviceType.TV;
        if (hostname.contains("chromecast") || hostname.contains("google-home")) return DeviceType.SMART_SPEAKER;
        if (hostname.contains("echo") || hostname.contains("alexa")) return DeviceType.SMART_SPEAKER;
        if (hostname.contains("printer")) return DeviceType.PRINTER;
        if (hostname.contains("camera") || hostname.contains("cam")) return DeviceType.CAMERA;
        if (hostname.contains("nas") || hostname.contains("synology") || hostname.contains("qnap")) return DeviceType.NAS;
        if (hostname.contains("xbox") || hostname.contains("playstation") || hostname.contains("nintendo")) return DeviceType.GAME_CONSOLE;
        if (hostname.contains("tv") || hostname.contains("samsung-tv") || hostname.contains("lg-tv")) return DeviceType.TV;
        if (hostname.contains("android") || hostname.contains("galaxy") || hostname.contains("pixel")) return DeviceType.PHONE;

        // Port-based detection
        if (ports.contains(62078)) return DeviceType.PHONE; // iPhone sync
        if (ports.contains(9100) || ports.contains(515) || ports.contains(631)) return DeviceType.PRINTER;
        if (ports.contains(80) && ports.contains(443) && ports.contains(53)) return DeviceType.ROUTER;
        if (ports.contains(548) && (ports.contains(139) || ports.contains(445))) return DeviceType.NAS;
        if (ports.contains(3389)) return DeviceType.COMPUTER; // RDP
        if (ports.contains(5900) || ports.contains(5901)) return DeviceType.COMPUTER; // VNC
        if (ports.contains(8008) || ports.contains(8009)) return DeviceType.SMART_SPEAKER; // Chromecast
        if (ports.contains(8001) && ports.contains(8002)) return DeviceType.TV; // Samsung TV
        if (ports.contains(32400)) return DeviceType.STREAMING_BOX; // Plex

        // Vendor-based detection
        if (vendor.contains("apple")) {
            return ports.contains(62078) ? DeviceType.PHONE : DeviceType.COMPUTER;
        }
        if (vendor.contains("samsung") && ports.contains(8001)) return DeviceType.TV;
        if (vendor.contains("roku")) return DeviceType.STREAMING_BOX;
        if (vendor.contains("sonos")) return DeviceType.SMART_SPEAKER;

        return DeviceType.UNKNOWN;
    }

    private String normalizeToOui(String mac) {
        if (mac == null) return null;
        String clean = mac.replaceAll("[:-]", "").toUpperCase();
        return clean.length() >= 6 ? clean.substring(0, 6) : null;
    }

    private String normalizePortSignature(List<Integer> ports) {
        if (ports == null || ports.isEmpty()) return "";
        return ports.stream()
            .sorted()
            .map(Object::toString)
            .collect(Collectors.joining(","));
    }

    private boolean portsOverlap(String signature1, String signature2) {
        Set<String> ports1 = new HashSet<>(Arrays.asList(signature1.split(",")));
        Set<String> ports2 = new HashSet<>(Arrays.asList(signature2.split(",")));
        ports1.retainAll(ports2);
        return !ports1.isEmpty();
    }

    private String createHostnamePattern(String hostname) {
        if (hostname == null || hostname.isEmpty()) return null;
        // Create a pattern that matches similar hostnames
        // e.g., "iPhone-John" -> ".*iPhone.*"
        String[] parts = hostname.split("[-_. ]");
        if (parts.length > 0) {
            return ".*" + parts[0] + ".*";
        }
        return null;
    }

    private String shortenVendorName(String vendorName) {
        if (vendorName == null) return null;
        if (vendorName.length() <= 20) return vendorName;

        // Common shortenings
        String[] removeWords = {"Inc.", "Inc", "Corporation", "Corp.", "Corp", "Ltd.", "Ltd",
            "LLC", "L.L.C.", "Co.", "Company", "Technologies", "Technology", "Electronics"};

        String result = vendorName;
        for (String word : removeWords) {
            result = result.replace(word, "").trim();
        }

        return result.length() > 30 ? result.substring(0, 30) : result;
    }

    @Async
    protected void incrementOuiSeenCount(String ouiPrefix) {
        try {
            macOuiRepository.incrementSeenCount(ouiPrefix);
        } catch (Exception e) {
            log.debug("Failed to increment OUI seen count: {}", e.getMessage());
        }
    }

    @Async
    protected void incrementFingerprintMatchCount(Long id) {
        try {
            fingerprintRepository.incrementMatchCount(id);
        } catch (Exception e) {
            log.debug("Failed to increment fingerprint match count: {}", e.getMessage());
        }
    }

    private List<DeviceFingerprint> createDefaultFingerprints() {
        return Arrays.asList(
            // Apple devices
            DeviceFingerprint.builder()
                .mdnsServiceType("_airplay._tcp")
                .deviceType(DeviceType.TV)
                .manufacturer("Apple")
                .deviceModel("Apple TV")
                .confidenceScore(90)
                .priority(100)
                .isActive(true)
                .notes("Apple TV via AirPlay service")
                .build(),

            DeviceFingerprint.builder()
                .portSignature("62078")
                .deviceType(DeviceType.PHONE)
                .manufacturer("Apple")
                .deviceModel("iPhone/iPad")
                .confidenceScore(95)
                .priority(100)
                .isActive(true)
                .notes("Apple mobile device via sync port")
                .build(),

            // Google devices
            DeviceFingerprint.builder()
                .mdnsServiceType("_googlecast._tcp")
                .deviceType(DeviceType.SMART_SPEAKER)
                .manufacturer("Google")
                .confidenceScore(90)
                .priority(100)
                .isActive(true)
                .notes("Google Cast device")
                .build(),

            DeviceFingerprint.builder()
                .portSignature("8008,8009")
                .deviceType(DeviceType.SMART_SPEAKER)
                .manufacturer("Google")
                .deviceModel("Chromecast")
                .confidenceScore(85)
                .priority(90)
                .isActive(true)
                .notes("Chromecast via ports")
                .build(),

            // Samsung TV
            DeviceFingerprint.builder()
                .portSignature("8001,8002")
                .ssdpServerPattern(".*Samsung.*")
                .deviceType(DeviceType.TV)
                .manufacturer("Samsung")
                .deviceModel("Samsung Smart TV")
                .confidenceScore(85)
                .priority(90)
                .isActive(true)
                .notes("Samsung TV via ports and SSDP")
                .build(),

            // Philips Hue
            DeviceFingerprint.builder()
                .ssdpServerPattern(".*Hue.*IpBridge.*")
                .deviceType(DeviceType.IOT)
                .manufacturer("Philips")
                .deviceModel("Hue Bridge")
                .confidenceScore(95)
                .priority(100)
                .isActive(true)
                .notes("Philips Hue Bridge via SSDP")
                .build(),

            // Printers
            DeviceFingerprint.builder()
                .portSignature("515,631,9100")
                .mdnsServiceType("_ipp._tcp")
                .deviceType(DeviceType.PRINTER)
                .confidenceScore(90)
                .priority(100)
                .isActive(true)
                .notes("Network printer via IPP")
                .build(),

            // NAS devices
            DeviceFingerprint.builder()
                .portSignature("5000,5001")
                .ssdpServerPattern(".*Synology.*")
                .deviceType(DeviceType.NAS)
                .manufacturer("Synology")
                .confidenceScore(90)
                .priority(90)
                .isActive(true)
                .notes("Synology NAS")
                .build(),

            DeviceFingerprint.builder()
                .ssdpServerPattern(".*QNAP.*")
                .deviceType(DeviceType.NAS)
                .manufacturer("QNAP")
                .confidenceScore(90)
                .priority(90)
                .isActive(true)
                .notes("QNAP NAS")
                .build(),

            // Routers
            DeviceFingerprint.builder()
                .portSignature("53,80,443")
                .upnpDeviceType("urn:schemas-upnp-org:device:InternetGatewayDevice")
                .deviceType(DeviceType.ROUTER)
                .confidenceScore(85)
                .priority(80)
                .isActive(true)
                .notes("Internet Gateway Device")
                .build(),

            // Sonos
            DeviceFingerprint.builder()
                .portSignature("1400,1443")
                .ssdpServerPattern(".*Sonos.*")
                .deviceType(DeviceType.SMART_SPEAKER)
                .manufacturer("Sonos")
                .confidenceScore(95)
                .priority(100)
                .isActive(true)
                .notes("Sonos speaker")
                .build(),

            // Plex
            DeviceFingerprint.builder()
                .portSignature("32400,32469")
                .deviceType(DeviceType.STREAMING_BOX)
                .manufacturer("Plex")
                .deviceModel("Plex Media Server")
                .confidenceScore(90)
                .priority(90)
                .isActive(true)
                .notes("Plex server")
                .build(),

            // Windows computer
            DeviceFingerprint.builder()
                .portSignature("135,139,445,3389")
                .hostnamePattern("DESKTOP-.*|WIN-.*")
                .deviceType(DeviceType.COMPUTER)
                .operatingSystem("Windows")
                .ttlValue(128)
                .confidenceScore(80)
                .priority(70)
                .isActive(true)
                .notes("Windows PC via NetBIOS/RDP")
                .build(),

            // Linux server
            DeviceFingerprint.builder()
                .portSignature("22,80,443")
                .ttlValue(64)
                .deviceType(DeviceType.SERVER)
                .operatingSystem("Linux")
                .confidenceScore(60)
                .priority(50)
                .isActive(true)
                .notes("Linux server via SSH")
                .build()
        );
    }

    // DTO classes
    public static class DeviceIdentificationRequest {
        private String macAddress;
        private List<Integer> openPorts;
        private String hostname;
        private String ssdpServer;
        private List<String> mdnsServices;
        private String manufacturer;
        private Integer ttl;
        private Integer tcpWindowSize;

        // Getters and setters
        public String getMacAddress() { return macAddress; }
        public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
        public List<Integer> getOpenPorts() { return openPorts; }
        public void setOpenPorts(List<Integer> openPorts) { this.openPorts = openPorts; }
        public String getHostname() { return hostname; }
        public void setHostname(String hostname) { this.hostname = hostname; }
        public String getSsdpServer() { return ssdpServer; }
        public void setSsdpServer(String ssdpServer) { this.ssdpServer = ssdpServer; }
        public List<String> getMdnsServices() { return mdnsServices; }
        public void setMdnsServices(List<String> mdnsServices) { this.mdnsServices = mdnsServices; }
        public String getManufacturer() { return manufacturer; }
        public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
        public Integer getTtl() { return ttl; }
        public void setTtl(Integer ttl) { this.ttl = ttl; }
        public Integer getTcpWindowSize() { return tcpWindowSize; }
        public void setTcpWindowSize(Integer tcpWindowSize) { this.tcpWindowSize = tcpWindowSize; }
    }

    public static class DeviceIdentificationResult {
        private DeviceType deviceType;
        private String manufacturer;
        private String vendor;
        private String deviceModel;
        private String operatingSystem;
        private int confidence;

        // Getters and setters
        public DeviceType getDeviceType() { return deviceType; }
        public void setDeviceType(DeviceType deviceType) { this.deviceType = deviceType; }
        public String getManufacturer() { return manufacturer; }
        public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
        public String getVendor() { return vendor; }
        public void setVendor(String vendor) { this.vendor = vendor; }
        public String getDeviceModel() { return deviceModel; }
        public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }
        public String getOperatingSystem() { return operatingSystem; }
        public void setOperatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; }
        public int getConfidence() { return confidence; }
        public void setConfidence(int confidence) { this.confidence = confidence; }
    }

    public static class DeviceDataSubmission {
        private String macAddress;
        private List<Integer> openPorts;
        private String hostname;
        private String ssdpServer;
        private String mdnsService;
        private DeviceType deviceType;
        private String manufacturer;
        private String deviceModel;

        // Getters and setters
        public String getMacAddress() { return macAddress; }
        public void setMacAddress(String macAddress) { this.macAddress = macAddress; }
        public List<Integer> getOpenPorts() { return openPorts; }
        public void setOpenPorts(List<Integer> openPorts) { this.openPorts = openPorts; }
        public String getHostname() { return hostname; }
        public void setHostname(String hostname) { this.hostname = hostname; }
        public String getSsdpServer() { return ssdpServer; }
        public void setSsdpServer(String ssdpServer) { this.ssdpServer = ssdpServer; }
        public String getMdnsService() { return mdnsService; }
        public void setMdnsService(String mdnsService) { this.mdnsService = mdnsService; }
        public DeviceType getDeviceType() { return deviceType; }
        public void setDeviceType(DeviceType deviceType) { this.deviceType = deviceType; }
        public String getManufacturer() { return manufacturer; }
        public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }
        public String getDeviceModel() { return deviceModel; }
        public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }
    }

    public static class ImportResult {
        private boolean success;
        private int imported;
        private int skipped;
        private String errorMessage;
        private long startTime;
        private long endTime;

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public int getImported() { return imported; }
        public void setImported(int imported) { this.imported = imported; }
        public int getSkipped() { return skipped; }
        public void setSkipped(int skipped) { this.skipped = skipped; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public long getStartTime() { return startTime; }
        public void setStartTime(long startTime) { this.startTime = startTime; }
        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }
        public long getDurationMs() { return endTime - startTime; }
    }
}
