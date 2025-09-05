package com.myapp.weatheraqi.ui;

import com.google.gson.JsonObject;
import com.myapp.weatheraqi.backend.CurrentDataFetcher;
import com.myapp.weatheraqi.backend.WeatherForecastFetcher;
import com.myapp.weatheraqi.backend.HistoricalDataFetchHelper;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

public class WeatherView extends VerticalLayout {

    private final Div currentWeatherDiv;
    private final Div forecastDiv;
    private final Div historicalDiv;
    private final DatePicker historicalDatePicker;
    private String currentCity;
    private final Div heroSection;

    public WeatherView(String city) {
        this();
        updateWeatherForCity(city);
    }

    public WeatherView() {
        setSizeFull();
        setSpacing(true);

        add(new H2("Weather Information"));

        heroSection = new Div();
        heroSection.setWidthFull();   // full width of window
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

    // --- Add this helper method ---
    private void addCurrentWeatherHero(JsonObject weather, String city) {
        heroSection.removeAll(); // reset previous

        boolean isDay = weather.has("is_day") && weather.get("is_day").getAsString().equals("1");
        String bgImage = isDay ? "/images/cloud_day.jpg" : "/images/cloud_night.jpg";
        String textColor = isDay ? "#0c4a6e" : "#f1f5f9"; // dark blue for day, light for night

        heroSection.getStyle()
            .set("width", "100vw")  // full window width
            .set("min-height", "320px")
            .set("background-image", "url('" + bgImage + "')")
            .set("background-size", "cover")
            .set("background-position", "center")
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("justify-content", "space-between")
            .set("padding", "20px 40px")
            .set("box-shadow", "0 4px 10px rgba(0,0,0,0.3)")
            .set("color", textColor)
            .set("position", "relative"); // so child abs positioning works

        // --- Heading (city + condition) ---
        String description = weather.has("weather_code")
                ? mapWeatherCode(weather.get("weather_code").getAsInt())
                : "Unknown";
        H2 heading = new H2(city + " - Current Weather Condition:");
        heading.getStyle()
            .set("margin", "0")
            .set("font-size", "26px")
            .set("font-weight", "700")
            .set("color", textColor);

        heroSection.add(heading);

        // --- Middle Row: Temp (left) and Feels/Humidity (center), Boy Image (right) ---
        Div middleRow = new Div();
        middleRow.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "space-between")
                .set("width", "100%")
                .set("margin-top", "10px")
                .set("margin-bottom", "10px");

        // Left column: Big temperature
        Div leftCol = new Div();
        leftCol.getStyle().set("display", "flex").set("flex-direction", "column").set("align-items", "flex-start");

        String temp = weather.has("temperature_2m")
                ? weather.get("temperature_2m").getAsString() + " °C"
                : "--";
        Image tempIcon = new Image("/images/temp.jpg", "Temperature");
        tempIcon.setWidth("60px"); tempIcon.setHeight("60px");
        Span tempSpan = new Span(temp);
        tempSpan.getStyle()
                .set("font-size", "56px")
                .set("font-weight", "bold")
                .set("margin-left", "10px")
                .set("color", textColor);

        Div tempRow = new Div(tempIcon, tempSpan);
        tempRow.getStyle().set("display", "flex").set("align-items", "center");
        leftCol.add(tempRow);

        // Weather condition bold in separate background
        Span descSpan = new Span(description);
        descSpan.getStyle()
                .set("background", isDay ? "rgba(255,255,255,0.8)" : "rgba(0,0,0,0.5)")
                .set("padding", "4px 10px")
                .set("border-radius", "6px")
                .set("font-weight", "700")
                .set("margin-top", "8px")
                .set("color", textColor);
        leftCol.add(descSpan);

        // Center column: Feels Like + Humidity (centered)
        Div centerCol = new Div();
        centerCol.getStyle()
            .set("display", "flex")
            .set("flex-direction", "column")
            .set("align-items", "center")
            .set("justify-content", "center")
            .set("flex", "1");

        // Feels Like
        Div feelsDiv = new Div();
        feelsDiv.getStyle().set("display", "flex").set("align-items", "center").set("margin-bottom", "8px");
        Image feelsIcon = new Image("/images/feelslike.png", "Feels Like");
        feelsIcon.setWidth("28px"); feelsIcon.setHeight("28px");
        feelsIcon.getStyle().set("margin-right", "8px");
        String feelsLike = weather.has("apparent_temperature")
                ? weather.get("apparent_temperature").getAsString() + " °C"
                : "--";
        Span feelsText = new Span("Feels Like: " + feelsLike);
        feelsText.getStyle().set("color", textColor);
        feelsDiv.add(feelsIcon, feelsText);

        // Humidity
        Div humidityDiv = new Div();
        humidityDiv.getStyle().set("display", "flex").set("align-items", "center");
        Image humIcon = new Image("/images/humidity.png", "Humidity");
        humIcon.setWidth("28px"); humIcon.setHeight("28px");
        humIcon.getStyle().set("margin-right", "8px");
        String humidity = weather.has("relative_humidity_2m")
                ? weather.get("relative_humidity_2m").getAsString() + " %"
                : "--";
        Span humText = new Span("Humidity: " + humidity);
        humText.getStyle().set("color", textColor);
        humidityDiv.add(humIcon, humText);

        centerCol.add(feelsDiv, humidityDiv);

        // Right column: Boy Image (fills vertically to hero section height)
        String boyImgPath = weather.has("weather_code")
                ? mapWeatherBoyImage(weather.get("weather_code").getAsInt(), isDay)
                : (isDay ? "/images/cloudyboy.png" : "/images/cloudyboynight.png");

        Image boyImage = new Image(boyImgPath, "Weather Character");
        boyImage.getStyle()
                .set("height", "100%")   // scale to cloud height
                .set("max-height", "320px") // not bigger than container
                .set("object-fit", "contain")
                .set("margin-left", "20px");

        middleRow.add(leftCol, centerCol, boyImage);
        heroSection.add(middleRow);

        // --- Footer: Local Date & Time ---
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy HH:mm"));
        Span dateTime = new Span(now);
        dateTime.getStyle().set("font-size", "14px").set("opacity", "0.9").set("font-weight", "bold").set("color", textColor);
        heroSection.add(dateTime);
    }


    // Weather code mapping
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
        if (isDay) {
            return switch (code) {
                case 0, 1 -> "/images/sunnyboy.png";
                case 2, 3, 45, 48 -> "/images/cloudyboy.png";
                case 51, 53, 55, 61, 63, 65, 80, 81, 82 -> "/images/rainyboy.png";
                case 95, 96, 99 -> "/images/thunderstromboy.png";
                case 71, 73, 75, 77, 85, 86 -> "/images/snowyboy.png";
                case 56, 57, 66, 67 -> "/images/freezingboy.png";
                default -> "/images/cloudyboy.png";
            };
        } else {
            return switch (code) {
                case 0, 1 -> "/images/sunnyboynight.png";
                case 2, 3, 45, 48 -> "/images/cloudyboynight.png";
                case 51, 53, 55, 61, 63, 65, 80, 81, 82 -> "/images/rainyboynight.png";
                case 95, 96, 99 -> "/images/thunderstromboynight.png";
                case 71, 73, 75, 77, 85, 86 -> "/images/snowyboynight.png";
                case 56, 57, 66, 67 -> "/images/freezingboynight.png";
                default -> "/images/cloudyboynight.png";
            };
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
            .set("align-items", "flex-end")  // <-- push text to right
            .set("color", "white")
            .set("height", "180px")
            .set("box-shadow", "0 4px 10px rgba(0,0,0,0.3)");

        // Line 1 (bigger/bold)
        Span l1 = new Span(line1);
        l1.getStyle()
            .set("font-weight", "800")
            .set("font-size", "28px") // increased
            .set("margin-bottom", "10px");

        // Line 2 (smaller but still bigger)
        Span l2 = new Span(line2);
        l2.getStyle()
            .set("font-size", "20px") // increased
            .set("opacity", "0.95");

        panel.add(l1, l2);
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
        emojiSpan.getStyle()
            .set("font-size", "48px")   // much bigger emoji
            .set("margin-bottom", "8px");

        Span labelSpan = new Span(label);
        labelSpan.getStyle()
            .set("font-size", "20px")
            .set("font-weight", "600");

        Span valueSpan = new Span(value);
        valueSpan.getStyle()
            .set("font-size", "24px")
            .set("font-weight", "700");

        box.add(emojiSpan, labelSpan, valueSpan);
        return box;
    }


    private String categorizeWindSpeed(double kmh) {
        if (kmh < 5) return "Calm";
        if (kmh < 20) return "Light Breeze";
        if (kmh < 40) return "Moderate Wind";
        if (kmh < 60) return "Strong Wind";
        return "Gale/Storm";
    }


    private String categorizeUV(double uv) {
        if (uv < 3) return "Low";
        if (uv < 6) return "Moderate";
        if (uv < 8) return "High";
        if (uv < 11) return "Very High";
        return "Extreme";
    }


    public void updateWeatherForCity(String city) {
        if (city == null || city.isEmpty()) return;

        currentCity = city;

        heroSection.removeAll();
        currentWeatherDiv.removeAll();
        currentWeatherDiv.add(new H2("🌡 Current Weather"));

        try {
            JsonObject currentData = CurrentDataFetcher.getCurrentDataForCity(city);
            if (currentData != null && currentData.has("weather")) {
                JsonObject weather = currentData.getAsJsonObject("weather");

                // --- NEW: Hero section with cloud background ---
                addCurrentWeatherHero(weather, city);

                // --- Below Hero: 4 attribute boxes (2x2 grid) ---
                // --- Attribute Boxes Grid ---
                Div attributesGrid = new Div();
                attributesGrid.getStyle()
                    .set("display", "grid")
                    .set("grid-template-columns", "1fr 1fr")  // 2 columns
                    .set("gap", "16px")
                    .set("margin-top", "20px")
                    .set("width", "100%");


                    // 1. Wind
                if (weather.has("wind_speed_10m") && weather.has("wind_direction_10m")) {
                    double windMs = weather.get("wind_speed_10m").getAsDouble();
                    double windKmh = windMs * 3.6;
                    String category = categorizeWindSpeed(windKmh);
                    String windLine1 = "🌬 " + String.format("%.1f km/h", windKmh) + " (" + category + ")";
                    String windLine2 = "🧭 " + weather.get("wind_direction_10m").getAsString() + "°";
                    attributesGrid.add(createAttributePanel("/images/windmillbk.png", windLine1, windLine2));
                }

                // 2. Pressure
                if (weather.has("pressure_msl") && weather.has("surface_pressure")) {
                    String msl = "🌍 MSL: " + weather.get("pressure_msl").getAsString() + " hPa";
                    String surface = "🏔 Surface: " + weather.get("surface_pressure").getAsString() + " hPa";
                    attributesGrid.add(createAttributePanel("/images/pressurebk.png", msl, surface));
                }

                // 3. Rain & Precipitation (make it span 2 columns)
                if (weather.has("precipitation") && weather.has("rain")) {
                    String line1 = "☔ Rain: " + weather.get("rain").getAsString() + " mm";
                    String line2 = "💧 Precip: " + weather.get("precipitation").getAsString() + " mm";
                    Div rainBox = createAttributePanel("/images/precipitationbk.png", line1, line2);
                    rainBox.getStyle().set("grid-column", "span 2"); // <-- full row
                    attributesGrid.add(rainBox);
                }
                currentWeatherDiv.add(attributesGrid);
            }
 
            else {
                currentWeatherDiv.add(new Span("⚠️ No current weather data for " + city));
            }
        } catch (Exception e) {
            currentWeatherDiv.add(new Span("⚠️ Failed to load current weather: " + e.getMessage()));
        }

        forecastDiv.removeAll();
        try {
            JsonObject forecastData = WeatherForecastFetcher.getForecastDataForCity(city);
            if (forecastData != null && forecastData.has("forecast")) {
                forecastDiv.add(new H2("📅 7-day Forecast"));

                JsonObject daily = forecastData.getAsJsonObject("forecast");
                int days = daily.getAsJsonArray("time").size();

                // Wrapper container
                Div forecastWrapper = new Div();
                forecastWrapper.getStyle()
                    .set("position", "relative")
                    .set("margin-top", "20px")
                    .set("width", "100%")
                    .set("display", "flex")
                    .set("justify-content", "center")
                    .set("align-items", "center");

                // The card container (centered)
                Div cardContainer = new Div();
                cardContainer.getStyle()
                    .set("width", "100vw")              // full viewport width
                    .set("min-height", "320px")         // taller for readability
                    .set("background-image", "url('/images/forecastbk.png')")
                    .set("background-size", "cover")
                    .set("background-position", "center")
                    .set("border-radius", "0")          // no border curve, true edge-to-edge
                    .set("padding", "40px")             // space inside
                    .set("box-shadow", "inset 0 0 50px rgba(0,0,0,0.4)") // subtle fade effect
                    .set("display", "flex")
                    .set("flex-direction", "column")
                    .set("justify-content", "space-between")
                    .set("align-items", "center")
                    .set("overflow", "hidden");         // prevent overflow issues

                // Index tracker
                // Index tracker
                AtomicInteger currentIndex = new AtomicInteger(0);

                // --- Left Arrow ---
                Button leftArrow = new Button("◀");
                leftArrow.getStyle()
                    .set("position", "absolute")
                    .set("left", "20px")
                    .set("top", "50%")
                    .set("transform", "translateY(-50%)")
                    .set("z-index", "10")
                    .set("background", "rgba(255,255,255,0.7)")
                    .set("color", "#1e293b") // slate-800 text
                    .set("border-radius", "50%")
                    .set("width", "40px")
                    .set("height", "40px")
                    .set("box-shadow", "0 2px 6px rgba(0,0,0,0.3)");

                // --- Right Arrow ---
                Button rightArrow = new Button("▶");
                rightArrow.getStyle()
                    .set("position", "absolute")
                    .set("right", "20px")
                    .set("top", "50%")
                    .set("transform", "translateY(-50%)")
                    .set("z-index", "10")
                    .set("background", "rgba(255,255,255,0.7)")
                    .set("color", "#1e293b")
                    .set("border-radius", "50%")
                    .set("width", "40px")
                    .set("height", "40px")
                    .set("box-shadow", "0 2px 6px rgba(0,0,0,0.3)");

                // --- Method to update card ---
                Runnable updateCard = () -> {
                    int i = currentIndex.get();
                    cardContainer.removeAll();

                    String date = daily.getAsJsonArray("time").get(i).getAsString();
                    String max = daily.getAsJsonArray("temperature_2m_max").get(i).getAsString();
                    String min = daily.getAsJsonArray("temperature_2m_min").get(i).getAsString();
                    String rain = daily.getAsJsonArray("precipitation_sum").get(i).getAsString();
                    String sunrise = daily.getAsJsonArray("sunrise").get(i).getAsString().substring(11);
                    String sunset = daily.getAsJsonArray("sunset").get(i).getAsString().substring(11);
                    String wind = daily.getAsJsonArray("wind_speed_10m_max").get(i).getAsString();

                    // Title
                    H3 title = new H3("📅 " + date);
                    title.getStyle()
                        .set("margin", "0")
                        .set("margin-bottom", "32px")
                        .set("color", "white")
                        .set("font-weight", "900")
                        .set("font-size", "40px"); 

                    // Attributes row
                    Div attributesRow = new Div();
                    attributesRow.getStyle()
                        .set("display", "flex")
                        .set("justify-content", "space-around") // spread evenly
                        .set("width", "100%")
                        .set("max-width", "1200px")             // keep readable on large screens
                        .set("gap", "40px")
                        .set("color", "white")
                        .set("font-size", "28px");

                    attributesRow.add(makeForecastAttribute("🌡", "Max", max + " °C"));
                    attributesRow.add(makeForecastAttribute("❄", "Min", min + " °C"));
                    attributesRow.add(makeForecastAttribute("☔", "Rain", rain + " mm"));
                    attributesRow.add(makeForecastAttribute("🌅", "Sunrise", sunrise));
                    attributesRow.add(makeForecastAttribute("🌇", "Sunset", sunset));
                    attributesRow.add(makeForecastAttribute("💨", "Wind", wind + " m/s"));

                    cardContainer.add(title, attributesRow);

                    // Fade arrows if at ends
                    leftArrow.setEnabled(i > 0);
                    leftArrow.getStyle().set("opacity", i > 0 ? "1" : "0.3");

                    rightArrow.setEnabled(i < days - 1);
                    rightArrow.getStyle().set("opacity", i < days - 1 ? "1" : "0.3");
                };


                // --- Add listeners AFTER updateCard is defined ---
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

                // Initial card load
                updateCard.run();
                forecastWrapper.add(leftArrow, cardContainer, rightArrow);

                forecastDiv.add(forecastWrapper);

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
                String value = "Max: " + weather.get("temperature_2m_max").getAsString() + " °C<br>" +
                            "Min: " + weather.get("temperature_2m_min").getAsString() + " °C<br>" +
                            "Mean: " + weather.get("temperature_2m_mean").getAsString() + " °C";
                cardGrid.add(new WeatherCard("/images/temp.png", "Temperature", value));
            }


            // Feels Like Card
            if (weather.has("apparent_temperature_max") && weather.has("apparent_temperature_min") && weather.has("apparent_temperature_mean")) {
                String value = "Max: " + weather.get("apparent_temperature_max").getAsString() + " °C<br>" +
                            "Min: " + weather.get("apparent_temperature_min").getAsString() + " °C<br>" +
                            "Mean: " + weather.get("apparent_temperature_mean").getAsString() + " °C";
                cardGrid.add(new WeatherCard("/images/feelslike.png", "Feels Like", value));
            }

            // Rain / Precipitation
            if (weather.has("rain_sum") && weather.has("precipitation_sum")) {
                String value = "Rain: " + weather.get("rain_sum").getAsString() + " mm<br>" +
                            "Precipitation: " + weather.get("precipitation_sum").getAsString() + " mm";
                cardGrid.add(new WeatherCard("/images/rain.png", "Rain & Precipitation", value));
            }

        // Wind
        if (weather.has("wind_speed_10m_max") && weather.has("wind_gusts_10m_max") && weather.has("wind_direction_10m_dominant")) {
            String val = "Speed Max: " + weather.get("wind_speed_10m_max").getAsString() + " m/s<br>" +
                        "Gusts: " + weather.get("wind_gusts_10m_max").getAsString() + " m/s<br>" +
                        "Direction: " + weather.get("wind_direction_10m_dominant").getAsString() + "°";
            cardGrid.add(new WeatherCard("/images/wind.png", "Wind", val));
        }

            // Sun Cycle
            if (weather.has("sunrise") && weather.has("sunset") && weather.has("daylight_duration") && weather.has("sunshine_duration")) {
                String value = "Sunrise: " + weather.get("sunrise").getAsString() + "<br>" +
                            "Sunset: " + weather.get("sunset").getAsString() + "<br>" +
                            "Daylight: " + weather.get("daylight_duration").getAsString() + " min<br>" +
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
            getStyle().set("box-shadow", "0 2px 6px rgba(0,0,0,0.2)");
            getStyle().set("min-height", "160px");
            getStyle().set("min-width", "280px");
            getStyle().set("display", "flex")
                    .set("flex-direction", "row")
                    .set("align-items", "flex-start")
                    .set("flex", "1 1 auto");  // allow grid to arrange properly

            Image icon = new Image(imagePath, title);
            icon.setWidth("36px");
            icon.setHeight("36px");
            icon.getStyle().set("margin-right", "12px");

            VerticalLayout textLayout = new VerticalLayout();
            textLayout.setPadding(false);
            textLayout.setSpacing(false);
            textLayout.setWidthFull();

            // Title
            Span titleSpan = new Span(title);
            titleSpan.getStyle().set("font-size", "14px").set("font-weight", "bold");

            // Values on separate lines
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
