package com.qualcomm.robotcore.wifi;

import android.support.annotation.NonNull;

public enum NetworkType {

   WIFIDIRECT,
   LOOPBACK,
   SOFTAP,
   UNKNOWN_NETWORK_TYPE,
   LAN;

   @NonNull
   public static NetworkType fromString(String type) {
      try {
         return valueOf(type.toUpperCase());
      } catch (Exception var2) {
         return UNKNOWN_NETWORK_TYPE;
      }
   }

}
