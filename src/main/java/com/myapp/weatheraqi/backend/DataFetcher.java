package com.myapp.weatheraqi.backend;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public abstract class DataFetcher {
    protected static final String DB_URL = "jdbc:mysql://localhost:3306/weather_aqi";
    protected static final String DB_USER = "root";
    protected static final String DB_PASS = "Ritujaa@2006";

    protected static final HttpClient client = HttpClient.newHttpClient();
    public abstract void run();

    protected static CompletableFuture<String> fetchAPIAsync(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
                return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Failed to fetch API: " + url, e);
            }
        });
    }
}