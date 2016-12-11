package com.qualcomm.robotcore.wifi;

import android.support.annotation.NonNull;

import org.firstinspires.ftc.robotcore.internal.network.CallbackResult;

import java.net.InetAddress;

public abstract class NetworkConnection {

   public abstract NetworkType getNetworkType();

   public abstract void enable();

   public abstract void disable();

   public abstract void setCallback(@NonNull NetworkConnection.NetworkConnectionCallback var1);

   public abstract void discoverPotentialConnections();

   public abstract void cancelPotentialConnections();

   public abstract void createConnection();

   public abstract void connect(String var1);

   public abstract void connect(String var1, String var2);

   public abstract InetAddress getConnectionOwnerAddress();

   public abstract String getConnectionOwnerName();

   public abstract String getConnectionOwnerMacAddress();

   public abstract boolean isConnected();

   public abstract String getDeviceName();

   public abstract String getInfo();

   public abstract String getFailureReason();

   public abstract String getPassphrase();

   public abstract NetworkConnection.ConnectStatus getConnectStatus();

   public static boolean isDeviceNameValid(String deviceName) {
      return deviceName.matches("^\\p{Print}+$");
   }

   public enum ConnectStatus
   {
      NOT_CONNECTED,
      CONNECTING,
      CONNECTED,
      GROUP_OWNER,
      ERROR
   }

   public enum Event
   {

      DISCOVERING_PEERS,
      PEERS_AVAILABLE,
      GROUP_CREATED,
      CONNECTING,
      CONNECTED_AS_PEER,
      CONNECTED_AS_GROUP_OWNER,
      DISCONNECTED,
      CONNECTION_INFO_AVAILABLE,
      AP_CREATED,
      ERROR,
      UNKNOWN
   }

   public interface NetworkConnectionCallback {

      CallbackResult onNetworkConnectionEvent(NetworkConnection.Event var1);
   }
}
