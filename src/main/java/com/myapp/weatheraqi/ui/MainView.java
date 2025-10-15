package com.myapp.weatheraqi.ui;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
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
                "Shimla", "Ranchi", "Bangalore", "Thiruvananthapuram",
                "Bhopal", "Chandigarh", "Jaipur", "Gangtok", "Chennai",
                "Hyderabad", "Lucknow", "Dehradun", "Kolkata", "Port Blair",
                "Srinagar", "New Delhi", "Puducherry"
        );
        citySelector.setItems(cities);
        citySelector.setValue("Chennai");

        aqiView = new AqiView();
        weatherView = new WeatherView();
        
        weatherView.updateWeatherForCity(citySelector.getValue());
        aqiView.updateAqiForCity(citySelector.getValue());

        Tab aqiTab = new Tab("AQI");
        Tab weatherTab = new Tab("Weather");
        Tabs tabs = new Tabs(aqiTab, weatherTab);
        tabs.setSelectedTab(aqiTab);

        contentWrapper = new VerticalLayout();
        contentWrapper.setSizeFull();
        contentWrapper.setPadding(false);
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
            if (selectedCity == null || selectedCity.trim().isEmpty()) {
                return;
            }

            Notification notification = Notification.show("Fetching new data for " + selectedCity + "...", 3000, Notification.Position.BOTTOM_CENTER);
            notification.addThemeVariants(NotificationVariant.LUMO_CONTRAST);

            weatherView.updateWeatherForCity(selectedCity);
            aqiView.updateAqiForCity(selectedCity);
        });

        Div controls = new Div(citySelector, tabs);
        controls.getStyle()
                .set("padding-left", "1rem")
                .set("padding-right", "1rem");

        Div contentArea = new Div(controls, contentWrapper);
        contentArea.getStyle()
                .set("padding-top", "1rem")
                .set("flex", "1");
        contentArea.setSizeFull();

        add(header, contentArea);
        expand(contentArea);
    }
}