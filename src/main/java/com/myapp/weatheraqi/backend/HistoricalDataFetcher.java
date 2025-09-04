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

public class HistoricalDataFetcher {

    private static void setNullableDouble(PreparedStatement ps, int index, double value) throws SQLException {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            ps.setNull(index, Types.DOUBLE);
        } else {
            ps.setDouble(index, value);
        }
    }

    private static java.math.BigDecimal getNullableBigDecimal(JsonArray arr, int i) {
        return arr.get(i).isJsonNull() ? null : arr.get(i).getAsBigDecimal();
    }

    private static String safeSubstring(String str, int start) {
        if (str == null || str.length() < start) return "00:00";
        return str.substring(start);
    }

    private static double[] getMinMax(JsonArray arr) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int i = 0; i < arr.size(); i++) {
            if (!arr.get(i).isJsonNull()) {
                double val = arr.get(i).getAsDouble();
                min = Math.min(min, val);
                max = Math.max(max, val);
            }
        }
        if (min == Double.MAX_VALUE || max == -Double.MAX_VALUE) {
            return new double[]{Double.NaN, Double.NaN};
        }
        return new double[]{min, max};
    }

    private static String fetchAPI(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    static final String DB_URL = "jdbc:mysql://localhost:3306/weather_aqi";
    static final String DB_USER = "root";
    static final String DB_PASS = "Vruksha@2014";

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            LocalDate start = LocalDate.of(2025, 6, 1);
            LocalDate end = LocalDate.of(2025, 8, 28);

            Statement cityStmt = conn.createStatement();
            ResultSet cities = cityStmt.executeQuery("SELECT city_id, city_name, latitude, longitude FROM city");

            while (cities.next()) {
                int cityId = cities.getInt("city_id");
                String cityName = cities.getString("city_name");
                double latitude = cities.getDouble("latitude");
                double longitude = cities.getDouble("longitude");

                System.out.println("Fetching data for city: " + cityName);

                String weatherUrl = String.format(
                        "https://archive-api.open-meteo.com/v1/archive?latitude=%.4f&longitude=%.4f" +
                                "&start_date=%s&end_date=%s&daily=weather_code,temperature_2m_mean,temperature_2m_max," +
                                "temperature_2m_min,apparent_temperature_mean,apparent_temperature_max,apparent_temperature_min," +
                                "wind_gusts_10m_max,wind_speed_10m_max,wind_direction_10m_dominant,sunrise,sunset," +
                                "daylight_duration,sunshine_duration,precipitation_sum,rain_sum&timezone=auto",
                        latitude, longitude, start, end
                );

                String aqiUrl = String.format(
                        "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.4f&longitude=%.4f" +
                                "&hourly=pm10,pm2_5,carbon_dioxide,carbon_monoxide,nitrogen_dioxide,ozone,sulphur_dioxide,methane" +
                                "&start_date=%s&end_date=%s&timezone=auto",
                        latitude, longitude, start, end
                );

                JsonObject weatherData = JsonParser.parseString(fetchAPI(weatherUrl))
                        .getAsJsonObject().getAsJsonObject("daily");

                JsonObject aqiData = JsonParser.parseString(fetchAPI(aqiUrl))
                        .getAsJsonObject().getAsJsonObject("hourly");

                // -------------------------
                // Insert Weather Data
                // -------------------------
                String weatherSQL = "INSERT INTO historical_weather " +
                        "(city_id, date, weather_code, temperature_2m_max, temperature_2m_min, " +
                        "apparent_temperature_max, apparent_temperature_min, sunrise, sunset, " +
                        "daylight_duration, sunshine_duration, wind_gusts_10m_max, wind_speed_10m_max, " +
                        "wind_direction_10m_dominant, rain_sum, precipitation_sum, " +
                        "temperature_2m_mean, apparent_temperature_mean) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

                try (PreparedStatement ps = conn.prepareStatement(weatherSQL)) {
                    JsonArray dates = weatherData.getAsJsonArray("time");

                    for (int i = 0; i < dates.size(); i++) {
                        LocalDate date = LocalDate.parse(dates.get(i).getAsString());

                        ps.setInt(1, cityId);
                        ps.setDate(2, Date.valueOf(date));
                        ps.setInt(3, weatherData.getAsJsonArray("weather_code").get(i).getAsInt());
                        ps.setBigDecimal(4, getNullableBigDecimal(weatherData.getAsJsonArray("temperature_2m_max"), i));
                        ps.setBigDecimal(5, getNullableBigDecimal(weatherData.getAsJsonArray("temperature_2m_min"), i));
                        ps.setBigDecimal(6, getNullableBigDecimal(weatherData.getAsJsonArray("apparent_temperature_max"), i));
                        ps.setBigDecimal(7, getNullableBigDecimal(weatherData.getAsJsonArray("apparent_temperature_min"), i));

                        String sunrise = safeSubstring(weatherData.getAsJsonArray("sunrise").get(i).getAsString(), 11) + ":00";
                        String sunset = safeSubstring(weatherData.getAsJsonArray("sunset").get(i).getAsString(), 11) + ":00";
                        ps.setTime(8, Time.valueOf(sunrise));
                        ps.setTime(9, Time.valueOf(sunset));

                        ps.setInt(10, weatherData.getAsJsonArray("daylight_duration").get(i).getAsInt());
                        ps.setInt(11, weatherData.getAsJsonArray("sunshine_duration").get(i).getAsInt());
                        ps.setBigDecimal(12, getNullableBigDecimal(weatherData.getAsJsonArray("wind_gusts_10m_max"), i));
                        ps.setBigDecimal(13, getNullableBigDecimal(weatherData.getAsJsonArray("wind_speed_10m_max"), i));
                        ps.setBigDecimal(14, getNullableBigDecimal(weatherData.getAsJsonArray("wind_direction_10m_dominant"), i));
                        ps.setBigDecimal(15, getNullableBigDecimal(weatherData.getAsJsonArray("rain_sum"), i));
                        ps.setBigDecimal(16, getNullableBigDecimal(weatherData.getAsJsonArray("precipitation_sum"), i));
                        ps.setBigDecimal(17, getNullableBigDecimal(weatherData.getAsJsonArray("temperature_2m_mean"), i));
                        ps.setBigDecimal(18, getNullableBigDecimal(weatherData.getAsJsonArray("apparent_temperature_mean"), i));

                        ps.executeUpdate();
                    }
                }

                // -------------------------
                // Insert AQI Data
                // -------------------------
                String aqiSQL = "INSERT INTO historical_aqi " +
                        "(city_id, date, pm25_min, pm25_max, pm10_min, pm10_max, " +
                        "co_min, co_max, no2_min, no2_max, so2_min, so2_max, " +
                        "o3_min, o3_max, co2_min, co2_max, ch4_min, ch4_max) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

                try (PreparedStatement ps = conn.prepareStatement(aqiSQL)) {
                    JsonArray dates = weatherData.getAsJsonArray("time");

                    for (int i = 0; i < dates.size(); i++) {
                        LocalDate date = LocalDate.parse(dates.get(i).getAsString());

                        double[] pm25 = getMinMax(aqiData.getAsJsonArray("pm2_5"));
                        double[] pm10 = getMinMax(aqiData.getAsJsonArray("pm10"));
                        double[] co = getMinMax(aqiData.getAsJsonArray("carbon_monoxide"));
                        double[] no2 = getMinMax(aqiData.getAsJsonArray("nitrogen_dioxide"));
                        double[] so2 = getMinMax(aqiData.getAsJsonArray("sulphur_dioxide"));
                        double[] o3 = getMinMax(aqiData.getAsJsonArray("ozone"));
                        double[] co2 = getMinMax(aqiData.getAsJsonArray("carbon_dioxide"));
                        double[] ch4 = getMinMax(aqiData.getAsJsonArray("methane"));

                        ps.setInt(1, cityId);
                        ps.setDate(2, Date.valueOf(date));
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
            }

            System.out.println("Historical weather and AQI data inserted successfully!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
