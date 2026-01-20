package com.orbvpn.api.service.subscription;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
public class ProductGroupMapper {
    private final Map<String, Integer> groupMap;

    public ProductGroupMapper(Map<String, Integer> groupMap) {
        this.groupMap = groupMap;
        log.info("Initialized ProductGroupMapper with mappings: {}", groupMap);
    }

    /**
     * Maps a product ID to its corresponding group ID.
     *
     * @param productId The product ID to map
     * @return The corresponding group ID
     * @throws IllegalArgumentException if the product ID cannot be mapped
     */
    public int mapProductIdToGroupId(String productId) {
        log.debug("Attempting to map product ID: {}", productId);

        if (productId == null || productId.trim().isEmpty()) {
            log.error("Product ID is null or empty");
            throw new IllegalArgumentException("Product ID cannot be null or empty");
        }

        // First try direct mapping
        Integer groupId = groupMap.get(productId);
        if (groupId != null) {
            log.debug("Found direct mapping for product ID: {} -> {}", productId, groupId);
            return groupId;
        }

        // Try to extract base product ID if it's a complex ID
        try {
            String[] parts = productId.split("\\.");
            if (parts.length > 0) {
                String baseProductId = parts[0];
                groupId = groupMap.get(baseProductId);
                if (groupId != null) {
                    log.debug("Found mapping for base product ID: {} -> {}", baseProductId, groupId);
                    return groupId;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse complex product ID: {}", productId, e);
        }

        // If no mapping found, throw exception
        log.error("No mapping found for product ID: {}. Available mappings: {}", productId, groupMap);
        throw new IllegalArgumentException("Unknown product ID: " + productId);
    }

    /**
     * Safely maps a product ID to its corresponding group ID.
     *
     * @param productId The product ID to map
     * @return Optional containing the group ID if found, empty Optional otherwise
     */
    public Optional<Integer> safeMapProductIdToGroupId(String productId) {
        try {
            return Optional.of(mapProductIdToGroupId(productId));
        } catch (Exception e) {
            log.warn("Failed to map product ID: {} - {}", productId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Maps a product ID to its corresponding group ID with a default value.
     *
     * @param productId      The product ID to map
     * @param defaultGroupId The default group ID to return if mapping fails
     * @return The mapped group ID or the default value
     */
    public int mapProductIdToGroupIdWithDefault(String productId, int defaultGroupId) {
        try {
            return mapProductIdToGroupId(productId);
        } catch (Exception e) {
            log.warn("Failed to map product ID: {}, using default group ID: {}", productId, defaultGroupId);
            return defaultGroupId;
        }
    }

    /**
     * Checks if a product ID has a valid mapping.
     *
     * @param productId The product ID to check
     * @return true if the product ID has a valid mapping, false otherwise
     */
    public boolean hasValidMapping(String productId) {
        if (productId == null || productId.trim().isEmpty()) {
            return false;
        }

        // Check direct mapping
        if (groupMap.containsKey(productId)) {
            return true;
        }

        // Check base product ID
        try {
            String[] parts = productId.split("\\.");
            if (parts.length > 0) {
                return groupMap.containsKey(parts[0]);
            }
        } catch (Exception e) {
            log.warn("Failed to parse product ID while checking mapping: {}", productId);
        }

        return false;
    }

    /**
     * Gets the underlying group map.
     *
     * @return The map of product IDs to group IDs
     */
    public Map<String, Integer> getGroupMap() {
        return Map.copyOf(groupMap); // Return immutable copy
    }

    /**
     * Gets all available product IDs.
     *
     * @return Set of all product IDs
     */
    public Set<String> getAvailableProductIds() {
        return groupMap.keySet();
    }

    /**
     * Validates if the provided group ID is valid.
     *
     * @param groupId The group ID to validate
     * @return true if the group ID exists in the mappings, false otherwise
     */
    public boolean isValidGroupId(Integer groupId) {
        return groupId != null && groupMap.containsValue(groupId);
    }

    /**
     * Gets the product ID for a given group ID.
     *
     * @param groupId The group ID to look up
     * @return Optional containing the product ID if found, empty Optional otherwise
     */
    public Optional<String> getProductIdForGroupId(Integer groupId) {
        return groupMap.entrySet().stream()
                .filter(entry -> entry.getValue().equals(groupId))
                .map(Map.Entry::getKey)
                .findFirst();
    }
}
