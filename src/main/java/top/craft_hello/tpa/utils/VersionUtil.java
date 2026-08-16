package top.craft_hello.tpa.utils;

import org.jetbrains.annotations.NotNull;


/*
* 版本比较工具
* */
public class VersionUtil {
   public static int getPluginBigVersion(@NotNull String version) {
      String[] versions = version.split("\\.");
      return parseVersionPart(versions.length >= 1 ? versions[0] : "0");
   }

   public static int getPluginMiddleVersion(@NotNull String version) {
      String[] versions = version.split("\\.");
      return parseVersionPart(versions.length >= 2 ? versions[1] : "0");
   }

   public static int getPluginSmallVersion(@NotNull String version) {
      String[] versions = version.split("\\.");
      return parseVersionPart(versions.length == 3 ? versions[2] : "0");
   }

   private static int parseVersionPart(@NotNull String part) {
      if (part.isEmpty()) return 0;
      int value = 0;
      for (int i = 0; i < part.length(); i++) {
         char c = part.charAt(i);
         if (!Character.isDigit(c)) break;
         value = value * 10 + (c - '0');
      }
      return value;
   }

   public static boolean versionComparison(String version1, String version2){
      int version1Big = getPluginBigVersion(version1);
      int version1Middle = getPluginMiddleVersion(version1);
      int version1Small = getPluginSmallVersion(version1);
      int version2Big = getPluginBigVersion(version2);
      int version2Middle = getPluginMiddleVersion(version2);
      int version2Small = getPluginSmallVersion(version2);
      return isOlderThan(version1Big, version1Middle, version1Small, version2Big, version2Middle, version2Small);
   }

   public static boolean isOlderThan(int currentBig, int currentMiddle, int currentSmall, int big, int middle, int small) {
      if (currentBig > big) {
         return false;
      } else if (currentBig < big) {
         return true;
      } else if (currentMiddle > middle) {
         return false;
      } else if (currentMiddle < middle) {
         return true;
      } else {
         return currentSmall < small;
      }
   }
}
