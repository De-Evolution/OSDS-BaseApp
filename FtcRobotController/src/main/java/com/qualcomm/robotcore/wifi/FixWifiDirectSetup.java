package com.qualcomm.robotcore.wifi;

import android.net.wifi.WifiManager;
import com.qualcomm.robotcore.util.RobotLog;

public class FixWifiDirectSetup {

   public static final int WIFI_TOGGLE_DELAY = 2000;


   public static void fixWifiDirectSetup(WifiManager wifiManager) throws InterruptedException {
      toggleWifi(false, wifiManager);
      toggleWifi(true, wifiManager);
   }

   public static void disableWifiDirect(WifiManager wifiManager) throws InterruptedException {
      toggleWifi(false, wifiManager);
   }

   private static void toggleWifi(boolean enabled, WifiManager wifiManager) throws InterruptedException {
      String toggle = enabled?"on":"off";
      RobotLog.i("Toggling Wifi " + toggle);
      wifiManager.setWifiEnabled(enabled);
      Thread.sleep(2000L);
   }
}
