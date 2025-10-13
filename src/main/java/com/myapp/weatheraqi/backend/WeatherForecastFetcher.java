package com.myapp.weatheraqi.backend;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class WeatherForecastFetcher extends DataFetcher {

    private static final String FORECAST_PARAMS = "weather_code,temperature_2m_max,apparent_temperature_max,apparent_temperature_min,temperature_2m_min,sunrise,sunset,daylight_duration,sunshine_duration,uv_index_max,rain_sum,precipitation_sum,wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant";

    @Override
    public void run() {
    }

    public JsonObject getForecastDataForCity(String cityName) throws Exception {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            return getForecastDataForCity(conn, cityName);
        }
    }

    public JsonObject getForecastDataForCity(Connection conn, String cityName) throws Exception {
        double[] latLon = getLatLon(conn, cityName);
        if (latLon == null) {
            throw new Exception("City not found: " + cityName);
        }

        String forecastUrl = String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&daily=%s&timezone=auto",
                latLon[0], latLon[1], FORECAST_PARAMS
        );

        String response = fetchAPIAsync(forecastUrl).join();

        JsonObject fullResponse = JsonParser.parseString(response).getAsJsonObject();
        if (!fullResponse.has("daily")) {
            throw new Exception("API response did not contain 'daily' forecast data.");
        }

        JsonObject result = new JsonObject();
        result.add("daily", fullResponse.getAsJsonObject("daily"));
        System.out.println("Forecast API Response for " + cityName + " received.");
        return result;
    }

    private double[] getLatLon(Connection conn, String cityName) throws SQLException {
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