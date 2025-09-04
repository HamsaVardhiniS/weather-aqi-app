package com.myapp.weatheraqi.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

public class AqiView extends VerticalLayout {

    public AqiView() {
        setSizeFull();
        setSpacing(true);

        add(new H2("Air Quality Index (AQI)"));

        Div liveAqi = new Div();
        liveAqi.setText("Current AQI: 66 (Moderate)");

        Div pollutants = new Div();
        pollutants.setText("PM2.5: 14 µg/m³, PM10: 80 µg/m³");

        Div advisory = new Div();
        advisory.setText("Health Advisory: Sensitive groups should reduce outdoor activity.");

        add(liveAqi, pollutants, advisory);
    }
}
