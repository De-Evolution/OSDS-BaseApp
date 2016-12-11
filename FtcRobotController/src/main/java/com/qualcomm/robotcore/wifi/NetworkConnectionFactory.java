package com.qualcomm.robotcore.wifi;

import android.content.Context;

import com.qualcomm.robotcore.util.RobotLog;

public class NetworkConnectionFactory {

   public static final String NETWORK_CONNECTION_TYPE = "NETWORK_CONNECTION_TYPE";


   public static NetworkConnection getNetworkConnection(NetworkType type, Context context) {
      RobotLog.v("Starting network of type: " + type);
      switch(type) {
         case WIFIDIRECT:
         return WifiDirectAssistant.getWifiDirectAssistant(context);
         case LOOPBACK:
         return null;
         case SOFTAP:
         return SoftApAssistant.getSoftApAssistant(context);
         case LAN:
            return LanAssistant.getLanAssistant(context);
         default:
         return null;
      }
   }

   public static NetworkType getTypeFromString(String type) {
      return NetworkType.fromString(type);
   }
}
