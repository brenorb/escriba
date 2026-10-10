package dev.brenorb.escriba;
import java.util.ArrayList;
import java.util.List;
public final class Transcript {
    public record Segment(String text, double start, double end) {}
    private final List<Segment> segments = new ArrayList<>();
    private String provisional="";
    public synchronized void partial(String text) { provisional=text == null ? "" : text.trim(); }
    public synchronized void commit(String text, double start, double end) {
        provisional="";
        if(text!=null && !text.trim().isEmpty()) segments.add(new Segment(text.trim(),start,end));
    }
    public synchronized String copyText() {
        StringBuilder b=new StringBuilder();
        for(Segment s:segments) { if(b.length()>0)b.append("\n\n"); b.append(s.text()); }
        return b.toString();
    }
    public synchronized List<Segment> segments() { return new ArrayList<>(segments); }
    public synchronized String provisional() { return provisional; }
}
