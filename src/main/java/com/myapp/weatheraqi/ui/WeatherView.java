package com.myapp.weatheraqi.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class WeatherView extends VerticalLayout {

    public WeatherView() {
        setSizeFull();
        setSpacing(true);

        add(new H2("Weather Information"));

        Div currentWeather = new Div();
        currentWeather.setText("Temperature: 34°C, Partly Cloudy");

        Div forecast = new Div();
        forecast.setText("7-day forecast will be displayed here...");

        add(currentWeather, forecast);
    }
}
