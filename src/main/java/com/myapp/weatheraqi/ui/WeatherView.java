package com.myapp.weatheraqi.ui;

import com.google.gson.JsonObject;
import com.myapp.weatheraqi.backend.CurrentDataFetcher;
import com.myapp.weatheraqi.backend.WeatherForecastFetcher;
import com.myapp.weatheraqi.backend.HistoricalDataFetchHelper;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
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
                for (String key : weather.keySet()) {
                    currentWeatherDiv.add(new Span(key + ": " + weather.get(key).getAsString()));
                    currentWeatherDiv.add(new Div()); // line break
                }
            } else {
                currentWeatherDiv.add(new Span("⚠️ No current weather data for " + city));
            }
        } catch (Exception e) {
            currentWeatherDiv.add(new Span("⚠️ Failed to load current weather: " + e.getMessage()));
        }

        forecastDiv.removeAll();
        try {
            JsonObject forecastData = WeatherForecastFetcher.getForecastDataForCity(city);
            if (forecastData != null && forecastData.has("forecast")) {
                JsonObject daily = forecastData.getAsJsonObject("forecast");
                forecastDiv.add(new H2("📅 7-day Forecast"));
                int days = daily.getAsJsonArray("time").size();
                for (int i = 0; i < days; i++) {
                    Div dayDiv = new Div();
                    dayDiv.add(new Span(daily.getAsJsonArray("time").get(i).getAsString()));
                    dayDiv.getStyle().set("margin-bottom", "0.5rem");

                    for (String key : daily.keySet()) {
                        if (!key.equals("time")) {
                            dayDiv.add(new Span("  " + key + ": " + daily.getAsJsonArray(key).get(i).getAsString()));
                            dayDiv.add(new Div());
                        }
                    }
                    forecastDiv.add(dayDiv);
                }
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
            for (String key : weather.keySet()) {
                historicalDiv.add(new Span(key + ": " + weather.get(key).getAsString()));
                historicalDiv.add(new Div()); // line break
            }

        } catch (Exception e) {
            historicalDiv.add(new Span("⚠️ Failed to load historical weather: " + e.getMessage()));
        }
    }
}
