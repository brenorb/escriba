package dev.brenorb.escriba;

import java.util.List;
import java.util.Locale;

public final class VoiceModelPolicy {
  private VoiceModelPolicy() {}

  public static boolean installed(List<String> installed, String requested) {
    return contains(installed, requested);
  }

  public static boolean downloadable(
      List<String> supported, List<String> pending, String requested) {
    return contains(supported, requested) || contains(pending, requested);
  }

  private static boolean contains(List<String> locales, String requested) {
    String normalized = requested.toLowerCase(Locale.ROOT).replace('_', '-');
    return locales.stream()
        .anyMatch(locale -> locale.toLowerCase(Locale.ROOT).replace('_', '-').equals(normalized));
  }
}
