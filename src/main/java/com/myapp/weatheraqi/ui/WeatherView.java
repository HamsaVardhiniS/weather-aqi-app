package com.myapp.weatheraqi.ui;

import java.io.ByteArrayInputStream;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.myapp.weatheraqi.backend.CurrentDataFetcher;
import com.myapp.weatheraqi.backend.HistoricalDataFetchHelper;
import com.myapp.weatheraqi.backend.WeatherForecastFetcher;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.server.StreamResource;

public class WeatherView extends VerticalLayout {

    private final Div heroSection;
    private final Div currentWeatherDiv;
    private final Div forecastDiv;
    private final Div historicalDiv;
    private final Div historicalChartsDiv;
    private final DatePicker historicalDatePicker;
    private final ProgressBar progressBar;
    private String currentCity;

    public WeatherView(String city) {
        this();
        updateWeatherForCity(city);
    }

    public WeatherView() {
        setSizeFull();
        setSpacing(true);
        getStyle().set("position", "relative");

        add(new H2("Weather Information"));

        progressBar = new ProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);
        add(progressBar);

        heroSection = new Div();
        heroSection.setWidthFull();
        add(heroSection);

        currentWeatherDiv = new Div();
        currentWeatherDiv.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(1, minmax(320px, 1fr))")
                .set("gap", "1.5rem")
                .set("width", "100%");
        add(currentWeatherDiv);

        forecastDiv = new Div();
        add(forecastDiv);

        historicalChartsDiv = new Div();
        add(historicalChartsDiv);

        historicalDatePicker = new DatePicker("Select Historical Date");
        historicalDatePicker.addValueChangeListener(event -> {
            LocalDate date = event.getValue();
            if (date != null && currentCity != null) {
                updateHistoricalWeather(currentCity, date);
            }
        });

        historicalDiv = new Div();
        add(historicalDatePicker, historicalDiv);
    }

    public void updateWeatherForCity(String city) {
        if (city == null || city.isEmpty()) {
            return;
        }
        this.currentCity = city;

        setLoadingState(true);

        CompletableFuture<JsonObject> currentFuture = CurrentDataFetcher.getCurrentDataForCityAsync(city);

        CompletableFuture<JsonObject> forecastFuture = new WeatherForecastFetcher().getForecastDataForCityAsync(city);

        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        LocalDate monthlyStart = lastMonth.withDayOfMonth(1);
        LocalDate monthlyEnd = lastMonth.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
        CompletableFuture<JsonObject> monthlyFuture = HistoricalDataFetchHelper.fetchHistoricalDataAsync(city, monthlyStart, monthlyEnd);


        CompletableFuture.allOf(currentFuture, forecastFuture, monthlyFuture)
                .thenAccept(voidResult -> {
                    JsonObject currentData = currentFuture.join();
                    JsonObject forecastData = forecastFuture.join();
                    JsonObject monthlyData = monthlyFuture.join();

                    getUI().ifPresent(ui -> ui.access(() -> {
                        updateCurrentWeatherUI(city, currentData);
                        updateForecastUI(city, forecastData);
                        updateChartsDashboardUI(monthlyData, forecastData, LocalDate.now());
                        resetSingleDayHistoricalView();
                        setLoadingState(false); 
                    }));
                })
                .exceptionally(ex -> {
                    getUI().ifPresent(ui -> ui.access(() -> {
                        removeAll();
                        add(new H2("Weather Information"));
                        Div errorPanel = new Div(new Span("Failed to load weather data for " + city + ". Please try another city or refresh."));
                        errorPanel.getStyle()
                                .set("color", "red")
                                .set("border", "1px solid red")
                                .set("padding", "1rem")
                                .set("border-radius", "8px");
                        add(errorPanel);
                        setLoadingState(false);
                        ex.printStackTrace(); 
                    }));
                    return null;
                });
    }

    private void setLoadingState(boolean isLoading) {
        progressBar.setVisible(isLoading);
        heroSection.setVisible(!isLoading);
        currentWeatherDiv.setVisible(!isLoading);
        forecastDiv.setVisible(!isLoading);
        historicalChartsDiv.setVisible(!isLoading);
        historicalDatePicker.setVisible(!isLoading);
        historicalDiv.setVisible(!isLoading);
    }

    private void updateCurrentWeatherUI(String city, JsonObject currentData) {
        heroSection.removeAll();
        currentWeatherDiv.removeAll();

        if (currentData == null) {
            currentWeatherDiv.add(new Span("No response received from weather service."));
            return;
        }

        if (currentData.has("weather")) {
            JsonObject weather = currentData.getAsJsonObject("weather");
            addCurrentWeatherHero(weather, city);

            Div attributesGrid = new Div();
            attributesGrid.getStyle()
                    .set("display", "grid")
                    .set("grid-template-columns", "1fr 1fr")
                    .set("gap", "16px")
                    .set("margin-top", "20px")
                    .set("width", "100%");

            if (weather.has("wind_speed_10m") && weather.has("wind_direction_10m")) {
                double windMs = weather.get("wind_speed_10m").getAsDouble();
                double windKmh = windMs * 3.6;
                String category = categorizeWindSpeed(windKmh);
                String windLine1 = String.format("%.1f km/h", windKmh) + " (" + category + ")";
                String windLine2 = weather.get("wind_direction_10m").getAsString() + "°";
                attributesGrid.add(createAttributePanel("/images/windmillbk.png", windLine1, windLine2));
            }

            if (weather.has("pressure_msl") && weather.has("surface_pressure")) {
                String msl = "MSL: " + weather.get("pressure_msl").getAsString() + " hPa";
                String surface = "Surface: " + weather.get("surface_pressure").getAsString() + " hPa";
                attributesGrid.add(createAttributePanel("/images/pressurebk.png", msl, surface));
            }

            if (weather.has("precipitation") && weather.has("rain")) {
                String line1 = "Rain: " + weather.get("rain").getAsString() + " mm";
                String line2 = "Precip: " + weather.get("precipitation").getAsString() + " mm";
                Div rainBox = createAttributePanel("/images/precipitationbk.png", line1, line2);
                rainBox.getStyle().set("grid-column", "span 2");
                attributesGrid.add(rainBox);
            }
            currentWeatherDiv.add(attributesGrid);
        } else {
            currentWeatherDiv.add(new Span("No current weather data for " + city));
        }
    }

    private void updateForecastUI(String city, JsonObject forecastData) {
        forecastDiv.removeAll();

        if (forecastData == null || !forecastData.has("daily")) {
            forecastDiv.add(new H2("7-day Forecast"), new Span("No forecast data for " + city));
            return;
        }

        try {
            forecastDiv.add(new H2("7-day Forecast"));
            JsonObject daily = forecastData.getAsJsonObject("daily");
            int days = daily.getAsJsonArray("time").size();

            Div forecastWrapper = new Div();
            forecastWrapper.getStyle()
                    .set("position", "relative")
                    .set("margin-top", "20px")
                    .set("width", "100%")
                    .set("display", "flex")
                    .set("justify-content", "center")
                    .set("align-items", "center");

            Div cardContainer = new Div();
            cardContainer.getStyle()
                    .set("width", "100vw")
                    .set("min-height", "320px")
                    .set("background-image", "url('/images/forecastbk.png')")
                    .set("background-size", "cover")
                    .set("background-position", "center")
                    .set("border-radius", "0")
                    .set("padding", "40px")
                    .set("box-shadow", "inset 0 0 50px rgba(0,0,0,0.4)")
                    .set("display", "flex")
                    .set("flex-direction", "column")
                    .set("justify-content", "space-between")
                    .set("align-items", "center")
                    .set("overflow", "hidden");

            AtomicInteger currentIndex = new AtomicInteger(0);

            Button leftArrow = new Button("<");
            setArrowStyle(leftArrow, "left");

            Button rightArrow = new Button(">");
            setArrowStyle(rightArrow, "right");

            Runnable updateCard = () -> {
                try {
                    int i = currentIndex.get();
                    cardContainer.removeAll();

                    String date = daily.getAsJsonArray("time").get(i).getAsString();
                    String max = daily.getAsJsonArray("temperature_2m_max").get(i).getAsString();
                    String min = daily.getAsJsonArray("temperature_2m_min").get(i).getAsString();
                    String rain = daily.getAsJsonArray("precipitation_sum").get(i).getAsString();
                    String sunrise = daily.getAsJsonArray("sunrise").get(i).getAsString().substring(11);
                    String sunset = daily.getAsJsonArray("sunset").get(i).getAsString().substring(11);
                    String wind = daily.getAsJsonArray("wind_speed_10m_max").get(i).getAsString();

                    H3 title = new H3(date);
                    title.getStyle()
                            .set("margin", "0")
                            .set("margin-bottom", "32px")
                            .set("color", "white")
                            .set("font-weight", "900")
                            .set("font-size", "40px");

                    Div attributesRow = new Div();
                    attributesRow.getStyle()
                            .set("display", "flex")
                            .set("justify-content", "space-around")
                            .set("width", "100%")
                            .set("max-width", "1200px")
                            .set("gap", "40px")
                            .set("color", "white")
                            .set("font-size", "28px");

                    attributesRow.add(
                            makeForecastAttribute("🌡️", "Max Temp", max + " °C"),
                            makeForecastAttribute("🥶", "Min Temp", min + " °C"),
                            makeForecastAttribute("🌧️", "Rain", rain + " mm"),
                            makeForecastAttribute("🌅", "Sunrise", sunrise),
                            makeForecastAttribute("🌇", "Sunset", sunset),
                            makeForecastAttribute("💨", "Wind", wind + " m/s")
                    );
                    cardContainer.add(title, attributesRow);

                    leftArrow.setEnabled(i > 0);
                    leftArrow.getStyle().set("opacity", i > 0 ? "1" : "0.3");
                    rightArrow.setEnabled(i < days - 1);
                    rightArrow.getStyle().set("opacity", i < days - 1 ? "1" : "0.3");

                } catch (Exception ex) {
                    cardContainer.removeAll();
                    cardContainer.add(new Span("Error rendering forecast card: " + ex.getMessage()));
                    ex.printStackTrace();
                }
            };

            leftArrow.addClickListener(e -> {
                if (currentIndex.get() > 0) {
                    currentIndex.decrementAndGet();
                    updateCard.run();
                }
            });
            rightArrow.addClickListener(e -> {
                if (currentIndex.get() < days - 1) {
                    currentIndex.incrementAndGet();
                    updateCard.run();
                }
            });

            updateCard.run();
            forecastWrapper.add(leftArrow, cardContainer, rightArrow);
            forecastDiv.add(forecastWrapper);

        } catch (Exception e) {
            forecastDiv.add(new Span("Failed to load forecast: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    private void updateChartsDashboardUI(JsonObject monthlyData, JsonObject forecastData, LocalDate referenceDate) {
        historicalChartsDiv.removeAll();
        try {
            if (monthlyData != null && monthlyData.has("weather")) {
            LocalDate lastMonthDate = referenceDate.minusMonths(1);
            String dynamicTitle = lastMonthDate.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"));
            historicalChartsDiv.add(new H2("Monthly Summary (" + dynamicTitle + ")"));             
                JsonArray monthlyWeather = monthlyData.getAsJsonArray("weather");
                buildMonthlyDashboard(monthlyWeather);
            } else {
                historicalChartsDiv.add(new Span("No monthly historical weather data."));
            }

            if (forecastData != null && forecastData.has("daily")) {
                JsonObject daily = forecastData.getAsJsonObject("daily");
                buildForecastDashboard(daily);
            } else {
                historicalChartsDiv.add(new Span("No weekly forecast data."));
            }

        } catch (Exception e) {
            historicalChartsDiv.add(new Span("Failed to load historical charts: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    private void resetSingleDayHistoricalView() {
        historicalDiv.removeAll();
        historicalDiv.add(new Span("Select a date to view historical weather..."));
    }

    private void setArrowStyle(Button arrow, String position) {
        arrow.getStyle()
                .set("position", "absolute")
                .set(position, "20px")
                .set("top", "50%")
                .set("transform", "translateY(-50%)")
                .set("z-index", "10")
                .set("background", "rgba(255,255,255,0.7)")
                .set("color", "#1e293b")
                .set("border-radius", "50%")
                .set("width", "40px")
                .set("height", "40px")
                .set("box-shadow", "0 2px 6px rgba(0,0,0,0.3)");
    }

    private void addCurrentWeatherHero(JsonObject weather, String city) {
        boolean isDay = weather.has("is_day") && "1".equals(weather.get("is_day").getAsString());
        String bgImage = isDay ? "/images/cloud_day.jpg" : "/images/cloud_night.jpg";
        String textColor = isDay ? "#0c4a6e" : "#f1f5f9";

        heroSection.getStyle()
                .set("width", "100vw")
                .set("min-height", "320px")
                .set("background-image", "url('" + bgImage + "')")
                .set("background-size", "cover")
                .set("background-position", "center")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("padding", "20px 40px")
                .set("box-shadow", "0 4px 10px rgba(0,0,0,0.3)")
                .set("color", textColor)
                .set("position", "relative"); // Parent must be relative for absolute children

        String description = weather.has("weather_code") ? mapWeatherCode(weather.get("weather_code").getAsInt()) : "Unknown";
        H2 heading = new H2(city + " - Current Weather Condition:");
        heading.getStyle().set("margin", "0").set("font-size", "26px").set("font-weight", "700").set("color", textColor);
        heroSection.add(heading);

        Div middleRow = new Div();
        middleRow.getStyle()
                .set("display", "flex")
                .set("align-items", "flex-end") // FIX: Align items to the bottom
                .set("justify-content", "space-between")
                .set("width", "100%")
                .set("gap", "1rem") // Add some gap between elements
                .set("margin-top", "10px")
                .set("margin-bottom", "10px");

        Div leftCol = new Div();
        leftCol.getStyle().set("display", "flex").set("flex-direction", "column").set("align-items", "flex-start");
        String temp = weather.has("temperature_2m") ? weather.get("temperature_2m").getAsString() + " °C" : "--";
        Image tempIcon = new Image("/images/temp.jpg", "Temperature");
        tempIcon.setWidth("60px");
        tempIcon.setHeight("60px");
        Span tempSpan = new Span(temp);
        tempSpan.getStyle().set("font-size", "56px").set("font-weight", "bold").set("margin-left", "10px").set("color", textColor);
        Div tempRow = new Div(tempIcon, tempSpan);
        tempRow.getStyle().set("display", "flex").set("align-items", "center");
        leftCol.add(tempRow);

        Span descSpan = new Span(description);
        descSpan.getStyle()
                .set("background", isDay ? "rgba(255,255,255,0.8)" : "rgba(0,0,0,0.5)")
                .set("padding", "4px 10px")
                .set("border-radius", "6px")
                .set("font-weight", "700")
                .set("margin-top", "8px")
                .set("color", textColor);
        leftCol.add(descSpan);

        Div centerCol = new Div();
        centerCol.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("flex", "1");

        Div feelsDiv = new Div();
        feelsDiv.getStyle().set("display", "flex").set("align-items", "center").set("margin-bottom", "8px");
        Image feelsIcon = new Image("/images/feelslike.png", "Feels Like");
        feelsIcon.setWidth("28px");
        feelsIcon.setHeight("28px");
        feelsIcon.getStyle().set("margin-right", "8px");
        String feelsLike = weather.has("apparent_temperature") ? weather.get("apparent_temperature").getAsString() + " °C" : "--";
        Span feelsText = new Span("Feels Like: " + feelsLike);
        feelsText.getStyle().set("color", textColor);
        feelsDiv.add(feelsIcon, feelsText);

        Div humidityDiv = new Div();
        humidityDiv.getStyle().set("display", "flex").set("align-items", "center");
        Image humIcon = new Image("/images/humidity.png", "Humidity");
        humIcon.setWidth("28px");
        humIcon.setHeight("28px");
        humIcon.getStyle().set("margin-right", "8px");
        String humidity = weather.has("relative_humidity_2m") ? weather.get("relative_humidity_2m").getAsString() + " %" : "--";
        Span humText = new Span("Humidity: " + humidity);
        humText.getStyle().set("color", textColor);
        humidityDiv.add(humIcon, humText);

        centerCol.add(feelsDiv, humidityDiv);

        String boyImgPath = weather.has("weather_code") ? mapWeatherBoyImage(weather.get("weather_code").getAsInt(), isDay) : (isDay ? "/images/cloudyboy.png" : "/images/cloudyboynight.png");
        Image boyImage = new Image(boyImgPath, "Weather Character");
        boyImage.getStyle()
                .set("height", "220px")
                .set("object-fit", "contain");

        middleRow.add(leftCol, centerCol, boyImage);
        heroSection.add(middleRow);

        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy HH:mm"));
        Span dateTime = new Span(now);
        dateTime.getStyle()
                .set("position", "absolute")
                .set("bottom", "15px")
                .set("right", "40px")
                .set("font-size", "14px")
                .set("opacity", "0.9")
                .set("font-weight", "bold")
                .set("color", textColor);
        heroSection.add(dateTime);
    }

    private void updateHistoricalWeather(String city, LocalDate date) {
        historicalDiv.removeAll();
        try {
            JsonObject historicalData = HistoricalDataFetchHelper.fetchHistoricalData(city, date);
            if (historicalData == null || !historicalData.has("weather")) {
                historicalDiv.add(new Span("No historical data for " + city + " on " + date));
                return;
            }

            JsonObject weather = historicalData.getAsJsonObject("weather");
            historicalDiv.add(new H2("Historical Weather (" + date + ")"));

            Div cardGrid = new Div();
            cardGrid.getStyle()
                    .set("display", "grid")
                    .set("grid-template-columns", "repeat(auto-fit, minmax(280px, 1fr))")
                    .set("gap", "1rem");

            if (weather.has("temperature_2m_max") && weather.has("temperature_2m_min")) {
                String value = "Max: " + weather.get("temperature_2m_max").getAsString() + " °C<br>" +
                        "Min: " + weather.get("temperature_2m_min").getAsString() + " °C<br>" +
                        "Mean: " + weather.get("temperature_2m_mean").getAsString() + " °C";
                cardGrid.add(new WeatherCard("/images/temp.png", "Temperature", value));
            }

            if (weather.has("apparent_temperature_max") && weather.has("apparent_temperature_min")) {
                String value = "Max: " + weather.get("apparent_temperature_max").getAsString() + " °C<br>" +
                        "Min: " + weather.get("apparent_temperature_min").getAsString() + " °C<br>" +
                        "Mean: " + weather.get("apparent_temperature_mean").getAsString() + " °C";
                cardGrid.add(new WeatherCard("/images/feelslike.png", "Feels Like", value));
            }

            if (weather.has("rain_sum") && weather.has("precipitation_sum")) {
                String value = "Rain: " + weather.get("rain_sum").getAsString() + " mm<br>" +
                        "Precipitation: " + weather.get("precipitation_sum").getAsString() + " mm";
                cardGrid.add(new WeatherCard("/images/rain.png", "Rain & Precipitation", value));
            }

            if (weather.has("wind_speed_10m_max") && weather.has("wind_direction_10m_dominant")) {
                String val = "Speed Max: " + weather.get("wind_speed_10m_max").getAsString() + " m/s<br>" +
                        "Gusts: " + weather.get("wind_gusts_10m_max").getAsString() + " m/s<br>" +
                        "Direction: " + weather.get("wind_direction_10m_dominant").getAsString() + "°";
                cardGrid.add(new WeatherCard("/images/wind.png", "Wind", val));
            }

            if (weather.has("sunrise") && weather.has("sunset")) {
                String value = "Sunrise: " + weather.get("sunrise").getAsString() + "<br>" +
                        "Sunset: " + weather.get("sunset").getAsString() + "<br>" +
                        "Daylight: " + weather.get("daylight_duration").getAsString() + " min<br>" +
                        "Sunshine: " + weather.get("sunshine_duration").getAsString() + " min";
                cardGrid.add(new WeatherCard("/images/daynight.png", "Sun Cycle", value));
            }

            if (weather.has("weather_code")) {
                cardGrid.add(new WeatherCard("/images/code.png", "Weather Code", weather.get("weather_code").getAsString()));
            }

            historicalDiv.add(cardGrid);

        } catch (Exception e) {
            historicalDiv.add(new Span("Failed to load historical weather: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    private Div createAttributePanel(String bgImage, String line1, String line2) {
        Div panel = new Div();
        panel.getStyle()
                .set("background-image", "url('" + bgImage + "')")
                .set("background-size", "cover")
                .set("background-position", "center")
                .set("border-radius", "12px")
                .set("padding", "20px")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("justify-content", "center")
                .set("align-items", "flex-end")
                .set("color", "white")
                .set("height", "180px")
                .set("box-shadow", "0 4px 10px rgba(0,0,0,0.3)");

        Span line1Span = new Span(line1);
        line1Span.getStyle()
                .set("font-weight", "800")
                .set("font-size", "28px")
                .set("margin-bottom", "10px");

        Span line2Span = new Span(line2);
        line2Span.getStyle()
                .set("font-size", "20px")
                .set("opacity", "0.95");

        panel.add(line1Span, line2Span);
        return panel;
    }

    private Div makeForecastAttribute(String emoji, String label, String value) {
        Div box = new Div();
        box.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("text-align", "center");

        Span emojiSpan = new Span(emoji);
        emojiSpan.getStyle().set("font-size", "32px");

        Span labelSpan = new Span(label);
        labelSpan.getStyle().set("font-size", "18px").set("font-weight", "600").set("margin-top", "4px");

        Span valueSpan = new Span(value);
        valueSpan.getStyle().set("font-size", "22px").set("font-weight", "700").set("margin-top", "2px");

        box.add(emojiSpan, labelSpan, valueSpan);
        return box;
    }

    private void buildMonthlyDashboard(JsonArray weatherData) {
        try {
            HorizontalLayout topRow = new HorizontalLayout();
            topRow.setWidthFull();
            topRow.setSpacing(true);
            topRow.add(
                    buildMonthlyAvgTempCard(weatherData),
                    buildMonthlyMaxTempCard(weatherData),
                    buildMonthlyTotalRainCard(weatherData)
            );

            HorizontalLayout chartRow = new HorizontalLayout();
            chartRow.setWidthFull();
            chartRow.setSpacing(true);
            chartRow.add(
                    buildMonthlyTempTrendChart(weatherData),
                    buildMonthlyRainHumidityChart(weatherData)
            );

            historicalChartsDiv.add(topRow, chartRow);
        } catch (Exception e) {
            historicalChartsDiv.add(new Span("Failed to build monthly dashboard: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    private void buildForecastDashboard(JsonObject forecastData) {
        try {
            if (forecastData == null) {
                forecastDiv.add(new Span("No forecast data available for charts."));
                return;
            }

            JsonArray forecastArray = transformForecastData(forecastData);

            Image tempForecastImg = buildForecastTempChart(forecastArray);
            Image rainForecastImg = buildForecastRainChart(forecastArray);

            HorizontalLayout forecastRow = new HorizontalLayout();
            forecastRow.setWidthFull();
            forecastRow.setSpacing(true);
            forecastRow.add(tempForecastImg, rainForecastImg);

            historicalChartsDiv.add(new H2("7-Day Forecast Charts"), forecastRow);
        } catch (Exception e) {
            historicalChartsDiv.add(new Span("Failed to build forecast dashboard: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    private JsonArray transformForecastData(JsonObject daily) {
        JsonArray forecastArray = new JsonArray();
        int days = daily.getAsJsonArray("time").size();

        for (int i = 0; i < days; i++) {
            JsonObject dayObj = new JsonObject();
            dayObj.addProperty("date", daily.getAsJsonArray("time").get(i).getAsString());
            dayObj.addProperty("rain_sum", daily.getAsJsonArray("precipitation_sum").get(i).getAsDouble());
            dayObj.addProperty("wind_speed_10m_max", daily.getAsJsonArray("wind_speed_10m_max").get(i).getAsDouble());
            dayObj.addProperty("temperature_2m_max", daily.getAsJsonArray("temperature_2m_max").get(i).getAsDouble());
            dayObj.addProperty("temperature_2m_min", daily.getAsJsonArray("temperature_2m_min").get(i).getAsDouble());
            forecastArray.add(dayObj);
        }
        return forecastArray;
    }

    private Div buildMonthlyAvgTempCard(JsonArray monthlyWeather) {
        Div card = createDashboardCard();
        try {
            double sum = 0;
            int count = 0;
            for (JsonElement e : monthlyWeather) {
                JsonObject day = e.getAsJsonObject();
                if (day.has("temperature_2m_mean") && !day.get("temperature_2m_mean").isJsonNull()) {
                    sum += day.get("temperature_2m_mean").getAsDouble();
                    count++;
                }
            }
            double avg = count > 0 ? sum / count : 0;
            card.add(new H3("Avg Temp"), new H4(String.format("%.1f °C", avg)));
        } catch (Exception e) {
            card.add(new Span("Error loading card"));
            e.printStackTrace();
        }
        return card;
    }

    private Div buildMonthlyMaxTempCard(JsonArray monthlyWeather) {
        Div card = createDashboardCard();
        try {
            double max = Double.NEGATIVE_INFINITY;
            String date = "";

            for (JsonElement e : monthlyWeather) {
                JsonObject day = e.getAsJsonObject();
                if (day.has("temperature_2m_max") && !day.get("temperature_2m_max").isJsonNull()) {
                    double t = day.get("temperature_2m_max").getAsDouble();
                    if (t > max) {
                        max = t;
                        date = day.has("date") ? day.get("date").getAsString() : "N/A";
                    }
                }
            }
            card.add(new H3("Max Temp"), new Span("Date: " + date), new H4(String.format("%.1f °C", max)));
        } catch (Exception e) {
            card.add(new Span("Error loading card"));
            e.printStackTrace();
        }
        return card;
    }

    private Div buildMonthlyTotalRainCard(JsonArray monthlyWeather) {
        Div card = createDashboardCard();
        try {
            double total = 0;
            for (JsonElement e : monthlyWeather) {
                JsonObject day = e.getAsJsonObject();
                if (day.has("rain_sum") && !day.get("rain_sum").isJsonNull()) {
                    total += day.get("rain_sum").getAsDouble();
                }
            }
            card.add(new H3("Total Rainfall"), new H4(String.format("%.1f mm", total)));
        } catch (Exception e) {
            card.add(new Span("Error loading card"));
            e.printStackTrace();
        }
        return card;
    }

    private Div createDashboardCard() {
        Div card = new Div();
        card.getStyle()
                .set("padding", "20px")
                .set("border", "1px solid #ccc")
                .set("border-radius", "12px")
                .set("box-shadow", "2px 2px 10px rgba(0,0,0,0.1)")
                .set("text-align", "center")
                .set("width", "250px");
        return card;
    }

    private Image buildMonthlyTempTrendChart(JsonArray monthlyWeather) {
        try {
            List<Date> dates = new ArrayList<>();
            List<Double> tMax = new ArrayList<>();
            List<Double> tMin = new ArrayList<>();
            List<Double> tMean = new ArrayList<>();

            for (JsonElement e : monthlyWeather) {
                JsonObject day = e.getAsJsonObject();
                if (day.has("date") && !day.get("date").isJsonNull()) {
                    dates.add(Date.valueOf(LocalDate.parse(day.get("date").getAsString())));
                    tMax.add(day.has("temperature_2m_max") ? day.get("temperature_2m_max").getAsDouble() : Double.NaN);
                    tMin.add(day.has("temperature_2m_min") ? day.get("temperature_2m_min").getAsDouble() : Double.NaN);
                    tMean.add(day.has("temperature_2m_mean") ? day.get("temperature_2m_mean").getAsDouble() : Double.NaN);
                }
            }
            XYChart chart = new XYChartBuilder().width(600).height(400).title("Monthly Temperature Trend").xAxisTitle("Date").yAxisTitle("°C").build();
            chart.addSeries("Max Temp", dates, tMax);
            chart.addSeries("Min Temp", dates, tMin);
            chart.addSeries("Mean Temp", dates, tMean);
            chart.getStyler().setLegendVisible(true).setMarkerSize(4);
            return toVaadinImage(chart);
        } catch (Exception e) {
            e.printStackTrace();
            return new Image((String) null, "Failed to generate temperature trend chart");
        }
    }

    private Image buildMonthlyRainHumidityChart(JsonArray monthlyWeather) {
        try {
            List<String> dates = new ArrayList<>();
            List<Double> rain = new ArrayList<>();
            List<Double> humidity = new ArrayList<>();

            for (JsonElement e : monthlyWeather) {
                JsonObject day = e.getAsJsonObject();
                dates.add(day.has("date") ? day.get("date").getAsString() : "N/A");
                rain.add(day.has("rain_sum") ? day.get("rain_sum").getAsDouble() : 0.0);
                humidity.add(day.has("temperature_2m_mean") ? day.get("temperature_2m_mean").getAsDouble() : 0.0);
            }
            CategoryChart chart = new CategoryChartBuilder().width(600).height(400).title("Rain vs Temperature").xAxisTitle("Date").yAxisTitle("Amount").build();
            chart.addSeries("Rainfall (mm)", dates, rain);
            chart.addSeries("Mean Temp (°C)", dates, humidity);
            return toVaadinImage(chart);
        } catch (Exception e) {
            e.printStackTrace();
            return new Image((String) null, "Error generating Rain vs Temperature chart");
        }
    }

    private Image buildForecastRainChart(JsonArray forecastArray) {
        List<Date> dates = new ArrayList<>();
        List<Double> rainSum = new ArrayList<>();

        for (JsonElement e : forecastArray) {
            JsonObject day = e.getAsJsonObject();
            dates.add(Date.valueOf(LocalDate.parse(day.get("date").getAsString())));
            rainSum.add(day.get("rain_sum").getAsDouble());
        }
        XYChart rainChart = new XYChartBuilder()
                .width(700).height(400)
                .title("Rainfall Forecast")
                .xAxisTitle("Date")
                .yAxisTitle("Rainfall (mm)")
                .build();

        rainChart.addSeries("Rainfall", dates, rainSum);
        rainChart.getStyler().setLegendVisible(false).setMarkerSize(5);

        return toVaadinImage(rainChart);
    }

    private Image buildForecastTempChart(JsonArray forecastArray) {
        List<Date> dates = new ArrayList<>();
        List<Double> maxTemps = new ArrayList<>();
        List<Double> minTemps = new ArrayList<>();

        for (JsonElement e : forecastArray) {
            JsonObject day = e.getAsJsonObject();
            dates.add(Date.valueOf(LocalDate.parse(day.get("date").getAsString())));
            maxTemps.add(day.get("temperature_2m_max").getAsDouble());
            minTemps.add(day.get("temperature_2m_min").getAsDouble());
        }
        XYChart tempChart = new XYChartBuilder()
                .width(700).height(400)
                .title("7-Day Temperature Forecast")
                .xAxisTitle("Date")
                .yAxisTitle("Temperature (°C)")
                .build();

        tempChart.addSeries("Max Temp", dates, maxTemps);
        tempChart.addSeries("Min Temp", dates, minTemps);
        tempChart.getStyler().setLegendVisible(true).setMarkerSize(5);

        return toVaadinImage(tempChart);
    }

    private Image toVaadinImage(org.knowm.xchart.internal.chartpart.Chart<?, ?> chart) {
        try {
            byte[] bytes = BitmapEncoder.getBitmapBytes(chart, BitmapEncoder.BitmapFormat.PNG);
            StreamResource resource = new StreamResource("chart.png", () -> new ByteArrayInputStream(bytes));
            Image img = new Image(resource, "Chart");
            img.setWidth("600px");
            img.setHeight("400px");
            return img;
        } catch (Exception e) {
            e.printStackTrace();
            return new Image((String) null, "Chart generation failed");
        }
    }

    private String mapWeatherCode(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45 -> "Fog";
            case 48 -> "Depositing rime fog";
            case 51 -> "Light drizzle";
            case 53 -> "Moderate drizzle";
            case 55 -> "Dense drizzle";
            case 56 -> "Light freezing drizzle";
            case 57 -> "Dense freezing drizzle";
            case 61 -> "Slight rain";
            case 63 -> "Moderate rain";
            case 65 -> "Heavy rain";
            case 66 -> "Light freezing rain";
            case 67 -> "Heavy freezing rain";
            case 71 -> "Slight snowfall";
            case 73 -> "Moderate snowfall";
            case 75 -> "Heavy snowfall";
            case 77 -> "Snow grains";
            case 80 -> "Slight rain showers";
            case 81 -> "Moderate rain showers";
            case 82 -> "Violent rain showers";
            case 85 -> "Slight snow showers";
            case 86 -> "Heavy snow showers";
            case 95 -> "Slight or moderate thunderstorm";
            case 96 -> "Thunderstorm with slight hail";
            case 99 -> "Thunderstorm with heavy hail";
            default -> "Unknown";
        };
    }

    private String mapWeatherBoyImage(int code, boolean isDay) {
        String path = switch (code) {
            case 0, 1 -> "/images/sunnyboy";
            case 2, 3, 45, 48 -> "/images/cloudyboy";
            case 51, 53, 55, 61, 63, 65, 80, 81, 82 -> "/images/rainyboy";
            case 95, 96, 99 -> "/images/thunderstromboy";
            case 71, 73, 75, 77, 85, 86 -> "/images/snowyboy";
            case 56, 57, 66, 67 -> "/images/freezingboy";
            default -> "/images/cloudyboy";
        };
        return isDay ? path + ".png" : path + "night.png";
    }

    private String categorizeWindSpeed(double kmh) {
        if (kmh < 5) return "Calm";
        if (kmh < 20) return "Light Breeze";
        if (kmh < 40) return "Moderate Wind";
        if (kmh < 60) return "Strong Wind";
        return "Gale/Storm";
    }

    private static class WeatherCard extends Div {
        public WeatherCard(String imagePath, String title, String value) {
            getStyle()
                    .set("background-color", "#e0f2fe")
                    .set("color", "#0c4a6e")
                    .set("border-radius", "18px")
                    .set("padding", "1.5rem")
                    .set("box-shadow", "0 2px 6px rgba(0,0,0,0.2)")
                    .set("min-height", "160px")
                    .set("min-width", "280px")
                    .set("display", "flex")
                    .set("flex-direction", "row")
                    .set("align-items", "flex-start")
                    .set("flex", "1 1 auto");

            Image icon = new Image(imagePath, title);
            icon.setWidth("36px");
            icon.setHeight("36px");
            icon.getStyle().set("margin-right", "12px");

            VerticalLayout textLayout = new VerticalLayout();
            textLayout.setPadding(false);
            textLayout.setSpacing(false);
            textLayout.setWidthFull();

            Span titleSpan = new Span(title);
            titleSpan.getStyle().set("font-size", "14px").set("font-weight", "bold");

            Div valueContainer = new Div();
            valueContainer.getStyle()
                    .set("font-size", "16px")
                    .set("font-weight", "600")
                    .set("display", "flex")
                    .set("flex-direction", "column")
                    .set("gap", "4px")
                    .set("width", "100%");

            for (String line : value.split("<br>")) {
                valueContainer.add(new Span(line));
            }

            textLayout.add(titleSpan, valueContainer);
            add(icon, textLayout);
        }
    }
}