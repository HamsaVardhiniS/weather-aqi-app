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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class WeatherForecastFetcher {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/weather_aqi";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Vruksha@2014";

    private static final String FORECAST_PARAMS =
            "weather_code,temperature_2m_max,apparent_temperature_max,apparent_temperature_min," +
            "temperature_2m_min,sunrise,sunset,daylight_duration,sunshine_duration," +
            "uv_index_max,rain_sum,precipitation_sum,wind_speed_10m_max," +
            "wind_gusts_10m_max,wind_direction_10m_dominant";

    public static void main(String[] args) {
        String cityName = "Jaipur"; // Example input
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            JsonObject forecastData = getForecastDataForCity(conn, cityName);
            System.out.println("Forecast Data for " + cityName + ":\n" + forecastData);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static JsonObject getForecastDataForCity(Connection conn, String cityName) throws Exception {
        double[] latLon = getLatLon(conn, cityName);
        if (latLon == null) throw new Exception("City not found: " + cityName);

        double lat = latLon[0], lon = latLon[1];

        String forecastUrl = String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&daily=%s&timezone=auto",
                lat, lon, FORECAST_PARAMS
        );

        String response = fetchAPI(forecastUrl);

        JsonObject dailyData = JsonParser.parseString(response)
                .getAsJsonObject()
                .getAsJsonObject("daily");

        JsonObject result = new JsonObject();
        result.add("forecast", dailyData);

        return result;
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
}
