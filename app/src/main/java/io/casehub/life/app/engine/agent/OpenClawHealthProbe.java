package io.casehub.life.app.engine.agent;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

/**
 * Startup TCP reachability probe for the OpenClaw LLM backend.
 *
 * <p>Fires on startup in production only ({@code @IfBuildProfile("prod")}). Suppressed in
 * tests (default profile) and quarkus:dev (dev profile) — logs a warning in both contexts
 * would be noise. In dev mode, the first agent execution surfaces unreachability.
 *
 * <p>OpenClaw has no documented /health endpoint (GE-20260614-8c0371). A TCP connectivity
 * probe to host:port avoids probing an undocumented HTTP path. Failure is non-fatal — the
 * application starts regardless; the warning is a deployment signal.
 *
 * <p>casehub.life.openclaw.api-url is required — Quarkus throws ConfigException at startup
 * if absent (no defaultValue on LifeOpenClawChatModelProvider.apiUrl).
 */
@IfBuildProfile("prod")
@ApplicationScoped
public class OpenClawHealthProbe {

    private static final Logger LOG = Logger.getLogger(OpenClawHealthProbe.class);
    private static final int CONNECT_TIMEOUT_MS = 3000;

    @ConfigProperty(name = "casehub.life.openclaw.api-url")
    String apiUrl;

    void onStart(@Observes final StartupEvent event) {
        try {
            final URI uri = URI.create(apiUrl);
            final int port = uri.getPort() > 0
                    ? uri.getPort()
                    : ("https".equals(uri.getScheme()) ? 443 : 80);
            try (final Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(uri.getHost(), port), CONNECT_TIMEOUT_MS);
            }
            LOG.infof("OpenClaw reachable at %s (TCP)", apiUrl);
        } catch (final Exception e) {
            LOG.warnf("OpenClaw not reachable at %s — agent workers will fail on first "
                    + "invocation: %s", apiUrl, e.getMessage());
        }
    }
}
