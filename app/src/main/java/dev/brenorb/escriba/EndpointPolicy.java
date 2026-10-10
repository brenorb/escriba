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
            if (!h.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")) return false;
            String[] s=h.split("\\."); int[] p=new int[4];
            for(int i=0;i<4;i++){p[i]=Integer.parseInt(s[i]); if(p[i]>255) return false;}
            return p[0]==10 || (p[0]==192 && p[1]==168) || (p[0]==172 && p[1]>=16 && p[1]<=31);
        } catch (Exception e) { return false; }
    }
}
