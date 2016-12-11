package org.firstinspires.ftc.robotcontroller.internal.lan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.widget.TextView;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;

/**
 * BroadcastReceiver which updates a TextView with the current Wifi IP address
 */
public class WifiIPUpdaterReceiver extends BroadcastReceiver
{
	private TextView toUpdate;
	private ConnectivityManager conMan;
	private WifiManager wifiMan;


	public WifiIPUpdaterReceiver(Context context, TextView toUpdate)
	{
		this.toUpdate = toUpdate;
		conMan = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		wifiMan = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

		updateText();
	}

	private void updateText()
	{
		String message = null;


		NetworkInfo netInfo = conMan.getActiveNetworkInfo();
		if (netInfo != null && netInfo.getType() == ConnectivityManager.TYPE_WIFI && netInfo.isConnected())
		{

			int ipAddress = wifiMan.getConnectionInfo().getIpAddress();

			// Convert little-endian to big-endianif needed
			if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN))
			{
				ipAddress = Integer.reverseBytes(ipAddress);
			}

			byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

			try
			{
				message = "IP: " + InetAddress.getByAddress(ipByteArray).getHostAddress();
			}
			catch (UnknownHostException ex)
			{
				ex.printStackTrace();
				message = "Cannot get IP address.";
			}

		}
		else
		{
			message = "No wifi connection";
		}

		toUpdate.setText(message);

	}

	@Override
	public void onReceive(Context context, Intent intent)
	{
		updateText();
	}
}
