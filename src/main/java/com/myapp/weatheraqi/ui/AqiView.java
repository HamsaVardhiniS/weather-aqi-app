package com.myapp.weatheraqi.ui;

import com.google.gson.JsonObject;
import com.myapp.weatheraqi.backend.CurrentDataFetcher;
import com.myapp.weatheraqi.backend.HistoricalDataFetchHelper;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

public class AqiView extends VerticalLayout {

    private final Div liveAqiDiv;
    private final Div historicalDiv;
    private final DatePicker historicalDatePicker;
    private final ProgressBar progressBar;
    private String currentCity;

    public AqiView(String city) {
        this();
        updateAqiForCity(city);
    }

    public AqiView() {
        setSizeFull();
        setSpacing(true);

        add(new H2("Air Quality Index (AQI)"));

        progressBar = new ProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        add(progressBar);

        liveAqiDiv = new Div();
        add(liveAqiDiv);

        historicalDiv = new Div();
        historicalDatePicker = new DatePicker("Select Historical Date");
        historicalDatePicker.addValueChangeListener(event -> {
            LocalDate selectedDate = event.getValue();
            if (selectedDate != null && currentCity != null) {
                updateHistoricalAqi(currentCity, selectedDate);
            }
        });

        add(historicalDatePicker, historicalDiv);
    }

    public void updateAqiForCity(String city) {
        if (city == null || city.isEmpty()) return;
        this.currentCity = city;

        setLoadingState(true);

        CompletableFuture.supplyAsync(() -> {
            try {
                return new CurrentDataFetcher().getCurrentDataForCity(city);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(data -> {
            getUI().ifPresent(ui -> ui.access(() -> {
                liveAqiDiv.removeAll();
                if (data != null && data.has("aqi")) {
                    JsonObject aqi = data.getAsJsonObject("aqi");
                    liveAqiDiv.add(new H2("🌫 Current AQI"));
                    for (String key : aqi.keySet()) {
                        liveAqiDiv.add(new Span(key + ": " + aqi.get(key).getAsString()));
                        liveAqiDiv.add(new Div()); // line break
                    }
                } else {
                    liveAqiDiv.add(new Span("No current AQI data for " + city));
                }
                resetHistoricalView();
                setLoadingState(false);
            }));
        }).exceptionally(ex -> {
            getUI().ifPresent(ui -> ui.access(() -> {
                liveAqiDiv.removeAll();
                liveAqiDiv.add(new Span("Failed to load current AQI: " + ex.getMessage()));
                resetHistoricalView();
                setLoadingState(false);
            }));
            return null;
        });
    }

    private void updateHistoricalAqi(String city, LocalDate date) {
        historicalDiv.removeAll();
        historicalDiv.add(new Span("Loading historical data..."));
        CompletableFuture.supplyAsync(() -> {
            try {
                return HistoricalDataFetchHelper.fetchHistoricalData(city, date);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).thenAccept(historicalData -> {
            getUI().ifPresent(ui -> ui.access(() -> {
                historicalDiv.removeAll();
                if (historicalData == null || !historicalData.has("aqi")) {
                    historicalDiv.add(new Span("No historical AQI for " + city + " on " + date));
                    return;
                }
                JsonObject aqi = historicalData.getAsJsonObject("aqi");
                historicalDiv.add(new H2("🕰 Historical AQI (" + date + ")"));
                for (String key : aqi.keySet()) {
                    historicalDiv.add(new Span(key + ": " + aqi.get(key).getAsString()));
                    historicalDiv.add(new Div()); // line break
                }
            }));
        }).exceptionally(ex -> {
            getUI().ifPresent(ui -> ui.access(() -> {
                historicalDiv.removeAll();
                historicalDiv.add(new Span("Failed to load historical AQI: " + ex.getMessage()));
            }));
            return null;
        });
    }
    
    private void setLoadingState(boolean isLoading) {
        progressBar.setVisible(isLoading);
        liveAqiDiv.setVisible(!isLoading);
        historicalDatePicker.setVisible(!isLoading);
        historicalDiv.setVisible(!isLoading);
    }
    
    private void resetHistoricalView() {
        historicalDiv.removeAll();
        historicalDiv.add(new Span("Select a date to view historical AQI..."));
    }
}