package com.myapp.weatheraqi.backend;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class YesterdayDataFetcher extends DataFetcher {

    public static void main(String[] args) {
        new YesterdayDataFetcher().run();
    }

    @Override
    public void run() {
        //LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate yesterday = LocalDate.of(2025, 10, 10);


        System.out.println("Fetching data for: " + yesterday);

        int numberOfThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            List<CityInfo> cities = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT city_id, city_name, latitude, longitude FROM city")) {
                while (rs.next()) {
                    cities.add(new CityInfo(
                            rs.getInt("city_id"),
                            rs.getString("city_name"),
                            rs.getDouble("latitude"),
                            rs.getDouble("longitude")
                    ));
                }
            }

            for (CityInfo city : cities) {
                executor.submit(() -> {
                    try {
                        processCity(city, yesterday);
                    } catch (Exception e) {
                        System.err.println("Failed to process data for " + city.cityName);
                        e.printStackTrace();
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("All tasks submitted. Waiting for completion...");
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.MINUTES)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }
        System.out.println("Yesterday's data fetching process finished.");
    }

    private void processCity(CityInfo city, LocalDate date) throws Exception {
        System.out.println("Processing city: " + city.cityName);

        String weatherUrl = String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                        "&daily=weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min," +
                        "sunrise,sunset,daylight_duration,sunshine_duration,rain_sum,precipitation_sum,wind_speed_10m_max,wind_gusts_10m_max," +
                        "wind_direction_10m_dominant,temperature_2m_mean,apparent_temperature_mean" +
                        "&timezone=auto&start_date=%s&end_date=%s",
                city.latitude, city.longitude, date, date);

        String aqiUrl = String.format(
                "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f" +
                        "&hourly=pm2_5,pm10,carbon_monoxide,carbon_dioxide,nitrogen_dioxide,sulphur_dioxide,ozone,methane" +
                        "&timezone=auto&start_date=%s&end_date=%s",
                city.latitude, city.longitude, date, date);

        CompletableFuture<String> weatherFuture = fetchAPIAsync(weatherUrl);
        CompletableFuture<String> aqiFuture = fetchAPIAsync(aqiUrl);

        CompletableFuture.allOf(weatherFuture, aqiFuture).join();

        JsonObject weatherData = JsonParser.parseString(weatherFuture.get()).getAsJsonObject().getAsJsonObject("daily");
        JsonObject aqiData = JsonParser.parseString(aqiFuture.get()).getAsJsonObject().getAsJsonObject("hourly");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            insertWeatherData(conn, city.cityId, date, weatherData);
            insertAqiData(conn, city.cityId, date, aqiData);
            System.out.println("Successfully inserted data for " + city.cityName);
        }
    }

    private record CityInfo(int cityId, String cityName, double latitude, double longitude) {}

    private void insertWeatherData(Connection conn, int cityId, LocalDate date, JsonObject weather) throws SQLException {
        String sql = "INSERT INTO historical_weather VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE temperature_2m_max=VALUES(temperature_2m_max)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cityId);
            ps.setDate(2, Date.valueOf(date));
            ps.setInt(3, getInt(weather, "weather_code"));
            ps.setBigDecimal(4, getDecimal(weather, "temperature_2m_max"));
            ps.setBigDecimal(5, getDecimal(weather, "temperature_2m_min"));
            ps.setBigDecimal(6, getDecimal(weather, "apparent_temperature_max"));
            ps.setBigDecimal(7, getDecimal(weather, "apparent_temperature_min"));
            ps.setTime(8, Time.valueOf(getTime(weather, "sunrise")));
            ps.setTime(9, Time.valueOf(getTime(weather, "sunset")));
            ps.setInt(10, getInt(weather, "daylight_duration"));
            ps.setInt(11, getInt(weather, "sunshine_duration"));
            ps.setBigDecimal(12, getDecimal(weather, "wind_gusts_10m_max"));
            ps.setBigDecimal(13, getDecimal(weather, "wind_speed_10m_max"));
            ps.setBigDecimal(14, getDecimal(weather, "wind_direction_10m_dominant"));
            ps.setBigDecimal(15, getDecimal(weather, "rain_sum"));
            ps.setBigDecimal(16, getDecimal(weather, "precipitation_sum"));
            ps.setBigDecimal(17, getDecimal(weather, "temperature_2m_mean"));
            ps.setBigDecimal(18, getDecimal(weather, "apparent_temperature_mean"));
            ps.executeUpdate();
        }
    }

    private void insertAqiData(Connection conn, int cityId, LocalDate date, JsonObject aqi) throws SQLException {
        String sql = "INSERT INTO historical_aqi VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE pm25_max=VALUES(pm25_max)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cityId);
            ps.setDate(2, Date.valueOf(date));

            double[] pm25 = getMinMax(aqi.getAsJsonArray("pm2_5"));
            double[] pm10 = getMinMax(aqi.getAsJsonArray("pm10"));
            double[] co = getMinMax(aqi.getAsJsonArray("carbon_monoxide"));
            double[] no2 = getMinMax(aqi.getAsJsonArray("nitrogen_dioxide"));
            double[] so2 = getMinMax(aqi.getAsJsonArray("sulphur_dioxide"));
            double[] o3 = getMinMax(aqi.getAsJsonArray("ozone"));
            double[] co2 = getMinMax(aqi.getAsJsonArray("carbon_dioxide"));
            double[] ch4 = getMinMax(aqi.getAsJsonArray("methane"));

            setNullableDouble(ps, 3, pm25[0]); setNullableDouble(ps, 4, pm25[1]);
            setNullableDouble(ps, 5, pm10[0]); setNullableDouble(ps, 6, pm10[1]);
            setNullableDouble(ps, 7, co[0]);   setNullableDouble(ps, 8, co[1]);
            setNullableDouble(ps, 9, no2[0]);  setNullableDouble(ps, 10, no2[1]);
            setNullableDouble(ps, 11, so2[0]); setNullableDouble(ps, 12, so2[1]);
            setNullableDouble(ps, 13, o3[0]);  setNullableDouble(ps, 14, o3[1]);
            setNullableDouble(ps, 15, co2[0]); setNullableDouble(ps, 16, co2[1]);
            setNullableDouble(ps, 17, ch4[0]); setNullableDouble(ps, 18, ch4[1]);

            ps.executeUpdate();
        }
    }
    
    private double[] getMinMax(JsonArray arr) {
        if (arr == null) return new double[]{Double.NaN, Double.NaN};
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (int i = 0; i < arr.size(); i++) {
            if (!arr.get(i).isJsonNull()) {
                double val = arr.get(i).getAsDouble();
                min = Math.min(min, val);
                max = Math.max(max, val);
            }
        }
        return (min == Double.MAX_VALUE) ? new double[]{Double.NaN, Double.NaN} : new double[]{min, max};
    }

    private void setNullableDouble(PreparedStatement ps, int idx, double val) throws SQLException {
        if (Double.isNaN(val) || Double.isInfinite(val)) ps.setNull(idx, Types.DOUBLE);
        else ps.setDouble(idx, val);
    }

    private java.math.BigDecimal getDecimal(JsonObject obj, String key) {
        JsonArray arr = obj.getAsJsonArray(key);
        if (arr != null && arr.size() > 0 && !arr.get(0).isJsonNull()) {
            return arr.get(0).getAsBigDecimal();
        }
        return null;
    }

    private int getInt(JsonObject obj, String key) {
        JsonArray arr = obj.getAsJsonArray(key);
        return (arr != null && arr.size() > 0 && !arr.get(0).isJsonNull()) ? arr.get(0).getAsInt() : 0;
    }

    private String getTime(JsonObject obj, String key) {
        JsonArray arr = obj.getAsJsonArray(key);
        if (arr != null && arr.size() > 0 && !arr.get(0).isJsonNull()) {
            String dateTime = arr.get(0).getAsString();
            if (dateTime.length() >= 16) {
                return dateTime.substring(11, 16) + ":00";
            }
        }
        return "00:00:00";
    }
}