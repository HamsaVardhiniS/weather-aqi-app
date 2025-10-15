package com.myapp.weatheraqi.backend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

public class CurrentDataFetcher extends DataFetcher {

    private static final String WEATHER_PARAMS = "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,rain,weather_code,pressure_msl,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m";
    private static final String AQI_PARAMS = "pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,ozone,sulphur_dioxide";

    @Override
    public void run() {
    }

    public static CompletableFuture<JsonObject> getCurrentDataForCityAsync(String cityName) {
        try {
            double[] latLon;
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
                latLon = getLatLon(conn, cityName);
            }
            
            if (latLon == null) {
                throw new Exception("City not found: " + cityName);
            }

            String weatherUrl = String.format(
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=%s&timezone=auto",
                    latLon[0], latLon[1], WEATHER_PARAMS);
            String aqiUrl = String.format(
                    "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f&current=%s&timezone=auto",
                    latLon[0], latLon[1], AQI_PARAMS);

            CompletableFuture<String> weatherFuture = fetchAPIAsync(weatherUrl);
            CompletableFuture<String> aqiFuture = fetchAPIAsync(aqiUrl);

            return weatherFuture.thenCombine(aqiFuture, (weatherResp, aqiResp) -> {
                JsonObject weatherCurrent = JsonParser.parseString(weatherResp).getAsJsonObject().getAsJsonObject("current");
                JsonObject aqiCurrent = JsonParser.parseString(aqiResp).getAsJsonObject().getAsJsonObject("current");
                
                JsonObject merged = new JsonObject();
                merged.add("weather", weatherCurrent);
                merged.add("aqi", aqiCurrent);
                return merged;
            });

        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static double[] getLatLon(Connection conn, String cityName) throws SQLException {
        String sql = "SELECT latitude, longitude FROM city WHERE city_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cityName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new double[]{rs.getDouble("latitude"), rs.getDouble("longitude")};
                }
            }
        }
        return null;
    }
}