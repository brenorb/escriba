package dev.brenorb.escriba;

public final class ModelCatalog {
  private static final String[] IDS = {
    "parakeet-redux", "parakeet-v3-int8", "phonon-2", "parakeet-ultra"
  };
  private static final String[] LABELS = {
    "Parakeet Redux · menor · 171 MiB",
    "Parakeet v3 int8 · 640 MiB",
    "Phonon-2 · rápido em GPU · 325 MiB",
    "Parakeet Ultra · 1,17 GiB"
  };

  private ModelCatalog() {}

  public static String defaultId() {
    return IDS[0];
  }

  public static String[] ids() {
    return IDS.clone();
  }

  public static String[] labels() {
    return LABELS.clone();
  }

  public static String idAt(int index) {
    return IDS[index];
  }

  public static int indexOf(String id) {
    for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
    return -1;
  }

  public static String labelAt(int index) {
    return LABELS[index];
  }
}
