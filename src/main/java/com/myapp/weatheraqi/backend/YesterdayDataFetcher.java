package com.myapp.weatheraqi.backend;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class YesterdayDataFetcher {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/weather_aqi";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Vruksha@2014";

    public static void main(String[] args) {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            Statement stmt = conn.createStatement();
            ResultSet cities = stmt.executeQuery("SELECT city_id, latitude, longitude FROM city");

            while (cities.next()) {
                int cityId = cities.getInt("city_id");
                double lat = cities.getDouble("latitude");
                double lon = cities.getDouble("longitude");

                // Weather API
                String weatherUrl = String.format(
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                                "&daily=weather_code,temperature_2m_max,temperature_2m_min,apparent_temperature_max,apparent_temperature_min," +
                                "sunrise,sunset,daylight_duration,sunshine_duration,rain_sum,precipitation_sum,wind_speed_10m_max,wind_gusts_10m_max," +
                                "wind_direction_10m_dominant,temperature_2m_mean,apparent_temperature_mean" +
                                "&timezone=auto&start_date=%s&end_date=%s",
                        lat, lon, yesterday, yesterday
                );

                // AQI API
                String aqiUrl = String.format(
                        "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f" +
                                "&hourly=pm2_5,pm10,carbon_monoxide,carbon_dioxide,nitrogen_dioxide,sulphur_dioxide,ozone,methane" +
                                "&timezone=auto&start_date=%s&end_date=%s",
                        lat, lon, yesterday, yesterday
                );

                JsonObject weatherData = JsonParser.parseString(fetchAPI(weatherUrl))
                        .getAsJsonObject().getAsJsonObject("daily");
                JsonObject aqiData = JsonParser.parseString(fetchAPI(aqiUrl))
                        .getAsJsonObject().getAsJsonObject("hourly");

                insertWeatherData(conn, cityId, yesterday, weatherData);
                insertAqiData(conn, cityId, yesterday, aqiData);
            }

            System.out.println("✅ Yesterday's weather + AQI inserted successfully!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================= WEATHER =================
    private static void insertWeatherData(Connection conn, int cityId, LocalDate date, JsonObject weather) throws SQLException {
        String sql = "INSERT INTO historical_weather VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
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

    // ================= AQI =================
    private static void insertAqiData(Connection conn, int cityId, LocalDate date, JsonObject aqi) throws SQLException {
        String sql = "INSERT INTO historical_aqi VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
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

    private static String fetchAPI(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private static double[] getMinMax(JsonArray arr) {
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

    private static void setNullableDouble(PreparedStatement ps, int idx, double val) throws SQLException {
        if (Double.isNaN(val) || Double.isInfinite(val)) ps.setNull(idx, Types.DOUBLE);
        else ps.setDouble(idx, val);
    }

    private static java.math.BigDecimal getDecimal(JsonObject obj, String key) {
        JsonArray arr = obj.getAsJsonArray(key);
        if (arr != null && arr.size() > 0 && !arr.get(0).isJsonNull()) {
            return arr.get(0).getAsBigDecimal();
        }
        return null;
    }

    private static int getInt(JsonObject obj, String key) {
        JsonArray arr = obj.getAsJsonArray(key);
        return (arr != null && arr.size() > 0 && !arr.get(0).isJsonNull()) ? arr.get(0).getAsInt() : 0;
    }

    private static String getTime(JsonObject obj, String key) {
        JsonArray arr = obj.getAsJsonArray(key);
        if (arr != null && arr.size() > 0 && !arr.get(0).isJsonNull()) {
            return arr.get(0).getAsString().substring(11) + ":00";
        }
        return "00:00:00";
    }
}
