package com.orbvpn.api.service;

import com.orbvpn.api.domain.dto.CountryStatsView;
import com.orbvpn.api.domain.dto.UserStatsByCountryView;
import com.orbvpn.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAnalyticsService {
    private final UserRepository userRepository;

    // Map of country codes to continents
    private static final Map<String, String> COUNTRY_TO_CONTINENT = new HashMap<>();

    static {
        // Africa
        String[] africa = {"DZ", "AO", "BJ", "BW", "BF", "BI", "CM", "CV", "CF", "TD", "KM", "CG", "CD", "CI", "DJ", "EG", "GQ", "ER", "ET", "GA", "GM", "GH", "GN", "GW", "KE", "LS", "LR", "LY", "MG", "MW", "ML", "MR", "MU", "YT", "MA", "MZ", "NA", "NE", "NG", "RE", "RW", "SH", "ST", "SN", "SC", "SL", "SO", "ZA", "SS", "SD", "SZ", "TZ", "TG", "TN", "UG", "EH", "ZM", "ZW"};
        for (String country : africa) COUNTRY_TO_CONTINENT.put(country, "Africa");

        // Asia
        String[] asia = {"AF", "AM", "AZ", "BH", "BD", "BT", "BN", "KH", "CN", "CX", "CC", "IO", "GE", "HK", "IN", "ID", "IR", "IQ", "IL", "JP", "JO", "KZ", "KW", "KG", "LA", "LB", "MO", "MY", "MV", "MN", "MM", "NP", "KP", "OM", "PK", "PS", "PH", "QA", "SA", "SG", "KR", "LK", "SY", "TW", "TJ", "TH", "TL", "TR", "TM", "AE", "UZ", "VN", "YE"};
        for (String country : asia) COUNTRY_TO_CONTINENT.put(country, "Asia");

        // Europe
        String[] europe = {"AX", "AL", "AD", "AT", "BY", "BE", "BA", "BG", "HR", "CY", "CZ", "DK", "EE", "FO", "FI", "FR", "DE", "GI", "GR", "GG", "HU", "IS", "IE", "IM", "IT", "JE", "XK", "LV", "LI", "LT", "LU", "MK", "MT", "MD", "MC", "ME", "NL", "NO", "PL", "PT", "RO", "RU", "SM", "RS", "SK", "SI", "ES", "SJ", "SE", "CH", "UA", "GB", "VA"};
        for (String country : europe) COUNTRY_TO_CONTINENT.put(country, "Europe");

        // North America
        String[] northAmerica = {"AI", "AG", "AW", "BS", "BB", "BZ", "BM", "BQ", "CA", "KY", "CR", "CU", "CW", "DM", "DO", "SV", "GL", "GD", "GP", "GT", "HT", "HN", "JM", "MQ", "MX", "MS", "NI", "PA", "PM", "PR", "BL", "KN", "LC", "MF", "VC", "SX", "TT", "TC", "US", "VG", "VI"};
        for (String country : northAmerica) COUNTRY_TO_CONTINENT.put(country, "North America");

        // South America
        String[] southAmerica = {"AR", "BO", "BR", "CL", "CO", "EC", "FK", "GF", "GY", "PY", "PE", "SR", "UY", "VE"};
        for (String country : southAmerica) COUNTRY_TO_CONTINENT.put(country, "South America");

        // Oceania
        String[] oceania = {"AS", "AU", "CK", "FJ", "PF", "GU", "KI", "MH", "FM", "NR", "NC", "NZ", "NU", "NF", "MP", "PW", "PG", "PN", "WS", "SB", "TK", "TO", "TV", "VU", "WF"};
        for (String country : oceania) COUNTRY_TO_CONTINENT.put(country, "Oceania");

        // Antarctica
        COUNTRY_TO_CONTINENT.put("AQ", "Antarctica");
        COUNTRY_TO_CONTINENT.put("BV", "Antarctica");
        COUNTRY_TO_CONTINENT.put("GS", "Antarctica");
        COUNTRY_TO_CONTINENT.put("HM", "Antarctica");
        COUNTRY_TO_CONTINENT.put("TF", "Antarctica");
    }

    @Transactional(readOnly = true)
    public List<UserStatsByCountryView> getUserStatsByCountry() {
        // Get all users with their countries
        List<Object[]> countryStats = userRepository.countUsersByCountry();

        // Group by continent
        Map<String, Map<String, Long>> continentMap = new HashMap<>();

        for (Object[] row : countryStats) {
            String countryCode = (String) row[0];
            Long count = (Long) row[1];

            if (countryCode == null || countryCode.isEmpty()) {
                countryCode = "UNKNOWN";
            }

            String continent = COUNTRY_TO_CONTINENT.getOrDefault(countryCode, "Unknown");

            continentMap
                .computeIfAbsent(continent, k -> new HashMap<>())
                .put(countryCode, count);
        }

        // Build the response
        return continentMap.entrySet().stream()
            .map(continentEntry -> {
                String continent = continentEntry.getKey();
                Map<String, Long> countries = continentEntry.getValue();

                int totalCount = countries.values().stream()
                    .mapToInt(Long::intValue)
                    .sum();

                List<CountryStatsView> countryList = countries.entrySet().stream()
                    .map(countryEntry -> CountryStatsView.builder()
                        .country(countryEntry.getKey())
                        .count(countryEntry.getValue().intValue())
                        .build())
                    .sorted((a, b) -> Integer.compare(b.getCount(), a.getCount()))
                    .collect(Collectors.toList());

                return UserStatsByCountryView.builder()
                    .continent(continent)
                    .count(totalCount)
                    .countries(countryList)
                    .build();
            })
            .sorted((a, b) -> Integer.compare(b.getCount(), a.getCount()))
            .collect(Collectors.toList());
    }
}
