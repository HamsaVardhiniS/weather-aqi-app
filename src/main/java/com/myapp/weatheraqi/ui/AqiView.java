package com.myapp.weatheraqi.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.myapp.weatheraqi.backend.CurrentDataFetcher;
import com.myapp.weatheraqi.backend.HistoricalDataFetchHelper;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.renderer.ComponentRenderer;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@JavaScript("https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.0/chart.umd.min.js")
public class AqiView extends VerticalLayout {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/weather_aqi";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "Ritujaa@2006";

    private final Div liveAqiDiv;
    private final Div historicalDiv;
    private final DatePicker historicalDatePicker;
    private String currentCity;
    private final Div extremeCitiesContainer;
    private final Div cityRankingContainer;
    private Grid<CityAqiData> cityRankingGrid;
    private Div chartContainer; 

    public AqiView(String city) {
        this();
        updateAqiForCity(city);
    }
    
    public AqiView() {
        
    
        getStyle()
        .set("min-height", "100vh")           // full viewport height
        .set("display", "flex")
        .set("flex-direction", "column")
        .set("background-image", "url('images/bg.jpg')")
        .set("background-size", "cover")
        .set("background-position", "center center")
        .set("background-repeat", "no-repeat")
        .set("background-attachment", "fixed") // optional: keeps image fixed on scroll
        .set("overflow", "auto");

    // 🔹 General layout config
    setSizeFull();
    setSpacing(true);
    setPadding(true);


    H2 mainTitle = new H2("Air Quality Index (AQI)");
    mainTitle.getStyle()
            .set("color", "#2c3e50")
            .set("text-align", "center")
            .set("margin-bottom", "30px")
            .set("font-size", "2.5em");
    add(mainTitle);

    // ----- Extreme Cities Section -----
    extremeCitiesContainer = new Div();
    extremeCitiesContainer.getStyle()
            .set("margin-bottom", "40px")
            .set("width", "100%")
            .set("display", "flex")
            .set("justify-content", "center")
            .set("align-items", "center")
            .set("gap", "30px");
    add(extremeCitiesContainer);
    updateExtremeCitiesDisplay();

    // ----- Live AQI + Historical Section -----
    FlexLayout mainContentLayout = new FlexLayout();
mainContentLayout.setWidthFull();
mainContentLayout.setFlexWrap(FlexLayout.FlexWrap.WRAP);
mainContentLayout.setJustifyContentMode(FlexLayout.JustifyContentMode.CENTER);
mainContentLayout.getStyle()
        .set("gap", "30px")
        .set("align-items", "flex-start");

    FlexLayout wrapper = new FlexLayout();
wrapper.setWidthFull();
wrapper.setJustifyContentMode(FlexLayout.JustifyContentMode.CENTER);

liveAqiDiv = new Div();
liveAqiDiv.getStyle()
        .set("width", "500px") // can use % if you want responsive
        .set("background", "linear-gradient(135deg, #ffffffff 0%, #ffffffff 100%)")
        .set("padding", "20px")
        .set("border-radius", "15px")
        .set("box-shadow", "0 10px 30px rgba(0,0,0,0.1)");

wrapper.add(liveAqiDiv);
add(wrapper);


 
    // ----- Charts Section (Placed BELOW AQI Meter) -----
    VerticalLayout chartsSection = new VerticalLayout();
    chartsSection.setWidthFull();
    chartsSection.setSpacing(true);
    chartsSection.setPadding(true);
    chartsSection.getStyle()
            .set("margin-top", "40px")
            .set("align-items", "center");

    // Chart: 7-Day Pollution Trend
    chartContainer = new Div();
    chartContainer.getStyle()
            .set("width", "100%")
            .set("max-width", "900px")
            .set("margin", "0 auto")
            .set("padding", "25px")
            .set("background-color", "white")
            .set("border-radius", "15px")
            .set("box-shadow", "0 10px 30px rgba(0, 0, 0, 0.08)");

    H3 chartTitle = new H3("Last 7 Days Pollution Trends");
    chartTitle.getStyle()
            .set("color", "#2c3e50")
            .set("text-align", "center")
            .set("margin-bottom", "20px");
    chartContainer.add(chartTitle);

    Div canvasWrapper = new Div();
    canvasWrapper.getElement().setProperty("innerHTML",
            "<canvas id='aqiChart' style='width:100%; height:400px;'></canvas>");
    chartContainer.add(canvasWrapper);
    chartsSection.add(chartContainer);

    // Add other charts here (comparison, radar, etc.)
    createCurrentPollutantsChart();
    createPollutantComparisonChart();
    createRadarChart();
    add(chartsSection); 

       historicalDiv = new Div();
historicalDiv.getStyle()
        .set("flex", "1 1 500px")
        .set("max-width", "48%")
        .set("background", "white")
        .set("padding", "20px")
        .set("border-radius", "15px")
        .set("box-shadow", "0 10px 30px rgba(0,0,0,0.08)")
        .set("margin", "0 auto");        // centers horizontally

    VerticalLayout historicalSectionWrapper = new VerticalLayout();
historicalSectionWrapper.setPadding(false);
historicalSectionWrapper.setSpacing(true);
historicalSectionWrapper.getStyle()
        .set("width", "100%");

historicalDatePicker = new DatePicker("Select Historical Date");
historicalDatePicker.setWidthFull();
historicalDatePicker.addValueChangeListener(event -> {
    LocalDate selectedDate = event.getValue();
    if (selectedDate != null && currentCity != null) {
        updateHistoricalAqi(currentCity, selectedDate);
    }
});

historicalSectionWrapper.add(historicalDatePicker, historicalDiv);
mainContentLayout.add(historicalSectionWrapper);
add(mainContentLayout);

    cityRankingContainer = new Div();
    cityRankingContainer.getStyle()
            .set("margin-top", "40px")
            .set("padding", "30px")
            .set("background-color", "#1f6896ff")
            .set("border-radius", "15px")
            .set("box-shadow", "0 10px 30px rgba(0, 0, 0, 0.08)")
            .set("width", "100%");
    add(cityRankingContainer);
    updateCityRankingTable();

    // ----- Chart.js Load Verification -----
    getElement().executeJs(
            "setTimeout(() => {" +
                    "  console.log('Chart.js loaded:', typeof Chart !== 'undefined');" +
                    "  if (typeof Chart === 'undefined') {" +
                    "    console.error('Chart.js NOT loaded!');" +
                    "  } else {" +
                    "    console.log('Chart.js version:', Chart.version);" +
                    "  }" +
                    "}, 2000);"
    );
}



    public void updateAqiForCity(String city) {
    if (city == null || city.isEmpty()) return;
    currentCity = city;
    liveAqiDiv.removeAll();
    
    try {
        JsonObject data = CurrentDataFetcher.getCurrentDataForCity(city);
        if (data != null && data.has("aqi")) {
            JsonObject aqi = data.getAsJsonObject("aqi");
            H2 currentAqiHeader = new H2("Current AQI for " + city);
            currentAqiHeader.getStyle()
                    .set("color", "#34495e")
                    .set("margin-bottom", "25px")
                    .set("text-align", "center")
                    .set("font-size", "2em");
            liveAqiDiv.add(currentAqiHeader);

            double calculatedAqi = calculateIndianAQI(aqi);
            String aqiCategory = getAqiCategory(calculatedAqi);

            HorizontalLayout mainLayout = new HorizontalLayout();
            mainLayout.setSizeFull();
            mainLayout.setSpacing(true);
            mainLayout.getStyle().set("align-items", "flex-start");

            Div leftSide = createAqiMeter(calculatedAqi, aqiCategory);
            leftSide.getStyle()
                    .set("flex-shrink", "0")
                    .set("width", "35%");

            VerticalLayout rightSide = new VerticalLayout();
            rightSide.getStyle()
                    .set("width", "65%")
                    .set("padding", "0")
                    .set("spacing", "0");
            rightSide.setPadding(false);
            rightSide.setSpacing(true);

            Div rightHeader = new Div();
            rightHeader.getStyle()
                    .set("position", "relative")
                    .set("background", "linear-gradient(135deg, #667eea 0%, #667eea 0%)")
                    .set("color", "white")
                    .set("padding", "20px 25px")
                    .set("border-radius", "15px")
                    .set("margin-bottom", "20px")
                    .set("text-align", "center")
                    .set("box-shadow", "0 6px 20px rgba(102, 126, 234, 0.4)")
                    .set("overflow", "hidden");

            H3 rightTitle = new H3("Current Pollutant Details");
            rightTitle.getStyle()
                    .set("margin", "0")
                    .set("font-size", "20px")
                    .set("font-weight", "600")
                    .set("text-shadow", "0 2px 4px rgba(0, 0, 0, 0.3)");
            rightHeader.add(rightTitle);
            rightSide.add(rightHeader);

            if (data.has("time")) {
                addSuperAqiCard(rightSide, "Time", data.get("time").getAsString());
            }
            if (aqi.has("interval")) {
                addSuperAqiCard(rightSide, "Interval", aqi.get("interval").getAsString() + "s");
            }
            if (aqi.has("pm10")) {
                addSuperAqiCard(rightSide, "PM10", aqi.get("pm10").getAsString() + " μg/m³");
            }
            if (aqi.has("pm2_5")) {
                addSuperAqiCard(rightSide, "PM2.5", aqi.get("pm2_5").getAsString() + " μg/m³");
            }
            if (aqi.has("carbon_monoxide")) {
                addSuperAqiCard(rightSide, "CO", aqi.get("carbon_monoxide").getAsString() + " μg/m³");
            }
            if (aqi.has("nitrogen_dioxide")) {
                addSuperAqiCard(rightSide, "NO2", aqi.get("nitrogen_dioxide").getAsString() + " μg/m³");
            }
            if (aqi.has("ozone")) {
                addSuperAqiCard(rightSide, "Ozone", aqi.get("ozone").getAsString() + " μg/m³");
            }
            if (aqi.has("sulphur_dioxide")) {
                addSuperAqiCard(rightSide, "SO2", aqi.get("sulphur_dioxide").getAsString() + " μg/m³");
            }

            Div recommendationsDiv = createRecommendationsSection(aqi);
            rightSide.add(recommendationsDiv);

            mainLayout.add(leftSide, rightSide);
            liveAqiDiv.add(mainLayout);

            updateCurrentPollutantsChart(aqi);
            updatePollutantComparisonChart(aqi);
            updateRadarChart(aqi);
            
        } else {
            liveAqiDiv.add(new Span("No current AQI data for " + city));
        }
    } catch (Exception e) {
        liveAqiDiv.add(new Span("Failed to load current AQI: " + e.getMessage()));
        e.printStackTrace(); 
    }

    historicalDiv.removeAll();
    
Span selectDateSpan = new Span("Select a date to view historical AQI");
selectDateSpan.getStyle()
        .set("color", "#7f8c8d")
        .set("font-style", "italic")
        .set("font-size", "0.75em")      
        .set("text-align", "center")
        .set("display", "block")
        .set("margin", "10px auto");     

historicalDiv.add(selectDateSpan);


    updateChartForCity(city);
}
    

// CHART UPDATE METHOD (Line Chart for All Pollutants - Last 7 Days)
private void updateChartForCity(String city) {
    if (chartContainer == null || city == null) return;

    try {
        JsonObject chartData = HistoricalDataFetchHelper.fetchLast7DaysAqi(city);

        if (chartData == null || !chartData.has("dates")) {
            System.err.println("No chart data available for " + city);
            chartContainer.setVisible(false);
            return;
        }

        chartContainer.setVisible(true);

        JsonArray dates = chartData.getAsJsonArray("dates");
        JsonArray pm25 = chartData.getAsJsonArray("pm25");
        JsonArray pm10 = chartData.getAsJsonArray("pm10");
        JsonArray co   = chartData.getAsJsonArray("co");
        JsonArray no2  = chartData.has("no2") ? chartData.getAsJsonArray("no2") : new JsonArray();
        JsonArray so2  = chartData.has("so2") ? chartData.getAsJsonArray("so2") : new JsonArray();
        JsonArray o3   = chartData.has("o3")  ? chartData.getAsJsonArray("o3")  : new JsonArray();

        String datesStr = dates.toString();
        String pm25Str = pm25.toString();
        String pm10Str = pm10.toString();
        String coStr = co.toString();
        String no2Str = no2.toString();
        String so2Str = so2.toString();
        String o3Str = o3.toString();

        chartContainer.getElement().executeJs(
                "const canvas = document.getElementById('aqiChart');" +
                        "if (canvas && typeof Chart !== 'undefined') {" +
                        "  const ctx = canvas.getContext('2d');" +
                        "  if (window.aqiChartInstance) window.aqiChartInstance.destroy();" +

                        "  window.aqiChartInstance = new Chart(ctx, {" +
                        "    type: 'line'," +
                        "    data: {" +
                        "      labels: " + datesStr + "," +
                        "      datasets: [" +
                        "        { label: 'PM2.5 (μg/m³)', data: " + pm25Str + ", borderColor: 'rgba(255, 99, 132, 1)', backgroundColor: 'rgba(255,99,132,0.3)', borderWidth: 2, tension: 0.3, fill: false }," +
                        "        { label: 'PM10 (μg/m³)', data: " + pm10Str + ", borderColor: 'rgba(54, 162, 235, 1)', backgroundColor: 'rgba(54,162,235,0.3)', borderWidth: 2, tension: 0.3, fill: false }," +
                        "        { label: 'CO (μg/m³)', data: " + coStr + ", borderColor: 'rgba(255, 206, 86, 1)', backgroundColor: 'rgba(255,206,86,0.3)', borderWidth: 2, tension: 0.3, fill: false }," +
                        "        { label: 'NO₂ (μg/m³)', data: " + no2Str + ", borderColor: 'rgba(75, 192, 192, 1)', backgroundColor: 'rgba(75,192,192,0.3)', borderWidth: 2, tension: 0.3, fill: false }," +
                        "        { label: 'SO₂ (μg/m³)', data: " + so2Str + ", borderColor: 'rgba(153, 102, 255, 1)', backgroundColor: 'rgba(153,102,255,0.3)', borderWidth: 2, tension: 0.3, fill: false }," +
                        "        { label: 'O₃ (μg/m³)',  data: " + o3Str  + ", borderColor: 'rgba(255, 159, 64, 1)', backgroundColor: 'rgba(255,159,64,0.3)', borderWidth: 2, tension: 0.3, fill: false }" +
                        "      ]" +
                        "    }," +

                        "    options: {" +
                        "      responsive: true," +
                        "      maintainAspectRatio: false," +
                        "      plugins: {" +
                        "        legend: { position: 'top', labels: { font: { size: 12, weight: 'bold' }, usePointStyle: true } }," +
                        "        title: { display: true, text: 'Air Quality Trend - " + city + " (Past 7 Days)', font: { size: 16, weight: 'bold' } }" +
                        "      }," +
                        "      interaction: { mode: 'nearest', intersect: false }," +
                        "      scales: {" +
                        "        x: { grid: { display: false }, ticks: { font: { size: 11, weight: 'bold' } } }," +
                        "        y: { beginAtZero: true, title: { display: true, text: 'Concentration (μg/m³)', font: { size: 12, weight: 'bold' } } }" +
                        "      }," +
                        "      animation: { duration: 800, easing: 'easeInOutCubic' }" +
                        "    }" +
                        "  });" +
                        "} else {" +
                        "  console.error('Chart.js not loaded or canvas not found');" +
                        "}"
        );

    } catch (Exception e) {
        System.err.println("Error updating chart: " + e.getMessage());
        e.printStackTrace();
    }
}

    private Div currentPollutantsChartContainer;

private void createCurrentPollutantsChart() {
    currentPollutantsChartContainer = new Div();
    currentPollutantsChartContainer.getStyle()
            .set("width", "100%")
            .set("max-width", "900px")
            .set("margin", "30px auto")
            .set("padding", "25px")
            .set("background", "linear-gradient(135deg, #667eea 0%, #764ba2 100%)")
            .set("border-radius", "20px")
            .set("box-shadow", "0 15px 35px rgba(102, 126, 234, 0.4)");

    H3 chartTitle = new H3("Current Air Quality");
    chartTitle.getStyle()
            .set("color", "white")
            .set("text-align", "center")
            .set("margin-bottom", "20px")
            .set("text-shadow", "0 2px 4px rgba(0,0,0,0.3)");
    currentPollutantsChartContainer.add(chartTitle);

    Div canvasWrapper = new Div();
    canvasWrapper.getElement().setProperty("innerHTML",
            "<canvas id='currentPollutantsChart' style='width:100%; height:350px;'></canvas>");
    currentPollutantsChartContainer.add(canvasWrapper);
    
    add(currentPollutantsChartContainer);
}

private void updateCurrentPollutantsChart(JsonObject aqi) {
    System.out.println("Updating current pollutants chart...");
    System.out.println("AQI data: " + aqi);
    
    if (currentPollutantsChartContainer == null) {
        System.err.println("Chart container is NULL!");
        return;
    }
    if (aqi == null) {
        System.err.println("AQI data is NULL!");
        return;
    }
    
    
    if (currentPollutantsChartContainer == null || aqi == null) return;

    try {
        // Extract current pollutant values
        double pm25 = aqi.has("pm2_5") ? aqi.get("pm2_5").getAsDouble() : 0;
        double pm10 = aqi.has("pm10") ? aqi.get("pm10").getAsDouble() : 0;
        double co = aqi.has("carbon_monoxide") ? aqi.get("carbon_monoxide").getAsDouble() : 0;
        double no2 = aqi.has("nitrogen_dioxide") ? aqi.get("nitrogen_dioxide").getAsDouble() : 0;
        double so2 = aqi.has("sulphur_dioxide") ? aqi.get("sulphur_dioxide").getAsDouble() : 0;
        double o3 = aqi.has("ozone") ? aqi.get("ozone").getAsDouble() : 0;

        currentPollutantsChartContainer.getElement().executeJs(
                "const canvas = document.getElementById('currentPollutantsChart');" +
                        "if (canvas && typeof Chart !== 'undefined') {" +
                        "  const ctx = canvas.getContext('2d');" +
                        "  if (window.currentPollutantsChartInstance) {" +
                        "    window.currentPollutantsChartInstance.destroy();" +
                        "  }" +
                        "  window.currentPollutantsChartInstance = new Chart(ctx, {" +
                        "    type: 'doughnut'," +
                        "    data: {" +
                        "      labels: ['PM2.5', 'PM10', 'CO', 'NO2', 'SO2', 'O3']," +
                        "      datasets: [{" +
                        "        data: [" + pm25 + "," + pm10 + "," + (co/100) + "," + no2 + "," + so2 + "," + o3 + "]," +
                        "        backgroundColor: [" +
                        "          'rgba(255, 99, 132, 0.8)'," +
                        "          'rgba(54, 162, 235, 0.8)'," +
                        "          'rgba(255, 206, 86, 0.8)'," +
                        "          'rgba(75, 192, 192, 0.8)'," +
                        "          'rgba(153, 102, 255, 0.8)'," +
                        "          'rgba(255, 159, 64, 0.8)'" +
                        "        ]," +
                        "        borderColor: 'white'," +
                        "        borderWidth: 3," +
                        "        hoverOffset: 15" +
                        "      }]" +
                        "    }," +
                        "    options: {" +
                        "      responsive: true," +
                        "      maintainAspectRatio: false," +
                        "      plugins: {" +
                        "        legend: { " +
                        "          position: 'right'," +
                        "          labels: { color: 'white', font: { size: 14, weight: 'bold' }, padding: 15 }" +
                        "        }," +
                        "        title: { " +
                        "          display: true, " +
                        "          text: 'Current Pollutant Distribution'," +
                        "          color: 'white'," +
                        "          font: { size: 18, weight: 'bold' }" +
                        "        }," +
                        "        tooltip: {" +
                        "          callbacks: {" +
                        "            label: function(context) {" +
                        "              let label = context.label || '';" +
                        "              if (label === 'CO') {" +
                        "                return label + ': ' + (context.parsed * 100).toFixed(1) + ' μg/m³';" +
                        "              }" +
                        "              return label + ': ' + context.parsed.toFixed(1) + ' μg/m³';" +
                        "            }" +
                        "          }" +
                        "        }" +
                        "      }," +
                        "      animation: { animateRotate: true, animateScale: true }" +
                        "    }" +
                        "  });" +
                        "}"
        );
    } catch (Exception e) {
        System.err.println("Error updating current pollutants chart: " + e.getMessage());
    }
}


private Div pollutantComparisonChartContainer;

private void createPollutantComparisonChart() {
    pollutantComparisonChartContainer = new Div();
    pollutantComparisonChartContainer.getStyle()
            .set("width", "100%")
            .set("max-width", "900px")
            .set("margin", "30px auto")
            .set("padding", "25px")
            .set("background", "linear-gradient(135deg, #f093fb 0%, #f5576c 100%)")
            .set("border-radius", "20px")
            .set("box-shadow", "0 15px 35px rgba(245, 87, 108, 0.4)");

    H3 chartTitle = new H3("Pollutant Levels vs Safe Limits");
    chartTitle.getStyle()
            .set("color", "white")
            .set("text-align", "center")
            .set("margin-bottom", "20px")
            .set("text-shadow", "0 2px 4px rgba(0,0,0,0.3)");
    pollutantComparisonChartContainer.add(chartTitle);

    Div canvasWrapper = new Div();
    canvasWrapper.getElement().setProperty("innerHTML",
            "<canvas id='pollutantComparisonChart' style='width:100%; height:350px;'></canvas>");
    pollutantComparisonChartContainer.add(canvasWrapper);
    
    add(pollutantComparisonChartContainer);
}

private void updatePollutantComparisonChart(JsonObject aqi) {
    if (pollutantComparisonChartContainer == null || aqi == null) return;

    try {
        double pm25 = aqi.has("pm2_5") ? aqi.get("pm2_5").getAsDouble() : 0;
        double pm10 = aqi.has("pm10") ? aqi.get("pm10").getAsDouble() : 0;
        double co = aqi.has("carbon_monoxide") ? aqi.get("carbon_monoxide").getAsDouble() : 0;
        double no2 = aqi.has("nitrogen_dioxide") ? aqi.get("nitrogen_dioxide").getAsDouble() : 0;
        double so2 = aqi.has("sulphur_dioxide") ? aqi.get("sulphur_dioxide").getAsDouble() : 0;
        double o3 = aqi.has("ozone") ? aqi.get("ozone").getAsDouble() : 0;

        double pm25Limit = 15;
        double pm10Limit = 45;
        double coLimit = 4000;
        double no2Limit = 40;
        double so2Limit = 40;
        double o3Limit = 100;

        pollutantComparisonChartContainer.getElement().executeJs(
                "const canvas = document.getElementById('pollutantComparisonChart');" +
                        "if (canvas && typeof Chart !== 'undefined') {" +
                        "  const ctx = canvas.getContext('2d');" +
                        "  if (window.pollutantComparisonChartInstance) {" +
                        "    window.pollutantComparisonChartInstance.destroy();" +
                        "  }" +
                        "  window.pollutantComparisonChartInstance = new Chart(ctx, {" +
                        "    type: 'bar'," +
                        "    data: {" +
                        "      labels: ['PM2.5', 'PM10', 'CO', 'NO2', 'SO2', 'O3']," +
                        "      datasets: [{" +
                        "        label: 'Current Level'," +
                        "        data: [" + pm25 + "," + pm10 + "," + (co/100) + "," + no2 + "," + so2 + "," + o3 + "]," +
                        "        backgroundColor: 'rgba(255, 255, 255, 0.9)'," +
                        "        borderColor: 'rgba(255, 255, 255, 1)'," +
                        "        borderWidth: 2," +
                        "        borderRadius: 8" +
                        "      }, {" +
                        "        label: 'Safe Limit (WHO)'," +
                        "        data: [" + pm25Limit + "," + pm10Limit + "," + (coLimit/100) + "," + no2Limit + "," + so2Limit + "," + o3Limit + "]," +
                        "        backgroundColor: 'rgba(76, 175, 80, 0.7)'," +
                        "        borderColor: 'rgba(76, 175, 80, 1)'," +
                        "        borderWidth: 2," +
                        "        borderRadius: 8" +
                        "      }]" +
                        "    }," +
                        "    options: {" +
                        "      responsive: true," +
                        "      maintainAspectRatio: false," +
                        "      interaction: { mode: 'index', intersect: false }," +
                        "      plugins: {" +
                        "        legend: { " +
                        "          labels: { color: 'white', font: { size: 13, weight: 'bold' }, padding: 15 }" +
                        "        }," +
                        "        title: { " +
                        "          display: true, " +
                        "          text: 'How do current levels compare to WHO safe limits?'," +
                        "          color: 'white'," +
                        "          font: { size: 16, weight: 'bold' }" +
                        "        }," +
                        "        tooltip: {" +
                        "          backgroundColor: 'rgba(0, 0, 0, 0.9)'," +
                        "          callbacks: {" +
                        "            label: function(context) {" +
                        "              let label = context.dataset.label || '';" +
                        "              let value = context.parsed.y;" +
                        "              if (context.label === 'CO') value = value * 100;" +
                        "              return label + ': ' + value.toFixed(1) + ' μg/m³';" +
                        "            }" +
                        "          }" +
                        "        }" +
                        "      }," +
                        "      scales: {" +
                        "        x: { " +
                        "          grid: { color: 'rgba(255,255,255,0.1)' }," +
                        "          ticks: { color: 'white', font: { size: 12, weight: 'bold' } }" +
                        "        }," +
                        "        y: { " +
                        "          beginAtZero: true," +
                        "          grid: { color: 'rgba(255,255,255,0.1)' }," +
                        "          ticks: { color: 'white', font: { size: 11 } }," +
                        "          title: {" +
                        "            display: true," +
                        "            text: 'Concentration (μg/m³)'," +
                        "            color: 'white'," +
                        "            font: { size: 13, weight: 'bold' }" +
                        "          }" +
                        "        }" +
                        "      }" +
                        "    }" +
                        "  });" +
                        "}"
        );
    } catch (Exception e) {
        System.err.println("Error updating pollutant comparison chart: " + e.getMessage());
    }
}


private Div radarChartContainer;

private void createRadarChart() {
    radarChartContainer = new Div();
    radarChartContainer.getStyle()
            .set("width", "100%")
            .set("max-width", "900px")
            .set("margin", "30px auto")
            .set("padding", "25px")
            .set("background", "linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)")
            .set("border-radius", "20px")
            .set("box-shadow", "0 15px 35px rgba(79, 172, 254, 0.4)");

    H3 chartTitle = new H3("Air Quality - Radar chart");
    chartTitle.getStyle()
            .set("color", "white")
            .set("text-align", "center")
            .set("margin-bottom", "20px")
            .set("text-shadow", "0 2px 4px rgba(0,0,0,0.3)");
    radarChartContainer.add(chartTitle);

    Div canvasWrapper = new Div();
    canvasWrapper.getElement().setProperty("innerHTML",
            "<canvas id='radarChart' style='width:100%; height:400px;'></canvas>");
    radarChartContainer.add(canvasWrapper);
    
    add(radarChartContainer);
}

private void updateRadarChart(JsonObject aqi) {
    if (radarChartContainer == null || aqi == null) return;

    try {
        double pm25 = aqi.has("pm2_5") ? Math.min((aqi.get("pm2_5").getAsDouble() / 60) * 100, 100) : 0;
        double pm10 = aqi.has("pm10") ? Math.min((aqi.get("pm10").getAsDouble() / 100) * 100, 100) : 0;
        double co = aqi.has("carbon_monoxide") ? Math.min((aqi.get("carbon_monoxide").getAsDouble() / 10000) * 100, 100) : 0;
        double no2 = aqi.has("nitrogen_dioxide") ? Math.min((aqi.get("nitrogen_dioxide").getAsDouble() / 80) * 100, 100) : 0;
        double so2 = aqi.has("sulphur_dioxide") ? Math.min((aqi.get("sulphur_dioxide").getAsDouble() / 80) * 100, 100) : 0;
        double o3 = aqi.has("ozone") ? Math.min((aqi.get("ozone").getAsDouble() / 168) * 100, 100) : 0;

        radarChartContainer.getElement().executeJs(
                "const canvas = document.getElementById('radarChart');" +
                        "if (canvas && typeof Chart !== 'undefined') {" +
                        "  const ctx = canvas.getContext('2d');" +
                        "  if (window.radarChartInstance) {" +
                        "    window.radarChartInstance.destroy();" +
                        "  }" +
                        "  window.radarChartInstance = new Chart(ctx, {" +
                        "    type: 'radar'," +
                        "    data: {" +
                        "      labels: ['PM2.5', 'PM10', 'CO', 'NO2', 'SO2', 'Ozone']," +
                        "      datasets: [{" +
                        "        label: 'Pollution Level (%)'," +
                        "        data: [" + pm25 + "," + pm10 + "," + co + "," + no2 + "," + so2 + "," + o3 + "]," +
                        "        backgroundColor: 'rgba(255, 255, 255, 0.3)'," +
                        "        borderColor: 'rgba(255, 255, 255, 1)'," +
                        "        borderWidth: 3," +
                        "        pointBackgroundColor: 'rgba(255, 255, 255, 1)'," +
                        "        pointBorderColor: '#fff'," +
                        "        pointHoverBackgroundColor: '#fff'," +
                        "        pointHoverBorderColor: 'rgba(255, 255, 255, 1)'," +
                        "        pointRadius: 6," +
                        "        pointHoverRadius: 8" +
                        "      }]" +
                        "    }," +
                        "    options: {" +
                        "      responsive: true," +
                        "      maintainAspectRatio: false," +
                        "      plugins: {" +
                        "        legend: { " +
                        "          labels: { color: 'white', font: { size: 14, weight: 'bold' } }" +
                        "        }," +
                        "        title: { " +
                        "          display: true, " +
                        "          text: 'Normalized Pollutant Levels (0-100 scale)'," +
                        "          color: 'white'," +
                        "          font: { size: 16, weight: 'bold' }" +
                        "        }" +
                        "      }," +
                        "      scales: {" +
                        "        r: {" +
                        "          beginAtZero: true," +
                        "          max: 100," +
                        "          ticks: { color: 'white', backdropColor: 'transparent', font: { size: 11 } }," +
                        "          grid: { color: 'rgba(255, 255, 255, 0.3)' }," +
                        "          angleLines: { color: 'rgba(255, 255, 255, 0.3)' }," +
                        "          pointLabels: { color: 'white', font: { size: 13, weight: 'bold' } }" +
                        "        }" +
                        "      }" +
                        "    }" +
                        "  });" +
                        "}"
        );
    } catch (Exception e) {
        System.err.println("Error updating radar chart: " + e.getMessage());
    }
}
    
 private void updateHistoricalAqi(String city, LocalDate date) {
        historicalDiv.removeAll();
        try {
            JsonObject historicalData = HistoricalDataFetchHelper.fetchHistoricalData(city, date);
            if (historicalData == null || !historicalData.has("aqi")) {
                historicalDiv.add(new Span("No historical AQI for " + city + " on " + date));
                return;
            }
            JsonObject aqi = historicalData.getAsJsonObject("aqi");
            H2 historicalAqiHeader = new H2("Historical AQI (" + date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")) + ") for " + city);
            historicalAqiHeader.getStyle()
                    .set("color", "#34495e")
                    .set("margin-bottom", "25px")
                    .set("text-align", "center")
                    .set("font-size", "2em")
                    .set("animation", "fadeInUp 0.5s ease-out");
            historicalDiv.add(historicalAqiHeader);

            double calculatedAqi = calculateIndianAQI(aqi);
            String aqiCategory = getAqiCategory(calculatedAqi);

            HorizontalLayout mainLayout = new HorizontalLayout();
            mainLayout.setSizeFull();
            mainLayout.setSpacing(true);
            mainLayout.getStyle().set("align-items", "flex-start");

            Div leftSide = createAqiMeter(calculatedAqi, aqiCategory);
            leftSide.getStyle()
                    .set("flex-shrink", "0")
                    .set("width", "35%");

            VerticalLayout rightSide = new VerticalLayout();
            rightSide.getStyle()
                    .set("width", "65%")
                    .set("padding", "0")
                    .set("spacing", "0");
            rightSide.setPadding(false);
            rightSide.setSpacing(true);

            Div rightHeader = new Div();
            rightHeader.getStyle()
                    .set("background", "linear-gradient(135deg, #667eea 0%, #764ba2 100%)")
                    .set("color", "white")
                    .set("padding", "15px 20px")
                    .set("border-radius", "12px")
                    .set("margin-bottom", "20px")
                    .set("text-align", "center")
                    .set("box-shadow", "0 4px 15px rgba(102, 126, 234, 0.3)");
            H3 rightTitle = new H3("Historical Data & Trends");
            rightTitle.getStyle()
                    .set("margin", "0")
                    .set("font-size", "18px")
                    .set("font-weight", "600");
            rightHeader.add(rightTitle);
            rightSide.add(rightHeader);

            if (historicalData.has("time")) {
                addSuperAqiCard(rightSide, "Time", historicalData.get("time").getAsString());
            }
            if (aqi.has("interval")) {
                addSuperAqiCard(rightSide, "Interval", aqi.get("interval").getAsString() + "s");
            }
            if (aqi.has("pm10_max")) {
                addSuperAqiCard(rightSide, "PM10", aqi.get("pm10_max").getAsString() + " μg/m³");
            }
            if (aqi.has("pm25_max")) {
                addSuperAqiCard(rightSide, "PM2.5", aqi.get("pm25_max").getAsString() + " μg/m³");
            }
            if (aqi.has("co_max")) {
                addSuperAqiCard(rightSide, "CO", aqi.get("co_max").getAsString() + " μg/m³");
            }
            if (aqi.has("no2_max")) {
                addSuperAqiCard(rightSide, "NO2", aqi.get("no2_max").getAsString() + " μg/m³");
            }
            if (aqi.has("o3_max")) {
                addSuperAqiCard(rightSide, "Ozone", aqi.get("o3_max").getAsString() + " μg/m³");
            }
            if (aqi.has("so2_max")) {
                addSuperAqiCard(rightSide, "SO2", aqi.get("so2_max").getAsString() + " μg/m³");
            }

            Div historicalInsights = createHistoricalInsights(aqi, date);
            rightSide.add(historicalInsights);

            mainLayout.add(leftSide, rightSide);
            historicalDiv.add(mainLayout);
        } catch (Exception e) {
            historicalDiv.add(new Span("Failed to load historical AQI: " + e.getMessage()));
        }
    }
    private void updateExtremeCitiesDisplay() {
        extremeCitiesContainer.removeAll();
        H2 header = new H2("India's Air Quality Extremes 🇮🇳");
        header.getStyle()
                .set("color", "#2c3e50")
                .set("width", "100%")
                .set("text-align", "center")
                .set("margin-bottom", "25px")
                .set("font-size", "2em");
        extremeCitiesContainer.add(header);

        List<CityAqiData> data = fetchAllCitiesAqi();
        if (data.isEmpty()) {
            extremeCitiesContainer.add(new Span("No data available for extreme cities."));
            return;
        }

        CityAqiData mostAffected = data.stream()
                .max(Comparator.comparingDouble(CityAqiData::getAqiValue))
                .orElse(null);
        CityAqiData safest = data.stream()
                .min(Comparator.comparingDouble(CityAqiData::getAqiValue))
                .orElse(null);

        if (mostAffected != null) {
            extremeCitiesContainer.add(createCityExtremeCard("Most Affected City", mostAffected));
        }
        if (safest != null) {
            extremeCitiesContainer.add(createCityExtremeCard("Safest City", safest));
        }
    }

    private void updateCityRankingTable() {
        cityRankingContainer.removeAll();
        H2 header = new H2("City AQI Ranking (India)");
        header.getStyle()
                .set("color", "#2c3e50")
                .set("text-align", "center")
                .set("margin-bottom", "25px")
                .set("font-size", "2em");
        cityRankingContainer.add(header);

        List<CityAqiData> citiesData = fetchAllCitiesAqi();
        if (citiesData.isEmpty()) {
            cityRankingContainer.add(new Span("No data available for city ranking."));
            return;
        }

        citiesData.sort(Comparator.comparingDouble(CityAqiData::getAqiValue).reversed());

        cityRankingGrid = new Grid<>(CityAqiData.class, false);
        cityRankingGrid.addColumn(city -> citiesData.indexOf(city) + 1)
                .setHeader("Rank")
                .setFlexGrow(0)
                .setWidth("80px")
                .setTextAlign(ColumnTextAlign.CENTER);
        cityRankingGrid.addColumn(CityAqiData::getCityName)
                .setHeader("City")
                .setSortable(true)
                .setFlexGrow(1);
        cityRankingGrid.addColumn(city -> String.format("%.0f", city.getAqiValue()))
                .setHeader("AQI")
                .setSortable(true)
                .setFlexGrow(0)
                .setWidth("100px")
                .setTextAlign(ColumnTextAlign.CENTER);
        cityRankingGrid.addColumn(new ComponentRenderer<>(city -> {
            Span categorySpan = new Span(city.getCategory());
            categorySpan.getStyle()
                    .set("color", getAqiColor(city.getAqiValue()))
                    .set("font-weight", "bold");
            return categorySpan;
        }))
                .setHeader("Category")
                .setSortable(true)
                .setFlexGrow(1);

        cityRankingGrid.setItems(citiesData);
        cityRankingGrid.getStyle().set("min-height", "400px");
        cityRankingGrid.setAllRowsVisible(true);
        cityRankingContainer.add(cityRankingGrid);
    }

    private List<CityAqiData> fetchAllCitiesAqi() {
        List<CityAqiData> data = new ArrayList<>();
        List<String> cities = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT city_name FROM city")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    cities.add(rs.getString("city_name"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch cities from database: " + e.getMessage());
        }

        for (String city : cities) {
            try {
                JsonObject json = CurrentDataFetcher.getCurrentDataForCity(city);
                if (json != null && json.has("aqi")) {
                    JsonObject aqi = json.getAsJsonObject("aqi");
                    double calcAqi = calculateIndianAQI(aqi);
                    String cat = getAqiCategory(calcAqi);
                    data.add(new CityAqiData(city, calcAqi, cat, null));
                }
            } catch (Exception e) {
                System.err.println("Failed to fetch AQI for " + city + ": " + e.getMessage());
            }
        }
        return data;
    }

    private Component createCityExtremeCard(String cardTitle, CityAqiData cityData) {
        final Div card = new Div();
        card.getStyle()
                .set("width", "300px")
                .set("background-color", "white")
                .set("border-radius", "15px")
                .set("box-shadow", "0 6px 20px rgba(0, 0, 0, 0.1)")
                .set("padding", "20px")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("align-items", "center")
                .set("text-align", "center")
                .set("transition", "transform 0.3s ease, box-shadow 0.3s ease");

        card.getElement().addEventListener("mouseenter", e -> {
            card.getStyle()
                    .set("transform", "translateY(-8px)")
                    .set("box-shadow", "0 12px 30px rgba(0, 0, 0, 0.15)");
        });
        card.getElement().addEventListener("mouseleave", e -> {
            card.getStyle()
                    .set("transform", "translateY(0)")
                    .set("box-shadow", "0 6px 20px rgba(0, 0, 0, 0.1)");
        });

        H3 title = new H3(cardTitle);
        title.getStyle()
                .set("margin-top", "0")
                .set("margin-bottom", "15px")
                .set("color", "#333");
        card.add(title);

        Span cityName = new Span(cityData.getCityName().toUpperCase());
        cityName.getStyle()
                .set("font-size", "2.2em")
                .set("font-weight", "bold")
                .set("color", cardTitle.contains("Affected") ? "#e74c3c" : "#27ae60")
                .set("margin-bottom", "10px");
        card.add(cityName);

        HorizontalLayout aqiValueLayout = new HorizontalLayout();
        aqiValueLayout.setAlignItems(FlexComponent.Alignment.BASELINE);
        Span aqiVal = new Span(String.format("%.0f", cityData.getAqiValue()));
        aqiVal.getStyle()
                .set("font-size", "2.8em")
                .set("font-weight", "bold")
                .set("color", cardTitle.contains("Affected") ? "#e74c3c" : "#27ae60");
        Span aqiUnit = new Span(" AQI");
        aqiUnit.getStyle()
                .set("font-size", "1.2em")
                .set("color", "#7f8c8d");
        aqiValueLayout.add(aqiVal, aqiUnit);
        card.add(aqiValueLayout);

        HorizontalLayout categoryLayout = new HorizontalLayout();
        categoryLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        Span categoryDot = new Span();
        categoryDot.getStyle()
                .set("width", "12px")
                .set("height", "12px")
                .set("border-radius", "50%")
                .set("background-color", getAqiColor(cityData.getAqiValue()))
                .set("display", "inline-block");
        Span categoryText = new Span(cityData.getCategory());
        categoryText.getStyle()
                .set("font-size", "1.1em")
                .set("font-weight", "600")
                .set("color", "#555")
                .set("margin-left", "8px");
        categoryLayout.add(categoryDot, categoryText);
        card.add(categoryLayout);

        Div progressBarContainer = new Div();
        progressBarContainer.getStyle()
                .set("width", "100%")
                .set("height", "8px")
                .set("background-color", "#ecf0f1")
                .set("border-radius", "4px")
                .set("margin-top", "20px")
                .set("position", "relative");
        Div progressBarFill = new Div();
        double cappedAqi = Math.min(cityData.getAqiValue(), 500);
        double percentage = (cappedAqi / 500.0) * 100;
        progressBarFill.getStyle()
                .set("width", percentage + "%")
                .set("height", "100%")
                .set("background-color", getAqiColor(cityData.getAqiValue()))
                .set("border-radius", "4px")
                .set("transition", "width 0.5s ease");
        progressBarContainer.add(progressBarFill);
        Span maxAqiLabel = new Span("500");
        maxAqiLabel.getStyle()
                .set("position", "absolute")
                .set("right", "0")
                .set("bottom", "-20px")
                .set("font-size", "0.8em")
                .set("color", "#7f8c8d");
        progressBarContainer.add(maxAqiLabel);
        card.add(progressBarContainer);

        return card;
    }

    private void addSuperAqiCard(HasComponents parentLayout, String title, String value) {
        final Div cardContainer = new Div();
        cardContainer.getStyle()
                .set("position", "relative")
                .set("margin-bottom", "15px")
                .set("cursor", "pointer")
                .set("transition", "all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275)")
                .set("width", "100%")
                .set("animation", "fadeInUp 0.6s ease-out");
        
        cardContainer.getElement().addEventListener("mouseenter", e -> {
            cardContainer.getStyle()
                    .set("transform", "scale(1.03) translateX(-8px)")
                    .set("filter", "brightness(1.15)")
                    .set("box-shadow", "0 12px 30px rgba(0, 123, 204, 0.5)");
        });
        cardContainer.getElement().addEventListener("mouseleave", e -> {
            cardContainer.getStyle()
                    .set("transform", "scale(1) translateX(0)")
                    .set("filter", "brightness(1)")
                    .set("box-shadow", "0 4px 15px rgba(0, 123, 204, 0.2)");
        });

        Image backgroundImage = new Image("images/pollutants.png", "AQI Card Background");
        backgroundImage.getStyle()
                .set("width", "100%")
                .set("height", "80px")
                .set("object-fit", "cover")
                .set("border-radius", "10px")
                .set("display", "block")
                .set("position", "absolute")
                .set("top", "0")
                .set("left", "0")
                .set("z-index", "1")
                .set("transition", "transform 0.4s ease");
        
        backgroundImage.getElement().addEventListener("mouseenter", e -> {
            backgroundImage.getStyle().set("transform", "scale(1.05)");
        });
        backgroundImage.getElement().addEventListener("mouseleave", e -> {
            backgroundImage.getStyle().set("transform", "scale(1)");
        });

        Div contentContainer = new Div();
        contentContainer.getStyle()
                .set("position", "relative")
                .set("z-index", "3")
                .set("padding", "0 20px")
                .set("display", "flex")
                .set("justify-content", "space-between")
                .set("align-items", "center")
                .set("height", "80px");

        Span titleSpan = new Span(title);
        titleSpan.getStyle()
                .set("color", "white")
                .set("font-weight", "600")
                .set("font-size", "16px")
                .set("text-shadow", "0 2px 4px rgba(0, 0, 0, 0.4)")
                .set("transition", "all 0.3s ease");

        Span valueSpan = new Span(value);
        valueSpan.getStyle()
                .set("color", "white")
                .set("font-weight", "700")
                .set("font-size", "18px")
                .set("text-shadow", "0 2px 4px rgba(0, 0, 0, 0.4)")
                .set("transition", "all 0.3s ease");

        Span iconSpan = new Span(getHealthIcon(title));
        iconSpan.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("font-size", "24px")
                .set("margin-right", "10px")
                .set("transition", "transform 0.3s ease");
        
        iconSpan.getElement().addEventListener("mouseenter", e -> {
            iconSpan.getStyle().set("transform", "rotate(360deg) scale(1.2)");
        });
        iconSpan.getElement().addEventListener("mouseleave", e -> {
            iconSpan.getStyle().set("transform", "rotate(0deg) scale(1)");
        });

        HorizontalLayout leftContent = new HorizontalLayout();
        leftContent.setAlignItems(FlexComponent.Alignment.CENTER);
        leftContent.setSpacing(false);
        leftContent.add(iconSpan, titleSpan);
        contentContainer.add(leftContent, valueSpan);

        cardContainer.add(backgroundImage, contentContainer);
        parentLayout.add(cardContainer);

        if (isCriticalPollutant(title, value)) {
            cardContainer.getStyle()
                    .set("animation", "pulse 2s infinite, glow 2s ease-in-out infinite");
        }
    }


    private Image getHealthIcon(String pollutantType) {
    String imagePath;

    switch (pollutantType.toLowerCase()) {
        case "pm10":
            imagePath = "images/pm10.png";
            break;
        case "pm2.5":
            imagePath = "images/pm25.png";
            break;
        case "co":
            imagePath = "images/co.png";
            break;
        case "no2":
            imagePath = "images/no2.png";
            break;
        case "ozone":
        case "o3":
            imagePath = "images/ozone.png";
            break;
        case "so2":
            imagePath = "images/so2.png";
            break;
        case "time":
            imagePath = "images/interval.png";
            break;
        case "interval":
            imagePath = "images/interval.png";
            break;
        default:
            imagePath = "images/default.png";
            break;
    }

    Image icon = new Image(imagePath, pollutantType.toUpperCase());
    icon.setWidth("60px");
    icon.setHeight("60px");
    icon.getStyle().set("object-fit", "contain");
    return icon;
}


    private boolean isCriticalPollutant(String pollutantType, String value) {
        try {
            String numericValue = value.replaceAll("[^0-9.]", "");
            if (numericValue.isEmpty()) return false;
            double val = Double.parseDouble(numericValue);
            switch (pollutantType.toLowerCase()) {
                case "pm10":
                    return val > 100;
                case "pm2.5":
                    return val > 60;
                case "co":
                    return val > 4000;
                case "no2":
                    return val > 80;
                case "ozone":
                    return val > 168;
                case "so2":
                    return val > 80;
                default:
                    return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Div createAqiMeter(double aqiValue, String category) {
        Div meterContainer = new Div();
        meterContainer.getStyle()
                .set("background-color", "white")
                .set("border-radius", "20px")
                .set("padding", "30px")
                .set("box-shadow", "0 8px 25px rgba(0, 0, 0, 0.1)")
                .set("text-align", "center")
                .set("position", "relative");

        Div aqiDisplay = new Div();
        aqiDisplay.getStyle()
                .set("font-size", "4em")
                .set("font-weight", "bold")
                .set("color", getAqiColor(aqiValue))
                .set("margin-bottom", "15px")
                .set("text-shadow", "0 2px 4px rgba(0, 0, 0, 0.1)");
        aqiDisplay.add(new Span(String.format("%.0f", aqiValue)));
        Span aqiLabel = new Span("AQI");
        aqiLabel.getStyle()
                .set("font-size", "1.2em")
                .set("color", "#7f8c8d")
                .set("font-weight", "normal");
        aqiDisplay.add(aqiLabel);

        Span categorySpan = new Span(category);
        categorySpan.getStyle()
                .set("font-size", "1.5em")
                .set("font-weight", "600")
                .set("color", getAqiColor(aqiValue))
                .set("display", "block")
                .set("margin-bottom", "25px");

        Div meterVisual = createAqiGauge(aqiValue);

        Span healthAdvice = new Span(getHealthAdvice(aqiValue));
        healthAdvice.getStyle()
                .set("font-size", "0.9em")
                .set("color", "#555")
                .set("font-style", "italic")
                .set("margin-top", "20px")
                .set("display", "block")
                .set("line-height", "1.4");

        Image boyImage = new Image("images/boy_" + category.toLowerCase().replace(" ", "_") + ".png", "AQI Mascot");
        boyImage.getStyle()
                .set("width", "100px")
                .set("height", "auto")
                .set("margin-top", "20px")
                .set("display", "block")
                .set("margin-left", "auto")
                .set("margin-right", "auto");

        meterContainer.add(aqiDisplay, categorySpan, meterVisual, healthAdvice, boyImage);
        return meterContainer;
    }

    private Div createAqiGauge(double aqiValue) {
        Div gaugeContainer = new Div();
        gaugeContainer.getStyle()
                .set("width", "200px")
                .set("height", "110px")
                .set("margin", "20px auto")
                .set("position", "relative");

        Div gaugeBg = new Div();
        gaugeBg.getStyle()
                .set("width", "200px")
                .set("height", "100px")
                .set("border", "20px solid #ecf0f1")
                .set("border-bottom", "none")
                .set("border-radius", "200px 200px 0 0")
                .set("position", "relative");

        String[] colors = {"#00e400", "#ffff00", "#ff7e00", "#ff0000", "#8f3f97", "#7e0023"};
        double[] ranges = {50, 100, 150, 200, 300, 500};
        for (int i = 0; i < colors.length; i++) {
            Div segment = new Div();
            double startAngle = (i == 0) ? 0 : (ranges[i-1] / 500.0) * 180;
            double endAngle = (ranges[i] / 500.0) * 180;
            double segmentWidth = endAngle - startAngle;
            segment.getStyle()
                    .set("position", "absolute")
                    .set("width", "160px")
                    .set("height", "80px")
                    .set("border", "20px solid " + colors[i])
                    .set("border-bottom", "none")
                    .set("border-radius", "160px 160px 0 0")
                    .set("transform", "rotate(" + startAngle + "deg)")
                    .set("transform-origin", "50% 100%")
                    .set("clip-path", "polygon(50% 100%, 0% 0%, " + (segmentWidth/180*100) + "% 0%)");
            gaugeBg.add(segment);
        }

        Div needle = new Div();
        double needleAngle = Math.min((aqiValue / 500.0) * 180, 180);
        needle.getStyle()
                .set("position", "absolute")
                .set("bottom", "0px")
                .set("left", "calc(50% - 2px)")
                .set("width", "4px")
                .set("height", "100px")
                .set("background-color", "#2c3e50")
                .set("transform-origin", "bottom center")
                .set("transform", "rotate(" + (needleAngle - 90) + "deg)")
                .set("border-radius", "2px 2px 0 0")
                .set("transition", "transform 1s ease-in-out");

        Div centerDot = new Div();
        centerDot.getStyle()
                .set("position", "absolute")
                .set("bottom", "-10px")
                .set("left", "calc(50% - 10px)")
                .set("width", "20px")
                .set("height", "20px")
                .set("background-color", "#2c3e50")
                .set("border-radius", "50%");

        gaugeContainer.add(gaugeBg, needle, centerDot);
        return gaugeContainer;
    }

    private Div createRecommendationsSection(JsonObject aqi) {
        Div recommendationsDiv = new Div();
        recommendationsDiv.getStyle()
                .set("background", "linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%)")
                .set("border-radius", "12px")
                .set("padding", "20px")
                .set("margin-top", "20px")
                .set("border-left", "4px solid #3498db");

        H3 recTitle = new H3("Health Recommendations");
        recTitle.getStyle()
                .set("margin-top", "0")
                .set("color", "#2c3e50")
                .set("font-size", "16px");

        double overallAqi = calculateIndianAQI(aqi);
        VerticalLayout recList = new VerticalLayout();
        recList.setPadding(false);
        recList.setSpacing(false);
        String[] recommendations = getRecommendations(overallAqi);
        for (String rec : recommendations) {
            Div recItem = new Div();
            recItem.getStyle()
                    .set("padding", "8px 0")
                    .set("border-bottom", "1px solid rgba(0, 0, 0, 0.1)")
                    .set("color", "#555");
            recItem.add(new Span("• " + rec));
            recList.add(recItem);
        }

        recommendationsDiv.add(recTitle, recList);
        return recommendationsDiv;
    }

    private Div createHistoricalInsights(JsonObject aqi, LocalDate date) {
        Div insightsDiv = new Div();
        insightsDiv.getStyle()
                .set("background", "linear-gradient(135deg, #ffeaa7 0%, #fab1a0 100%)")
                .set("border-radius", "12px")
                .set("padding", "20px")
                .set("margin-top", "20px")
                .set("border-left", "4px solid #e17055")
                .set("animation", "fadeInUp 1.3s ease-out")
                .set("transition", "transform 0.3s ease");

        insightsDiv.getElement().addEventListener("mouseenter", e -> {
            insightsDiv.getStyle().set("transform", "translateX(5px)");
        });
        insightsDiv.getElement().addEventListener("mouseleave", e -> {
            insightsDiv.getStyle().set("transform", "translateX(0)");
        });

        H3 insightTitle = new H3("Historical Analysis");
        insightTitle.getStyle()
                .set("margin-top", "0")
                .set("color", "#2c3e50")
                .set("font-size", "16px");

        Div insight = new Div();
        insight.getStyle()
                .set("color", "#555")
                .set("line-height", "1.5");

        String dayOfWeek = date.getDayOfWeek().toString();
        String monthName = date.getMonth().toString();
        insight.add(new Span("Data from " + dayOfWeek.toLowerCase() + " in " + monthName.toLowerCase() + ". "));
        insight.add(new Span("Compare with current readings to track air quality trends. "));
        double historicalAqi = calculateIndianAQI(aqi);
        if (historicalAqi > 150) {
            insight.add(new Span("This was a poor air quality day - avoid outdoor activities."));
        } else if (historicalAqi < 50) {
            insight.add(new Span("This was a good air quality day - perfect for outdoor activities."));
        }

        insightsDiv.add(insightTitle, insight);
        return insightsDiv;
    }
    private double calculateIndianAQI(JsonObject aqi) {
        double maxAqi = 0;
        double pm25 = aqi.has("pm2_5") ? aqi.get("pm2_5").getAsDouble() : (aqi.has("pm25_max") ? aqi.get("pm25_max").getAsDouble() : 0);
        if (pm25 > 0) {
            maxAqi = Math.max(maxAqi, calculatePM25AQI(pm25));
        }
        double pm10 = aqi.has("pm10") ? aqi.get("pm10").getAsDouble() : (aqi.has("pm10_max") ? aqi.get("pm10_max").getAsDouble() : 0);
        if (pm10 > 0) {
            maxAqi = Math.max(maxAqi, calculatePM10AQI(pm10));
        }
        double ozone = aqi.has("ozone") ? aqi.get("ozone").getAsDouble() : (aqi.has("o3_max") ? aqi.get("o3_max").getAsDouble() : 0);
        if (ozone > 0) {
            maxAqi = Math.max(maxAqi, calculateOzoneAQI(ozone));
        }
        double co = aqi.has("carbon_monoxide") ? aqi.get("carbon_monoxide").getAsDouble() : (aqi.has("co_max") ? aqi.get("co_max").getAsDouble() : 0);
        if (co > 0) {
            maxAqi = Math.max(maxAqi, calculateCOAQI(co));
        }
        double so2 = aqi.has("sulphur_dioxide") ? aqi.get("sulphur_dioxide").getAsDouble() : (aqi.has("so2_max") ? aqi.get("so2_max").getAsDouble() : 0);
        if (so2 > 0) {
            maxAqi = Math.max(maxAqi, calculateSO2AQI(so2));
        }
        double no2 = aqi.has("nitrogen_dioxide") ? aqi.get("nitrogen_dioxide").getAsDouble() : (aqi.has("no2_max") ? aqi.get("no2_max").getAsDouble() : 0);
        if (no2 > 0) {
            maxAqi = Math.max(maxAqi, calculateNO2AQI(no2));
        }
        return maxAqi;
    }

    private double calculatePM25AQI(double pm25) {
        if (pm25 <= 30) return linearInterpolation(pm25, 0, 30, 0, 50);
        else if (pm25 <= 60) return linearInterpolation(pm25, 30, 60, 51, 100);
        else if (pm25 <= 90) return linearInterpolation(pm25, 60, 90, 101, 200);
        else if (pm25 <= 120) return linearInterpolation(pm25, 90, 120, 201, 300);
        else if (pm25 <= 250) return linearInterpolation(pm25, 120, 250, 301, 400);
        else return linearInterpolation(pm25, 250, 380, 401, 500);
    }

    private double calculatePM10AQI(double pm10) {
        if (pm10 <= 50) return linearInterpolation(pm10, 0, 50, 0, 50);
        else if (pm10 <= 100) return linearInterpolation(pm10, 50, 100, 51, 100);
        else if (pm10 <= 250) return linearInterpolation(pm10, 100, 250, 101, 200);
        else if (pm10 <= 350) return linearInterpolation(pm10, 250, 350, 201, 300);
        else if (pm10 <= 430) return linearInterpolation(pm10, 350, 430, 301, 400);
        else return linearInterpolation(pm10, 430, 510, 401, 500);
    }

    private double calculateOzoneAQI(double ozone) {
        if (ozone <= 100) return linearInterpolation(ozone, 0, 100, 0, 50);
        else if (ozone <= 168) return linearInterpolation(ozone, 100, 168, 51, 100);
        else if (ozone <= 208) return linearInterpolation(ozone, 168, 208, 101, 200);
        else if (ozone <= 748) return linearInterpolation(ozone, 208, 748, 201, 300);
        else return 400;
    }

    private double calculateCOAQI(double co) {
        if (co <= 1000) return linearInterpolation(co, 0, 1000, 0, 50);
        else if (co <= 2000) return linearInterpolation(co, 1000, 2000, 51, 100);
        else if (co <= 10000) return linearInterpolation(co, 2000, 10000, 101, 200);
        else if (co <= 17000) return linearInterpolation(co, 10000, 17000, 201, 300);
        else if (co <= 34000) return linearInterpolation(co, 17000, 34000, 301, 400);
        else return linearInterpolation(co, 34000, 46000, 401, 500);
    }

    private double calculateSO2AQI(double so2) {
        if (so2 <= 40) return linearInterpolation(so2, 0, 40, 0, 50);
        else if (so2 <= 80) return linearInterpolation(so2, 40, 80, 51, 100);
        else if (so2 <= 380) return linearInterpolation(so2, 80, 380, 101, 200);
        else if (so2 <= 800) return linearInterpolation(so2, 380, 800, 201, 300);
        else if (so2 <= 1600) return linearInterpolation(so2, 800, 1600, 301, 400);
        else return linearInterpolation(so2, 1600, 2100, 401, 500);
    }

    private double calculateNO2AQI(double no2) {
        if (no2 <= 40) return linearInterpolation(no2, 0, 40, 0, 50);
        else if (no2 <= 80) return linearInterpolation(no2, 40, 80, 51, 100);
        else if (no2 <= 180) return linearInterpolation(no2, 80, 180, 101, 200);
        else if (no2 <= 280) return linearInterpolation(no2, 180, 280, 201, 300);
        else if (no2 <= 400) return linearInterpolation(no2, 280, 400, 301, 400);
        else return linearInterpolation(no2, 400, 500, 401, 500);
    }

    private double linearInterpolation(double value, double x1, double x2, double y1, double y2) {
        return ((value - x1) / (x2 - x1)) * (y2 - y1) + y1;
    }

    private String getAqiCategory(double aqiValue) {
        if (aqiValue <= 50) return "Good";
        else if (aqiValue <= 100) return "Satisfactory";
        else if (aqiValue <= 200) return "Moderate";
        else if (aqiValue <= 300) return "Poor";
        else if (aqiValue <= 400) return "Very Poor";
        else return "Severe";
    }

    private String getAqiColor(double aqi) {
        if (aqi <= 50) return "#00e400";
        else if (aqi <= 100) return "#ffff00";
        else if (aqi <= 200) return "#ff7e00";
        else if (aqi <= 300) return "#ff0000";
        else if (aqi <= 400) return "#8f3f97";
        else return "#7e0023";
    }

    private String getHealthAdvice(double aqi) {
        if (aqi <= 50) return "Minimal impact. Enjoy your day!";
        else if (aqi <= 100) return "Minor breathing discomfort to sensitive people. It's generally safe.";
        else if (aqi <= 200) return "Breathing discomfort to people with asthma, heart diseases, and children. Be mindful of outdoor exertion.";
        else if (aqi <= 300) return "Breathing discomfort to most people on prolonged exposure. Limit your outdoor time.";
        else if (aqi <= 400) return "Respiratory illness on prolonged exposure. Avoid prolonged outdoor activities.";
        else return "Severe respiratory effects. Everyone should avoid outdoor physical activity. Stay indoors and use air purifiers.";
    }

    private String[] getRecommendations(double aqi) {
        if (aqi <= 50) {
            return new String[]{
                    "Enjoy outdoor activities as normal.",
                    "Windows can be kept open for fresh air."
            };
        } else if (aqi <= 100) {
            return new String[]{
                    "Take a brisk walk but avoid jogging.",
                    "Limit outdoor activities for sensitive groups."
            };
        } else if (aqi <= 200) {
            return new String[]{
                    "People with asthma should avoid outdoor activities.",
                    "Consider wearing a mask if going outside.",
                    "Keep doors and windows closed."
            };
        } else if (aqi <= 300) {
            return new String[]{
                    "Avoid all outdoor physical activity.",
                    "Keep doors and windows closed.",
                    "Use air purifiers indoors."
            };
        } else {
            return new String[]{
                    "Stay indoors and wear a mask at all times.",
                    "Avoid all physical exertion outdoors.",
                    "Seek medical advice if you feel unwell."
            };
        }
    }

    public static class CityAqiData {
        private String cityName;
        private double aqiValue;
        private String category;
        private String imageUrl;

        public CityAqiData(String cityName, double aqiValue, String category, String imageUrl) {
            this.cityName = cityName;
            this.aqiValue = aqiValue;
            this.category = category;
            this.imageUrl = imageUrl;
        }

        public String getCityName() { return cityName; }
        public double getAqiValue() { return aqiValue; }
        public String getCategory() { return category; }
        public String getImageUrl() { return imageUrl; }
        public void setCityName(String cityName) { this.cityName = cityName; }
        public void setAqiValue(double aqiValue) { this.aqiValue = aqiValue; }
        public void setCategory(String category) { this.category = category; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    }
}