package dev.brenorb.escriba;

import java.util.Locale;

public final class InputPolicy {
  public static boolean accepts(String mime, String name) {
    if (mime != null && (mime.startsWith("audio/") || mime.startsWith("video/"))) return true;
    String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
    return n.matches(".*\\.(opus|ogg|oga|mp3|m4a|aac|wav|flac|mp4|webm|3gp|amr)$");
  }
}
