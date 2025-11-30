package com.orbvpn.api.utils;

import com.google.gson.Gson;
import com.orbvpn.api.domain.payload.CoinPrice;
import com.orbvpn.api.domain.payload.FiatConverted;
import com.orbvpn.api.domain.payload.FreaksResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.logging.Logger;

@Component
public class ThirdAPIUtils {

    private static final Logger logger = Logger.getLogger(ThirdAPIUtils.class.getName());
    private final WebClient binanceAPI;
    private final WebClient xchangeAPI;
    private static final Gson gson = new Gson();

    public ThirdAPIUtils(WebClient.Builder webClientBuilder) {
        this.binanceAPI = webClientBuilder
                .baseUrl("https://api.binance.com/api/v3")
                .build();
        this.xchangeAPI = webClientBuilder
                .baseUrl("https://api.exchangerate.host")
                .build();
    }

    public double getCryptoPriceBySymbol(String symbol) {
        if ("USDC".equals(symbol)) {
            return 1.0;
        }

        String symbolPair = "USDT".equals(symbol) ? "USDCUSDT" : symbol + "USDC";

        return binanceAPI.get()
                .uri(uriBuilder -> uriBuilder.path("/ticker/price")
                        .queryParam("symbol", symbolPair.toUpperCase())
                        .build())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError,
                        response -> Mono.error(new RuntimeException("4xx error: " + response.statusCode())))
                .bodyToMono(CoinPrice.class)
                .map(coinPrice -> Double.parseDouble(coinPrice.getPrice()))
                .onErrorResume(e -> {
                    logger.warning("Error fetching crypto price: " + e.getMessage());
                    return Mono.just(0.0);
                })
                .block();
    }

    public double currencyConvert(String from, String to, double amount) {
        return xchangeAPI.get()
                .uri(uriBuilder -> uriBuilder.path("/convert")
                        .queryParam("from", from)
                        .queryParam("to", to)
                        .queryParam("amount", amount)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> gson.fromJson(response, FiatConverted.class).getResult())
                .onErrorResume(e -> {
                    logger.warning("Error converting currency: " + e.getMessage());
                    return Mono.just(0.0);
                })
                .block();
    }

    public double getCurrencyRate(String from) {
        return xchangeAPI.get()
                .uri(uriBuilder -> uriBuilder.path("/latest")
                        .queryParam("symbols", from)
                        .queryParam("base", "USD")
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    var rates = gson.fromJson(response, FreaksResponse.class);
                    String rateString = rates.getRates().get(from);
                    return Double.parseDouble(rateString);
                })
                .onErrorResume(e -> {
                    logger.warning("Error fetching currency rate: " + e.getMessage());
                    return Mono.just(0.0);
                })
                .block();
    }
}