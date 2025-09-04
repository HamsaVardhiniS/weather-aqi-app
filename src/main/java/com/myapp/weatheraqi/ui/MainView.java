package com.myapp.weatheraqi.ui;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Footer;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
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
    private final VerticalLayout contentWrapper;

    public MainView() {
        setSizeFull();
        setSpacing(false);
        setPadding(false);

        Header header = new Header();
        header.setWidthFull();
        header.getStyle()
                .set("background-color", "#0A2342")
                .set("color", "white")
                .set("padding", "1rem")
                .set("text-align", "center");
        H1 appName = new H1("🌦️ Weather & AQI App");
        appName.getStyle().set("margin", "0").set("color", "#FFD23F");
        header.add(appName);

        ComboBox<String> citySelector = new ComboBox<>("Select City");
        List<String> cities = Arrays.asList(
                "Amaravati", "Guwahati", "Patna", "Mumbai", "Ahmedabad",
                "Shimla", "Ranchi", "Bangalore", "Thiruvanthapuram",
                "Bhopal", "Chandigarh", "Jaipur", "Gangtok", "Chennai",
                "Hyderabad", "Lucknow", "Dehradun", "Kolkata", "Port Blair",
                "Srinagar", "New Delhi", "Puducherry"
        );
        citySelector.setItems(cities);
        citySelector.setValue("Chennai"); // default

        aqiView = new AqiView();
        weatherView = new WeatherView();

        weatherView.updateWeatherForCity(citySelector.getValue());
        aqiView.updateAqiForCity(citySelector.getValue());

        Tab aqiTab = new Tab("AQI 🌫️");
        Tab weatherTab = new Tab("Weather 🌞");
        Tabs tabs = new Tabs(aqiTab, weatherTab);

        contentWrapper = new VerticalLayout();
        contentWrapper.setSizeFull();
        contentWrapper.add(aqiView);

        tabs.addSelectedChangeListener(event -> {
            contentWrapper.removeAll();
            if (event.getSelectedTab() == aqiTab) {
                contentWrapper.add(aqiView);
            } else {
                contentWrapper.add(weatherView);
            }
        });

        citySelector.addValueChangeListener(event -> {
            String selectedCity = event.getValue();
            if (selectedCity != null) {
                weatherView.updateWeatherForCity(selectedCity);
                aqiView.updateAqiForCity(selectedCity);
            }
        });

        Footer footer = new Footer();
        footer.setWidthFull();
        footer.getStyle()
                .set("background-color", "#0A2342")
                .set("color", "white")
                .set("text-align", "center")
                .set("padding", "0.5rem");
        footer.add(new Span("© 2025 Weather & AQI App • Built with Spring Boot + Vaadin"));

        Div contentArea = new Div(citySelector, tabs, contentWrapper);
        contentArea.getStyle().set("padding", "1rem").set("flex", "1");

        add(header, contentArea, footer);
        expand(contentArea);
    }
}
