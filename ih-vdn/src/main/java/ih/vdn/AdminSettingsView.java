package ih.vdn;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/**
 * Admin settings page. Placeholder controls for now — values are not
 * persisted anywhere yet.
 */
@PageTitle("InsuranceHub | Admin Settings")
@Route(value = "admin/settings", layout = AdminLayout.class)
public class AdminSettingsView extends VerticalLayout {

    public AdminSettingsView() {
        addClassName("admin-settings");

        var title = new H2("Settings");

        var streamEvents = new Checkbox("Stream telemetry events", true);

        var retention = new IntegerField("Event retention (days)");
        retention.setValue(30);
        retention.setMin(1);
        retention.setStepButtonsVisible(true);

        var tileProvider = new Select<String>();
        tileProvider.setLabel("Map tile provider");
        tileProvider.setItems("OpenStreetMap");
        tileProvider.setValue("OpenStreetMap");

        var note = new Paragraph("These settings are placeholders and are not persisted yet.");
        note.addClassName("admin-settings__note");

        add(title, streamEvents, retention, tileProvider, note);
    }
}
