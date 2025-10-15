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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class HistoricalDataFetchHelper {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/weather_aqi";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Ritujaa@2006";

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

    public static JsonObject fetchLast7DaysAqi(String cityName) {
        JsonObject result = new JsonObject();
        JsonArray dates = new JsonArray();
        JsonArray pm25Data = new JsonArray();
        JsonArray pm10Data = new JsonArray();
        JsonArray coData = new JsonArray();
        JsonArray no2Data = new JsonArray();
        JsonArray so2Data = new JsonArray();
        JsonArray o3Data = new JsonArray();

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            int cityId = getCityId(conn, cityName);
            if (cityId == -1) {
                System.err.println("City not found: " + cityName);
                return result;
            }

            String aqiQuery = "SELECT date, pm25_max, pm10_max, co_max, no2_max, so2_max, o3_max " +
                    "FROM historical_aqi " +
                    "WHERE city_id = ? " +
                    "ORDER BY date DESC " +
                    "LIMIT 7";

            try (PreparedStatement ps = conn.prepareStatement(aqiQuery)) {
                ps.setInt(1, cityId);
                ResultSet rs = ps.executeQuery();

                List<DayData> dataList = new ArrayList<>();
                while (rs.next()) {
                    DayData day = new DayData();
                    day.date = rs.getDate("date").toLocalDate();
                    day.pm25 = rs.getDouble("pm25_max");
                    day.pm10 = rs.getDouble("pm10_max");
                    day.co = rs.getDouble("co_max");
                    day.no2 = rs.getDouble("no2_max");
                    day.so2 = rs.getDouble("so2_max");
                    day.o3 = rs.getDouble("o3_max");
                    dataList.add(day);
                }

                // Reverse for chronological order
                for (int i = dataList.size() - 1; i >= 0; i--) {
                    DayData day = dataList.get(i);
                    
                    String dayName = day.date.getDayOfWeek().toString().substring(0, 3);
                    dayName = dayName.substring(0, 1).toUpperCase() + dayName.substring(1).toLowerCase();
                    dates.add(dayName);
                    
                    pm25Data.add(Math.round(day.pm25 * 10) / 10.0);
                    pm10Data.add(Math.round(day.pm10 * 10) / 10.0);
                    coData.add(Math.round(day.co * 10) / 10.0);
                    no2Data.add(Math.round(day.no2 * 10) / 10.0);
                    so2Data.add(Math.round(day.so2 * 10) / 10.0);
                    o3Data.add(Math.round(day.o3 * 10) / 10.0);
                }
            }

            result.add("dates", dates);
            result.add("pm25", pm25Data);
            result.add("pm10", pm10Data);
            result.add("co", coData);
            result.add("no2", no2Data);
            result.add("so2", so2Data);
            result.add("o3", o3Data);

        } catch (SQLException e) {
            System.err.println("Error fetching 7-day AQI data: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    private static int getCityId(Connection conn, String cityName) throws SQLException {
        String cityQuery = "SELECT city_id FROM city WHERE city_name = ?";
        try (PreparedStatement ps = conn.prepareStatement(cityQuery)) {
            ps.setString(1, cityName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("city_id");
            }
        }
        return -1;
    }

    private static class DayData {
        LocalDate date;
        double pm25, pm10, co, no2, so2, o3;
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