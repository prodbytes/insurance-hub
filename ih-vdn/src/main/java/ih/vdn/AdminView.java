package ih.vdn;

import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import software.xdev.vaadin.maps.leaflet.MapContainer;
import software.xdev.vaadin.maps.leaflet.basictypes.LLatLng;
import software.xdev.vaadin.maps.leaflet.layer.raster.LTileLayer;
import software.xdev.vaadin.maps.leaflet.layer.ui.LMarker;
import software.xdev.vaadin.maps.leaflet.registry.LDefaultComponentManagementRegistry;

/**
 * Admin map page: a Leaflet map (via the XDEV vaadin-maps-leaflet-flow
 * extension) of the monitored fleet, with a side panel listing the latest
 * car telemetry events received. Events are sample data for now.
 */
@PageTitle("InsuranceHub | Admin")
@Route(value = "admin", layout = AdminLayout.class)
public class AdminView extends HorizontalLayout {

    private record TelemetryEvent(
            String vehicleId,
            String receivedAt,
            double lat,
            double lng,
            double accelerationMs2,
            double engineTempC) {
    }

    // Sample telemetry, positioned around Barcelona.
    private static final List<TelemetryEvent> SAMPLE_EVENTS = List.of(
            new TelemetryEvent("BCN-4821", "12:04:31", 41.4036, 2.1744, 1.8, 92.5),
            new TelemetryEvent("BCN-1177", "12:03:58", 41.3809, 2.1228, -6.4, 104.2),
            new TelemetryEvent("BCN-2903", "12:03:12", 41.3797, 2.1899, 0.3, 88.1));

    public AdminView() {
        addClassName("admin-view");
        setSizeFull();
        setPadding(false);
        setSpacing(false);

        var map = buildMap();
        add(map, buildEventsPanel());
        setFlexGrow(1, map);
    }

    private MapContainer buildMap() {
        var reg = new LDefaultComponentManagementRegistry(this);

        var mapContainer = new MapContainer(reg);
        mapContainer.addClassName("admin-view__map");
        mapContainer.setSizeFull();

        var map = mapContainer.getlMap();
        map.addLayer(LTileLayer.createDefaultForOpenStreetMapTileServer(reg));
        map.setView(new LLatLng(reg, 41.3874, 2.1686), 13); // Barcelona

        // The default marker images ship at the web root (META-INF/resources of the
        // add-on jar), but Leaflet resolves them relative to the page URL, which is
        // /app/* here — point it back at the root explicitly.
        reg.execJs("L.Icon.Default.imagePath='/'");

        for (var event : SAMPLE_EVENTS) {
            new LMarker(reg, new LLatLng(reg, event.lat(), event.lng()))
                    .bindPopup(event.vehicleId() + " · " + formatTemperature(event))
                    .addTo(map);
        }
        return mapContainer;
    }

    private VerticalLayout buildEventsPanel() {
        var title = new Span("RECEIVED EVENTS");
        title.addClassName("admin-events__title");

        var panel = new VerticalLayout(title);
        panel.addClassName("admin-events");
        panel.setWidth("360px");
        panel.setHeightFull();
        panel.setSpacing(false);

        SAMPLE_EVENTS.forEach(event -> panel.add(buildEventCard(event)));
        return panel;
    }

    private Div buildEventCard(TelemetryEvent event) {
        var vehicle = new Span(event.vehicleId());
        vehicle.addClassName("admin-event__vehicle");

        var time = new Span(event.receivedAt());
        time.addClassName("admin-event__time");

        var header = new Div(vehicle, time);
        header.addClassName("admin-event__header");

        var card = new Div(header,
                buildEventRow("Position", String.format(Locale.ROOT, "%.4f, %.4f", event.lat(), event.lng())),
                buildEventRow("Acceleration", String.format(Locale.ROOT, "%+.1f m/s²", event.accelerationMs2())),
                buildEventRow("Temperature", formatTemperature(event)));
        card.addClassName("admin-event");
        return card;
    }

    private Div buildEventRow(String label, String value) {
        var labelSpan = new Span(label);
        labelSpan.addClassName("admin-event__label");

        var valueSpan = new Span(value);
        valueSpan.addClassName("admin-event__value");

        var row = new Div(labelSpan, valueSpan);
        row.addClassName("admin-event__row");
        return row;
    }

    private static String formatTemperature(TelemetryEvent event) {
        return String.format(Locale.ROOT, "%.1f °C", event.engineTempC());
    }
}
