package dev.brenorb.escriba;
import java.net.URI;
public final class EndpointPolicy {
    public static boolean valid(String value) {
        try {
            URI u = new URI(value);
            String h = u.getHost();
            if (h == null || u.getUserInfo() != null || u.getFragment() != null) return false;
            if ("https".equals(u.getScheme())) return true;
            if (!"http".equals(u.getScheme())) return false;
            if (h.equals("localhost") || h.equals("127.0.0.1") || h.equals("[::1]")) return true;
            return false;
        } catch (Exception e) { return false; }
    }
}
