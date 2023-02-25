package solution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;

record ExchangeRate(String rate, String date) {
    public ExchangeRate() {
        this("", "");
    }
}

public class Main {

    static String API_SECRET = "wOY2AwDtS4ma7NCkX4hu6A";
    static String MEASUREMENT_ID = "G-YZM2QSW9MJ";
    public static final String CURRENCY_RATE_ENDPOINT = "https://bank.gov.ua/NBUStatService/v1/statdirectory/exchange?date=%s&json";
    public static String GA_ENDPOINT = "https://www.google-analytics.com/mp/collect?measurement_id=%s&api_secret=%s"
            .formatted(MEASUREMENT_ID, API_SECRET);
    static String CLIENT_ID = "58641932.1608012265";
    public static final Long USD_CODE = 840L;
    public static final Long EUR_CODE = 978L;

    public static void main(String[] args) throws IOException, InterruptedException {

        LocalDate now = LocalDate.now();
        for (int i = 0; i < 365 * 2; i++) {
            LocalDate interestedDate = now.minusYears(2).plusDays(i);
            String dateParam = interestedDate.toString().replace("-", "");
            String response = getBankResponse(dateParam);
            var usdRate = extractCurrency(response, USD_CODE);
            String usdPayload = convertExchangeRateToPayload(usdRate, "USD");
            send(usdPayload);
            var eurRate = extractCurrency(response, EUR_CODE);
            String eurPayload = convertExchangeRateToPayload(eurRate, "EUR");
            send(eurPayload);
        }
    }

    private static String convertExchangeRateToPayload(ExchangeRate rate, String currency) {
        return """
                 {
                  "client_id": "%S",
                      "events": [{
                        "name": "uah_to_eur_exchange_rate",
                        "params": {
                          "currency": "%s",
                          "currency_rate": "%s",
                          "exchange_date": "%s"
                        }
                      }]
                  }
                """.formatted(CLIENT_ID, rate.rate(), rate.date());
    }

    private static ExchangeRate extractCurrency(String response, Long currency) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            TypeFactory typeFactory = mapper.getTypeFactory();
            List<Response> rate = mapper.readValue(response, typeFactory.constructCollectionType(List.class, Response.class));
            var usd = rate.stream().filter(r -> r.getR030() == currency).findFirst().get();
            return new ExchangeRate(String.valueOf(usd.getRate()), usd.getExchangedate());

        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return new ExchangeRate();
        }
    }

    private static String getBankResponse(String date) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(CURRENCY_RATE_ENDPOINT.formatted(date)))
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            return response.body();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return "NO RESPONSE";
    }

    private static void send(String payload) throws IOException, InterruptedException {
        System.out.println(HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(GA_ENDPOINT))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload)).build(),
                HttpResponse.BodyHandlers.ofInputStream()).statusCode());
    }
}
