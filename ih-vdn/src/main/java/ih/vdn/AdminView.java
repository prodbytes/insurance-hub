package ih.vdn;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import software.xdev.vaadin.maps.leaflet.MapContainer;
import software.xdev.vaadin.maps.leaflet.basictypes.LLatLng;
import software.xdev.vaadin.maps.leaflet.layer.raster.LTileLayer;
import software.xdev.vaadin.maps.leaflet.layer.ui.LMarker;
import software.xdev.vaadin.maps.leaflet.map.LMap;
import software.xdev.vaadin.maps.leaflet.registry.LComponentManagementRegistry;
import software.xdev.vaadin.maps.leaflet.registry.LDefaultComponentManagementRegistry;

/**
 * Admin page showing an operations map rendered with Leaflet
 * (via the XDEV vaadin-maps-leaflet-flow extension).
 */
@PageTitle("InsuranceHub | Admin")
@Route("admin")
public class AdminView extends VerticalLayout {

    public AdminView() {
        setSizeFull();

        add(new H1("Admin"));

        LComponentManagementRegistry reg = new LDefaultComponentManagementRegistry(this);

        MapContainer mapContainer = new MapContainer(reg);
        mapContainer.setSizeFull();
        addAndExpand(mapContainer);

        LMap map = mapContainer.getlMap();
        map.addLayer(LTileLayer.createDefaultForOpenStreetMapTileServer(reg));

        var headquarters = new LLatLng(reg, 41.8781, -87.6298);
        map.setView(headquarters, 13);

        new LMarker(reg, headquarters)
                .bindPopup("InsuranceHub HQ")
                .addTo(map);
    }
}
