package id.co.jalin.seconsole.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SPA fallback — forwards known top-level SPA routes to {@code index.html}
 * so React Router can own client-side navigation.
 *
 * <p>This config uses an <strong>explicit whitelist</strong> instead of a
 * catch-all for a concrete reason: a catch-all ViewController competes with
 * Spring's static resource handler in the PathPatternParser resolution order,
 * and can end up forwarding asset requests ({@code /assets/app.js},
 * {@code /favicon.svg}) to index.html. The browser then receives HTML under
 * a {@code Content-Type: text/html} header and refuses to execute it as a
 * module, leaving the page blank with a MIME-type error in the console.
 *
 * <p>When adding a new top-level SPA route, append it to {@link #SPA_ROUTES}.
 * Nested routes under an already-listed prefix (e.g. {@code /channels/ch-01})
 * are covered by the {@code /**} variant.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** Top-level SPA route prefixes that must be forwarded to index.html. */
    private static final String[] SPA_ROUTES = {
        "/login",
        "/dashboard",
        "/channels",  "/channels/**",
        "/monitoring","/monitoring/**",
        "/config",    "/config/**",
        "/logs",
        "/audit",
        "/admin",     "/admin/**",
        "/profile"
    };

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Root: Spring Boot's welcome-page auto-config serves /index.html
        // automatically when static/index.html is on the classpath.
        // We deliberately do NOT register "/" here — that would collide
        // with the auto-configured welcome handler.

        for (String route : SPA_ROUTES) {
            registry.addViewController(route).setViewName("forward:/index.html");
        }
    }
}
