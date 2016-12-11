package com.qualcomm.robotcore.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import com.qualcomm.robotcore.util.RobotLog;

public class WifiAssistant {

   private final IntentFilter intentFilter;
   private final Context context;
   private final WifiAssistant.WifiStateBroadcastReceiver receiver;


   public WifiAssistant(Context context, WifiAssistant.WifiAssistantCallback callback) {
      this.context = context;
      if(callback == null) {
         RobotLog.v("WifiAssistantCallback is null");
      }

      this.receiver = new WifiAssistant.WifiStateBroadcastReceiver(callback);
      this.intentFilter = new IntentFilter();
      this.intentFilter.addAction("android.net.wifi.STATE_CHANGE");
   }

   public void enable() {
      this.context.registerReceiver(this.receiver, this.intentFilter);
   }

   public void disable() {
      this.context.unregisterReceiver(this.receiver);
   }

   private static class WifiStateBroadcastReceiver extends BroadcastReceiver {

      private WifiAssistant.WifiState state = null;
      private final WifiAssistant.WifiAssistantCallback callback;


      public WifiStateBroadcastReceiver(WifiAssistant.WifiAssistantCallback callback) {
         this.callback = callback;
      }

      public void onReceive(Context context, Intent intent) {
         String action = intent.getAction();
         if(action.equals("android.net.wifi.STATE_CHANGE")) {
            NetworkInfo info = (NetworkInfo)intent.getParcelableExtra("networkInfo");
            if(info.isConnected()) {
               this.notify(WifiAssistant.WifiState.CONNECTED);
            } else {
               this.notify(WifiAssistant.WifiState.NOT_CONNECTED);
            }
         }

      }

      private void notify(WifiAssistant.WifiState newState) {
         if(this.state != newState) {
            this.state = newState;
            if(this.callback != null) {
               this.callback.wifiEventCallback(this.state);
            }

         }
      }
   }

   public static enum WifiState {

      CONNECTED("CONNECTED", 0),
      NOT_CONNECTED("NOT_CONNECTED", 1);
      // $FF: synthetic field
      private static final WifiAssistant.WifiState[] $VALUES = new WifiAssistant.WifiState[]{CONNECTED, NOT_CONNECTED};


      private WifiState(String var1, int var2) {}

   }

   public interface WifiAssistantCallback {

      void wifiEventCallback(WifiAssistant.WifiState var1);
   }
}
