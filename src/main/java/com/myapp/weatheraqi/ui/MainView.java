package com.myapp.weatheraqi.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.Route;

import java.util.Arrays;
import java.util.List;

@Route("")
public class MainView extends VerticalLayout {

    private final AqiView aqiView;
    private final WeatherView weatherView;
    private Component currentContent;

    public MainView() {
        setSizeFull();
        setSpacing(true);
        setPadding(true);

        // Title
        H1 title = new H1("City Dashboard");

        // City selector
        ComboBox<String> citySelector = new ComboBox<>("Select City");
        List<String> cities = Arrays.asList("Chennai", "Delhi", "Mumbai", "Bengaluru", "Kolkata");
        citySelector.setItems(cities);
        citySelector.setValue("Chennai"); // default

        // Views
        aqiView = new AqiView();
        weatherView = new WeatherView();

        // Tabs
        Tab aqiTab = new Tab("AQI 🌫️");
        Tab weatherTab = new Tab("Weather 🌞");
        Tabs tabs = new Tabs(aqiTab, weatherTab);

        // Default view
        currentContent = aqiView;

        tabs.addSelectedChangeListener(event -> {
            remove(currentContent);
            if (event.getSelectedTab() == aqiTab) {
                currentContent = aqiView;
            } else {
                currentContent = weatherView;
            }
            add(currentContent);
        });

        // Layout
        add(title, citySelector, tabs, currentContent);
    }
}
