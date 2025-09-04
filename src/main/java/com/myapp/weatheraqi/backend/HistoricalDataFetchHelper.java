package com.myapp.weatheraqi.backend;

import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;

public class HistoricalDataFetchHelper {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/weather_aqi";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Vruksha@2014";

    public static JsonObject fetchHistoricalData(String city, LocalDate date) throws Exception {
        JsonObject result = new JsonObject();

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {

            int cityId;
            try (PreparedStatement ps = conn.prepareStatement("SELECT city_id FROM city WHERE city_name=?")) {
                ps.setString(1, city);
                ResultSet rs = ps.executeQuery();
                if (!rs.next()) throw new Exception("City not found: " + city);
                cityId = rs.getInt("city_id");
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM historical_weather WHERE city_id=? AND date=?"
            )) {
                ps.setInt(1, cityId);
                ps.setDate(2, Date.valueOf(date));
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    JsonObject weather = new JsonObject();
                    weather.addProperty("temperature_2m_mean", rs.getDouble("temperature_2m_mean"));
                    weather.addProperty("temperature_2m_max", rs.getDouble("temperature_2m_max"));
                    weather.addProperty("temperature_2m_min", rs.getDouble("temperature_2m_min"));
                    weather.addProperty("apparent_temperature_mean", rs.getDouble("apparent_temperature_mean"));
                    weather.addProperty("apparent_temperature_max", rs.getDouble("apparent_temperature_max"));
                    weather.addProperty("apparent_temperature_min", rs.getDouble("apparent_temperature_min"));
                    weather.addProperty("wind_speed_10m_max", rs.getDouble("wind_speed_10m_max"));
                    weather.addProperty("wind_gusts_10m_max", rs.getDouble("wind_gusts_10m_max"));
                    weather.addProperty("sunrise", rs.getTime("sunrise").toString());
                    weather.addProperty("sunset", rs.getTime("sunset").toString());
                    weather.addProperty("daylight_duration", rs.getInt("daylight_duration"));
                    weather.addProperty("sunshine_duration", rs.getInt("sunshine_duration"));
                    weather.addProperty("rain_sum", rs.getDouble("rain_sum"));
                    weather.addProperty("precipitation_sum", rs.getDouble("precipitation_sum"));
                    result.add("weather", weather);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM historical_aqi WHERE city_id=? AND date=?"
            )) {
                ps.setInt(1, cityId);
                ps.setDate(2, Date.valueOf(date));
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    JsonObject aqi = new JsonObject();
                    aqi.addProperty("pm25_min", rs.getDouble("pm25_min"));
                    aqi.addProperty("pm25_max", rs.getDouble("pm25_max"));
                    aqi.addProperty("pm10_min", rs.getDouble("pm10_min"));
                    aqi.addProperty("pm10_max", rs.getDouble("pm10_max"));
                    aqi.addProperty("co_min", rs.getDouble("co_min"));
                    aqi.addProperty("co_max", rs.getDouble("co_max"));
                    aqi.addProperty("no2_min", rs.getDouble("no2_min"));
                    aqi.addProperty("no2_max", rs.getDouble("no2_max"));
                    aqi.addProperty("so2_min", rs.getDouble("so2_min"));
                    aqi.addProperty("so2_max", rs.getDouble("so2_max"));
                    aqi.addProperty("o3_min", rs.getDouble("o3_min"));
                    aqi.addProperty("o3_max", rs.getDouble("o3_max"));
                    aqi.addProperty("co2_min", rs.getDouble("co2_min"));
                    aqi.addProperty("co2_max", rs.getDouble("co2_max"));
                    aqi.addProperty("ch4_min", rs.getDouble("ch4_min"));
                    aqi.addProperty("ch4_max", rs.getDouble("ch4_max"));
                    result.add("aqi", aqi);
                }
            }
        }

        return result;
    }
}
