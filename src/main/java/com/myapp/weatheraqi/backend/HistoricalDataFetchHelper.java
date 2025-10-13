package com.myapp.weatheraqi.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class HistoricalDataFetchHelper {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/weather_aqi";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Vruksha@2014";

    public static JsonObject fetchHistoricalData(String city, LocalDate date) throws Exception {
        return fetchHistoricalData(city, date, date);
    }

    public static JsonObject fetchHistoricalData(String city, LocalDate startDate, LocalDate endDate) throws Exception {
        JsonObject result = new JsonObject();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {

            int cityId = getCityId(conn, city);
            if (cityId == -1) {
                throw new Exception("City not found: " + city);
            }

            CompletableFuture<JsonArray> weatherFuture = CompletableFuture.supplyAsync(() ->
                fetchWeatherRange(conn, cityId, startDate, endDate)
            );
            CompletableFuture<JsonArray> aqiFuture = CompletableFuture.supplyAsync(() ->
                fetchAqiRange(conn, cityId, startDate, endDate)
            );

            CompletableFuture.allOf(weatherFuture, aqiFuture).join();

            JsonArray weatherArray = weatherFuture.get();
            JsonArray aqiArray = aqiFuture.get();
            
            if (startDate.equals(endDate)) {
                if (weatherArray.size() > 0) {
                    result.add("weather", weatherArray.get(0).getAsJsonObject());
                }
                if (aqiArray.size() > 0) {
                    result.add("aqi", aqiArray.get(0).getAsJsonObject());
                }
            } else {
                result.add("weather", weatherArray);
                result.add("aqi", aqiArray);
            }

        } catch (InterruptedException | ExecutionException e) {
            throw new Exception("Failed to fetch historical data in parallel", e);
        }
        return result;
    }

    private static int getCityId(Connection conn, String city) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT city_id FROM city WHERE city_name=?")) {
            ps.setString(1, city);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt("city_id") : -1;
        }
    }

    private static JsonArray fetchWeatherRange(Connection conn, int cityId, LocalDate startDate, LocalDate endDate) {
        JsonArray weatherArray = new JsonArray();
        String sql = "SELECT * FROM historical_weather WHERE city_id=? AND date BETWEEN ? AND ? ORDER BY date";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cityId);
            ps.setDate(2, Date.valueOf(startDate));
            ps.setDate(3, Date.valueOf(endDate));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JsonObject weather = new JsonObject();
                weather.addProperty("date", rs.getDate("date").toString());
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
                weatherArray.add(weather);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error fetching weather data", e);
        }
        return weatherArray;
    }

    private static JsonArray fetchAqiRange(Connection conn, int cityId, LocalDate startDate, LocalDate endDate) {
        JsonArray aqiArray = new JsonArray();
        String sql = "SELECT * FROM historical_aqi WHERE city_id=? AND date BETWEEN ? AND ? ORDER BY date";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cityId);
            ps.setDate(2, Date.valueOf(startDate));
            ps.setDate(3, Date.valueOf(endDate));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                JsonObject aqi = new JsonObject();
                aqi.addProperty("date", rs.getDate("date").toString());
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
                aqiArray.add(aqi);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error fetching AQI data", e);
        }
        return aqiArray;
    }
}