package com.myapp.weatheraqi.backend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class CurrentDataFetcher {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/weather_aqi";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Vruksha@2014";

    private static final String WEATHER_PARAMS = "temperature_2m,relative_humidity_2m,apparent_temperature,is_day," +
            "precipitation,rain,weather_code,pressure_msl,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m";

    private static final String AQI_PARAMS = "pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,ozone,sulphur_dioxide";

    public static void main(String[] args) {
        String cityName = "Chennai"; // Example input from frontend

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            JsonObject currentData = getCurrentDataForCity(conn, cityName);
            System.out.println("Current Data for " + cityName + ":\n" + currentData);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static JsonObject getCurrentDataForCity(Connection conn, String cityName) throws Exception {
        double[] latLon = getLatLon(conn, cityName);
        if (latLon == null) throw new Exception("City not found: " + cityName);

        double lat = latLon[0], lon = latLon[1];

        String weatherUrl = String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=%s&timezone=auto",
                lat, lon, WEATHER_PARAMS
        );
        String aqiUrl = String.format(
                "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f&current=%s&timezone=auto",
                lat, lon, AQI_PARAMS
        );

        String weatherResp = fetchAPI(weatherUrl);
        String aqiResp = fetchAPI(aqiUrl);

        JsonObject weatherCurrent = JsonParser.parseString(weatherResp)
                .getAsJsonObject().getAsJsonObject("current");
        JsonObject aqiCurrent = JsonParser.parseString(aqiResp)
                .getAsJsonObject().getAsJsonObject("current");

        JsonObject merged = new JsonObject();
        merged.add("weather", weatherCurrent);
        merged.add("aqi", aqiCurrent);

        return merged;
    }

    private static double[] getLatLon(Connection conn, String cityName) throws SQLException {
        String sql = "SELECT latitude, longitude FROM city WHERE city_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cityName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new double[]{rs.getDouble("latitude"), rs.getDouble("longitude")};
            }
        }
        return null;
    }

    private static String fetchAPI(String url) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private static double getSafeValue(JsonElement el) {
        return (el == null || el.isJsonNull()) ? Double.NaN : el.getAsDouble();
    }
}
