package com.myapp.weatheraqi.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class HistoricalDataFetchHelper {
    
    private static final String DB_URL = "jdbc:mysql://localhost:3306/weather_aqi";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "pass";

    /**
     * Fetch historical data for a specific city and date
     */
    public static JsonObject fetchHistoricalData(String cityName, LocalDate date) {
        JsonObject result = new JsonObject();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            int cityId = getCityId(conn, cityName);
            if (cityId == -1) {
                return null;
            }

            String query = "SELECT * FROM historical_aqi WHERE city_id = ? AND date = ?";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setInt(1, cityId);
                ps.setDate(2, Date.valueOf(date));
                
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    JsonObject aqiData = new JsonObject();
                    aqiData.addProperty("pm25_max", rs.getDouble("pm25_max"));
                    aqiData.addProperty("pm10_max", rs.getDouble("pm10_max"));
                    aqiData.addProperty("co_max", rs.getDouble("co_max"));
                    aqiData.addProperty("no2_max", rs.getDouble("no2_max"));
                    aqiData.addProperty("so2_max", rs.getDouble("so2_max"));
                    aqiData.addProperty("o3_max", rs.getDouble("o3_max"));
                    
                    result.add("aqi", aqiData);
                    result.addProperty("time", date.toString());
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching historical data: " + e.getMessage());
            e.printStackTrace();
        }
        
        return result;
    }

    /**
     * Fetch last 7 days of AQI data for chart display
     */
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
}