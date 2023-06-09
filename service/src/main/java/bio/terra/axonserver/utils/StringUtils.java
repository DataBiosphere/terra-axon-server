package bio.terra.axonserver.utils;

public class StringUtils {
  public static String camelToSnake(String str) {
    String regex = "([a-z])([A-Z]+)";
    String replacement = "$1_$2";
    return str.replaceAll(regex, replacement).toLowerCase();
  }
}
