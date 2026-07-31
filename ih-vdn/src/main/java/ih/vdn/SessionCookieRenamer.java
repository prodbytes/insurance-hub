package ih.vdn;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * Renames the servlet session cookie from the default {@code JSESSIONID}.
 * Browsers scope cookies by host only, not port, so on localhost this app
 * (:8881), ih-audit (:8882) and Decision Control (:8880) would otherwise
 * overwrite each other's session cookie — each overwrite makes the next
 * Vaadin request here carry an unknown session id, which Vaadin treats as an
 * expired session and answers with a full page reload that swallows the click.
 */
@WebListener
public class SessionCookieRenamer implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        sce.getServletContext().getSessionCookieConfig().setName("IHVDNSESSION");
    }
}
