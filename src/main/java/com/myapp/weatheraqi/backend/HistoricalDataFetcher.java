package com.myapp.weatheraqi.backend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HistoricalDataFetcher extends DataFetcher {

    public static void main(String[] args) {
        new HistoricalDataFetcher().run();
    }

    @Override
    public void run() {
        int numberOfThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        LocalDate start = LocalDate.of(2025, 6, 1);
        LocalDate end = LocalDate.of(2025, 8, 28);

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            List<CityInfo> cities = getCities(conn);
            for (CityInfo city : cities) {
                executor.submit(() -> {
                    try {
                        processCity(city, start, end);
                    } catch (Exception e) {
                        System.err.println("Failed to process historical data for " + city.cityName);
                        e.printStackTrace();
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            shutdownExecutor(executor);
        }
        System.out.println("Historical data ingestion process finished.");
    }

    private void processCity(CityInfo city, LocalDate start, LocalDate end) throws Exception {
        System.out.println("Processing historical data for city: " + city.cityName);

        String weatherUrl = String.format("https://archive-api.open-meteo.com/v1/archive?latitude=%.4f&longitude=%.4f&start_date=%s&end_date=%s&daily=weather_code,temperature_2m_mean,temperature_2m_max,temperature_2m_min,apparent_temperature_mean,apparent_temperature_max,apparent_temperature_min,wind_gusts_10m_max,wind_speed_10m_max,wind_direction_10m_dominant,sunrise,sunset,daylight_duration,sunshine_duration,precipitation_sum,rain_sum&timezone=auto", city.latitude, city.longitude, start, end);
        String aqiUrl = String.format("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f&hourly=pm10,pm2_5,carbon_dioxide,carbon_monoxide,nitrogen_dioxide,ozone,sulphur_dioxide,methane&start_date=%s&end_date=%s&timezone=auto", city.latitude, city.longitude, start, end);

        CompletableFuture<String> weatherFuture = fetchAPIAsync(weatherUrl);
        CompletableFuture<String> aqiFuture = fetchAPIAsync(aqiUrl);
        CompletableFuture.allOf(weatherFuture, aqiFuture).join();

        JsonObject weatherData = JsonParser.parseString(weatherFuture.get()).getAsJsonObject().getAsJsonObject("daily");
        JsonObject aqiData = JsonParser.parseString(aqiFuture.get()).getAsJsonObject().getAsJsonObject("hourly");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            insertWeatherData(conn, city.cityId, weatherData);
            insertAqiData(conn, city.cityId, weatherData, aqiData);
            System.out.println("Successfully inserted historical data for " + city.cityName);
        }
    }

    private List<CityInfo> getCities(Connection conn) throws SQLException {
        List<CityInfo> cities = new ArrayList<>();
        try (Statement cityStmt = conn.createStatement();
             ResultSet rs = cityStmt.executeQuery("SELECT city_id, city_name, latitude, longitude FROM city")) {
            while (rs.next()) {
                cities.add(new CityInfo(rs.getInt("city_id"), rs.getString("city_name"), rs.getDouble("latitude"), rs.getDouble("longitude")));
            }
        }
        return cities;
    }

    private void shutdownExecutor(ExecutorService executor) {
        System.out.println("All historical tasks submitted. Shutting down executor.");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.MINUTES)) {
                System.err.println("Tasks did not complete in 60 minutes. Forcing shutdown.");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    private record CityInfo(int cityId, String cityName, double latitude, double longitude) {}

    private void insertWeatherData(Connection conn, int cityId, JsonObject weatherData) throws SQLException {
        String sql = "INSERT INTO historical_weather (city_id, date, weather_code, temperature_2m_max, temperature_2m_min, apparent_temperature_max, apparent_temperature_min, sunrise, sunset, daylight_duration, sunshine_duration, wind_gusts_10m_max, wind_speed_10m_max, wind_direction_10m_dominant, rain_sum, precipitation_sum, temperature_2m_mean, apparent_temperature_mean) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            JsonArray dates = weatherData.getAsJsonArray("time");
            for (int i = 0; i < dates.size(); i++) {
                ps.setInt(1, cityId);
                ps.setDate(2, Date.valueOf(LocalDate.parse(dates.get(i).getAsString())));
                ps.setInt(3, weatherData.getAsJsonArray("weather_code").get(i).getAsInt());
                ps.setBigDecimal(4, getNullableBigDecimal(weatherData.getAsJsonArray("temperature_2m_max"), i));
                ps.setBigDecimal(5, getNullableBigDecimal(weatherData.getAsJsonArray("temperature_2m_min"), i));
                ps.setBigDecimal(6, getNullableBigDecimal(weatherData.getAsJsonArray("apparent_temperature_max"), i));
                ps.setBigDecimal(7, getNullableBigDecimal(weatherData.getAsJsonArray("apparent_temperature_min"), i));
                ps.setTime(8, Time.valueOf(safeSubstring(weatherData.getAsJsonArray("sunrise").get(i).getAsString(), 11) + ":00"));
                ps.setTime(9, Time.valueOf(safeSubstring(weatherData.getAsJsonArray("sunset").get(i).getAsString(), 11) + ":00"));
                ps.setInt(10, weatherData.getAsJsonArray("daylight_duration").get(i).getAsInt());
                ps.setInt(11, weatherData.getAsJsonArray("sunshine_duration").get(i).getAsInt());
                ps.setBigDecimal(12, getNullableBigDecimal(weatherData.getAsJsonArray("wind_gusts_10m_max"), i));
                ps.setBigDecimal(13, getNullableBigDecimal(weatherData.getAsJsonArray("wind_speed_10m_max"), i));
                ps.setBigDecimal(14, getNullableBigDecimal(weatherData.getAsJsonArray("wind_direction_10m_dominant"), i));
                ps.setBigDecimal(15, getNullableBigDecimal(weatherData.getAsJsonArray("rain_sum"), i));
                ps.setBigDecimal(16, getNullableBigDecimal(weatherData.getAsJsonArray("precipitation_sum"), i));
                ps.setBigDecimal(17, getNullableBigDecimal(weatherData.getAsJsonArray("temperature_2m_mean"), i));
                ps.setBigDecimal(18, getNullableBigDecimal(weatherData.getAsJsonArray("apparent_temperature_mean"), i));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
    
    private void insertAqiData(Connection conn, int cityId, JsonObject weatherData, JsonObject aqiData) throws SQLException {
        String sql = "INSERT INTO historical_aqi (city_id, date, pm25_min, pm25_max, pm10_min, pm10_max, co_min, co_max, no2_min, no2_max, so2_min, so2_max, o3_min, o3_max, co2_min, co2_max, ch4_min, ch4_max) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            JsonArray dates = weatherData.getAsJsonArray("time");
            // These values are daily summaries but the API gives them hourly, so we calculate the min/max once.
            double[] pm25 = getMinMax(aqiData.getAsJsonArray("pm2_5"));
            double[] pm10 = getMinMax(aqiData.getAsJsonArray("pm10"));
            double[] co = getMinMax(aqiData.getAsJsonArray("carbon_monoxide"));
            double[] no2 = getMinMax(aqiData.getAsJsonArray("nitrogen_dioxide"));
            double[] so2 = getMinMax(aqiData.getAsJsonArray("sulphur_dioxide"));
            double[] o3 = getMinMax(aqiData.getAsJsonArray("ozone"));
            double[] co2 = getMinMax(aqiData.getAsJsonArray("carbon_dioxide"));
            double[] ch4 = getMinMax(aqiData.getAsJsonArray("methane"));

            for (int i = 0; i < dates.size(); i++) {
                ps.setInt(1, cityId);
                ps.setDate(2, Date.valueOf(LocalDate.parse(dates.get(i).getAsString())));
                setNullableDouble(ps, 3, pm25[0]); setNullableDouble(ps, 4, pm25[1]);
                setNullableDouble(ps, 5, pm10[0]); setNullableDouble(ps, 6, pm10[1]);
                setNullableDouble(ps, 7, co[0]);   setNullableDouble(ps, 8, co[1]);
                setNullableDouble(ps, 9, no2[0]);  setNullableDouble(ps, 10, no2[1]);
                setNullableDouble(ps, 11, so2[0]); setNullableDouble(ps, 12, so2[1]);
                setNullableDouble(ps, 13, o3[0]);  setNullableDouble(ps, 14, o3[1]);
                setNullableDouble(ps, 15, co2[0]); setNullableDouble(ps, 16, co2[1]);
                setNullableDouble(ps, 17, ch4[0]); setNullableDouble(ps, 18, ch4[1]);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void setNullableDouble(PreparedStatement ps, int index, double value) throws SQLException {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            ps.setNull(index, Types.DOUBLE);
        } else {
            ps.setDouble(index, value);
        }
    }
    
    private java.math.BigDecimal getNullableBigDecimal(JsonArray arr, int i) {
        return (arr != null && !arr.get(i).isJsonNull()) ? arr.get(i).getAsBigDecimal() : null;
    }

    private String safeSubstring(String str, int start) {
        return (str != null && str.length() > start) ? str.substring(start) : "00:00";
    }

    private double[] getMinMax(JsonArray arr) {
        if (arr == null) return new double[]{Double.NaN, Double.NaN};
        double min = Double.MAX_VALUE;
        double max = Double.NEGATIVE_INFINITY;
        for (JsonElement element : arr) {
            if (!element.isJsonNull()) {
                double val = element.getAsDouble();
                if (val < min) min = val;
                if (val > max) max = val;
            }
        }
        return (min == Double.MAX_VALUE) ? new double[]{Double.NaN, Double.NaN} : new double[]{min, max};
    }
}