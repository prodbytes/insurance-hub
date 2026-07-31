package ih.audit;

import com.vaadin.quarkus.QuarkusVaadinServlet;

import jakarta.servlet.annotation.WebServlet;

@WebServlet(urlPatterns = "/app/*", name = "AppServlet", asyncSupported = true)
public class AppServlet extends QuarkusVaadinServlet {

}
