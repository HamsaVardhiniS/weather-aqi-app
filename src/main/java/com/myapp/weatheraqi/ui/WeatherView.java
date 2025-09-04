package com.myapp.weatheraqi.ui;

import com.google.gson.JsonObject;
import com.myapp.weatheraqi.backend.CurrentDataFetcher;
import com.myapp.weatheraqi.backend.WeatherForecastFetcher;
import com.myapp.weatheraqi.backend.HistoricalDataFetchHelper;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.time.LocalDate;

public class WeatherView extends VerticalLayout {

    private final Div currentWeatherDiv;
    private final Div forecastDiv;
    private final Div historicalDiv;
    private final DatePicker historicalDatePicker;
    private String currentCity;

    public WeatherView(String city) {
        this();
        updateWeatherForCity(city);
    }

    public WeatherView() {
        setSizeFull();
        setSpacing(true);

        add(new H2("Weather Information"));

        currentWeatherDiv = new Div();
        currentWeatherDiv.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(3, minmax(300px, 1fr))")
                .set("gap", "1.5rem")
                .set("width", "100%");

        add(currentWeatherDiv);

        forecastDiv = new Div();
        add(forecastDiv);

        historicalDiv = new Div();
        historicalDatePicker = new DatePicker("Select Historical Date");
        historicalDatePicker.addValueChangeListener(event -> {
            LocalDate date = event.getValue();
            if (date != null && currentCity != null) {
                updateHistoricalWeather(currentCity, date);
            }
        });

        add(historicalDatePicker, historicalDiv);
    }

    public void updateWeatherForCity(String city) {
        if (city == null || city.isEmpty()) return;

        currentCity = city;

        currentWeatherDiv.removeAll();
        try {
            JsonObject currentData = CurrentDataFetcher.getCurrentDataForCity(city);
            if (currentData != null && currentData.has("weather")) {
                JsonObject weather = currentData.getAsJsonObject("weather");
                currentWeatherDiv.add(new H2("🌡 Current Weather"));

                // Temperature
                if (weather.has("temperature_2m")) {
                    currentWeatherDiv.add(new WeatherCard(
                            "/images/temp.png",
                            "Temperature",
                            weather.get("temperature_2m").getAsString() + " °C"
                    ));
                }

                // Humidity
                if (weather.has("relative_humidity_2m")) {
                    currentWeatherDiv.add(new WeatherCard(
                            "/images/humidity.png",
                            "Humidity",
                            weather.get("relative_humidity_2m").getAsString() + " %"
                    ));
                }

                // Feels Like
                if (weather.has("apparent_temperature")) {
                    currentWeatherDiv.add(new WeatherCard(
                            "/images/feelslike.png",
                            "Feels Like",
                            weather.get("apparent_temperature").getAsString() + " °C"
                    ));
                }

                // Day/Night
                if (weather.has("is_day")) {
                    String value = weather.get("is_day").getAsString().equals("1") ? "Day" : "Night";
                    currentWeatherDiv.add(new WeatherCard(
                            "/images/daynight.png",
                            "Day / Night",
                            value
                    ));
                }

                // Precipitation + Rainfall
                if (weather.has("precipitation") && weather.has("rain")) {
                    String combined = weather.get("precipitation").getAsString() + " mm / "
                                    + weather.get("rain").getAsString() + " mm";
                    currentWeatherDiv.add(new WeatherCard(
                            "/images/rain.png",
                            "Precipitation | Rainfall",
                            combined
                    ));
                }

                // Weather Code
                if (weather.has("weather_code")) {
                    currentWeatherDiv.add(new WeatherCard(
                            "/images/code.png",
                            "Weather Code",
                            weather.get("weather_code").getAsString()
                    ));
                }

                if (weather.has("pressure_msl") && weather.has("surface_pressure")) {
                    String combined = "MSL: " + weather.get("pressure_msl").getAsString() + " hPa, "
                                    + "Surface: " + weather.get("surface_pressure").getAsString() + " hPa";
                    currentWeatherDiv.add(new WeatherCard(
                            "/images/pressure.png",
                            "Pressure",
                            combined
                    ));
                }

                if (weather.has("wind_speed_10m")) {
                    currentWeatherDiv.add(new WeatherCard(
                            "/images/wind.png",
                            "Wind Speed",
                            weather.get("wind_speed_10m").getAsString() + " m/s"
                    ));
                }

                if (weather.has("uv_index")) {
                    currentWeatherDiv.add(new WeatherCard(
                            "/images/uv.png",
                            "UV Index",
                            weather.get("uv_index").getAsString()
                    ));
                }

            } else {
                currentWeatherDiv.add(new Span("⚠️ No current weather data for " + city));
            }
        } catch (Exception e) {
            currentWeatherDiv.add(new Span("⚠️ Failed to load current weather: " + e.getMessage()));
        }

        // --- Forecast simplified (no charts) ---
        forecastDiv.removeAll();
        try {
            JsonObject forecastData = WeatherForecastFetcher.getForecastDataForCity(city);
            if (forecastData != null && forecastData.has("forecast")) {
                forecastDiv.add(new H2("📅 7-day Forecast"));

                JsonObject daily = forecastData.getAsJsonObject("forecast");
                int days = daily.getAsJsonArray("time").size();

                // Table container
                Div table = new Div();
                table.getStyle()
                        .set("display", "grid")
                        .set("grid-template-columns", "repeat(7, minmax(120px, 1fr))")
                        .set("gap", "0.5rem")
                        .set("overflow-x", "auto")
                        .set("border", "1px solid #ccc")
                        .set("padding", "0.5rem");

                // --- Header row ---
                addCell(table, "Date", true);
                addCell(table, "Max Temp (°C)", true);
                addCell(table, "Min Temp (°C)", true);
                addCell(table, "Rain (mm)", true);
                addCell(table, "Sunrise", true);
                addCell(table, "Sunset", true);
                addCell(table, "Wind Max (m/s)", true);

                // --- Data rows ---
                for (int i = 0; i < days; i++) {
                    addCell(table, daily.getAsJsonArray("time").get(i).getAsString(), false);
                    addCell(table, daily.getAsJsonArray("temperature_2m_max").get(i).getAsString(), false);
                    addCell(table, daily.getAsJsonArray("temperature_2m_min").get(i).getAsString(), false);
                    addCell(table, daily.getAsJsonArray("precipitation_sum").get(i).getAsString(), false);
                    addCell(table, daily.getAsJsonArray("sunrise").get(i).getAsString().substring(11), false);
                    addCell(table, daily.getAsJsonArray("sunset").get(i).getAsString().substring(11), false);
                    addCell(table, daily.getAsJsonArray("wind_speed_10m_max").get(i).getAsString(), false);
                }

                forecastDiv.add(table);

            } else {
                forecastDiv.add(new Span("⚠️ No forecast data for " + city));
            }
        } catch (Exception e) {
            forecastDiv.add(new Span("⚠️ Failed to load forecast: " + e.getMessage()));
        }


        historicalDiv.removeAll();
        historicalDiv.add(new Span("🕰 Select a date to view historical weather..."));
    }

    private void updateHistoricalWeather(String city, LocalDate date) {
        historicalDiv.removeAll();
        try {
            JsonObject historicalData = HistoricalDataFetchHelper.fetchHistoricalData(city, date);
            if (historicalData == null || !historicalData.has("weather")) {
                historicalDiv.add(new Span("⚠️ No historical data for " + city + " on " + date));
                return;
            }

            JsonObject weather = historicalData.getAsJsonObject("weather");
            historicalDiv.add(new H2("🕰 Historical Weather (" + date + ")"));

            Div cardGrid = new Div();
            cardGrid.getStyle()
                    .set("display", "grid")
                    .set("grid-template-columns", "repeat(auto-fit, minmax(280px, 1fr))")
                    .set("gap", "1rem");

            // Temperature Card
            if (weather.has("temperature_2m_max") && weather.has("temperature_2m_min") && weather.has("temperature_2m_mean")) {
                String value = "Max: " + weather.get("temperature_2m_max").getAsString() + " °C\n" +
                            "Min: " + weather.get("temperature_2m_min").getAsString() + " °C\n" +
                            "Mean: " + weather.get("temperature_2m_mean").getAsString() + " °C";
                cardGrid.add(new WeatherCard("/images/temp.png", "Temperature", value));
            }

            // Feels Like Card
            if (weather.has("apparent_temperature_max") && weather.has("apparent_temperature_min") && weather.has("apparent_temperature_mean")) {
                String value = "Max: " + weather.get("apparent_temperature_max").getAsString() + " °C\n" +
                            "Min: " + weather.get("apparent_temperature_min").getAsString() + " °C\n" +
                            "Mean: " + weather.get("apparent_temperature_mean").getAsString() + " °C";
                cardGrid.add(new WeatherCard("/images/feelslike.png", "Feels Like", value));
            }

            // Rain / Precipitation
            if (weather.has("rain_sum") && weather.has("precipitation_sum")) {
                String value = "Rain: " + weather.get("rain_sum").getAsString() + " mm\n" +
                            "Precipitation: " + weather.get("precipitation_sum").getAsString() + " mm";
                cardGrid.add(new WeatherCard("/images/rain.png", "Rain & Precipitation", value));
            }

            // Wind
            if (weather.has("wind_speed_10m_max") && weather.has("wind_gusts_10m_max") && weather.has("wind_direction_10m_dominant")) {
                String value = "Speed Max: " + weather.get("wind_speed_10m_max").getAsString() + " m/s\n" +
                            "Gusts: " + weather.get("wind_gusts_10m_max").getAsString() + " m/s\n" +
                            "Direction: " + weather.get("wind_direction_10m_dominant").getAsString() + "°";
                cardGrid.add(new WeatherCard("/images/wind.png", "Wind", value));
            }

            // Sun Cycle
            if (weather.has("sunrise") && weather.has("sunset") && weather.has("daylight_duration") && weather.has("sunshine_duration")) {
                String value = "Sunrise: " + weather.get("sunrise").getAsString() + "\n" +
                            "Sunset: " + weather.get("sunset").getAsString() + "\n" +
                            "Daylight: " + weather.get("daylight_duration").getAsString() + " min\n" +
                            "Sunshine: " + weather.get("sunshine_duration").getAsString() + " min";
                cardGrid.add(new WeatherCard("/images/daynight.png", "Sun Cycle", value));
            }

            // Weather Code
            if (weather.has("weather_code")) {
                cardGrid.add(new WeatherCard("/images/code.png", "Weather Code", weather.get("weather_code").getAsString()));
            }

            historicalDiv.add(cardGrid);

        } catch (Exception e) {
            historicalDiv.add(new Span("⚠️ Failed to load historical weather: " + e.getMessage()));
        }
    }

    private static class WeatherCard extends Div {
        public WeatherCard(String imagePath, String title, String value) {
            getStyle().set("background-color", "#e0f2fe");
            getStyle().set("color", "#0c4a6e");
            getStyle().set("border-radius", "18px");
            getStyle().set("padding", "1.5rem");
            getStyle().set("margin", "0.8rem");
            getStyle().set("box-shadow", "0 2px 6px rgba(0,0,0,0.2)");
            getStyle().set("min-height", "160px");
            getStyle().set("min-width", "280px");
            getStyle().set("display", "flex");
            getStyle().set("align-items", "center");

            Image icon = new Image(imagePath, title);
            icon.setWidth("36px");
            icon.setHeight("36px");
            icon.getStyle().set("margin-right", "12px");

            VerticalLayout textLayout = new VerticalLayout();
            textLayout.setPadding(false);
            textLayout.setSpacing(false);

            Span titleSpan = new Span(title);
            titleSpan.getStyle().set("font-size", "14px").set("font-weight", "bold");

            Span valueSpan = new Span(value);
            valueSpan.getStyle().set("font-size", "16px").set("font-weight", "600");

            textLayout.add(titleSpan, valueSpan);

            add(icon, textLayout);
        }
    }
 
     private void addCell(Div table, String text, boolean header) {
        Span cell = new Span(text);
        cell.getStyle()
            .set("padding", "6px 8px")
            .set("border", "1px solid #ddd")
            .set("font-size", "14px")
            .set("text-align", "center");
        if (header) {
            cell.getStyle()
                .set("font-weight", "bold")
                .set("background-color", "#e0f2fe");
        }
        table.add(cell);
    }

}
