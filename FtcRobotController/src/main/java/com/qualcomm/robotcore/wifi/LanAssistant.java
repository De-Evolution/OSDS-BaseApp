package com.qualcomm.robotcore.wifi;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.text.format.Formatter;

import com.qualcomm.robotcore.util.RobotLog;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class LanAssistant extends NetworkConnection
{

	private static LanAssistant instance = null;
	private NetworkConnectionCallback callback;
	private Context context;
	private Event lastEvent;
	private WifiManager wifiService;

	private String controllerIPAddressString;
	private InetAddress controllerIPAddress; //only set if this is a driver station and


	//whether discoverPotentialConnections() or createConnection() has been called yet
	boolean connectionTypeKnown = false;
	boolean isDriverStation = false; //if true, we connect to the robot controller using the saved IP.  If false, we bind a socket to the wildcard address.

	public static synchronized LanAssistant getLanAssistant(Context context)
	{
		synchronized (LanAssistant.class)
		{
			if (instance == null)
			{
				instance = new LanAssistant(context);
			}
		}
		return instance;
	}

	private LanAssistant(Context context) {
		this.lastEvent = null;
		this.callback = null;
		this.context = context;
		wifiService = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

		controllerIPAddressString = PreferenceManager.getDefaultSharedPreferences(context).getString("ip_address", "");
	}

	@Override
	public NetworkType getNetworkType()
	{
		return NetworkType.LAN;
	}

	@Override
	public void enable()
	{

	}

	@Override
	public void disable()
	{

	}

	@Override
	public void setCallback(@NonNull NetworkConnectionCallback callback)
	{
		this.callback = callback;
	}

	@Override
	public void discoverPotentialConnections()
	{
		setIsDriverStation(true);

		try
		{
			controllerIPAddress = InetAddress.getByName(controllerIPAddressString);
		}
		catch(UnknownHostException e)
		{
			RobotLog.e("Failed to parse robot controller IP address preference");
			e.printStackTrace();
		}

		if(callback != null)
		{
			callback.onNetworkConnectionEvent(Event.CONNECTED_AS_PEER);
		}
	}

	@Override
	public void cancelPotentialConnections()
	{

	}

	@Override
	public void createConnection()
	{
		setIsDriverStation(false);

		if(callback != null)
		{
			callback.onNetworkConnectionEvent(Event.CONNECTED_AS_GROUP_OWNER);
		}
	}

	/**
	 * Called once we find out what side of the connection we're supposed to be.
	 * @param isDS
	 */
	private void setIsDriverStation(boolean isDS)
	{
		connectionTypeKnown = true;
		isDriverStation = isDS;
	}

	@Override
	public void connect(String var1)
	{
		//never called
	}

	@Override
	public void connect(String var1, String var2)
	{
		//never called
	}

	@Override
	public InetAddress getConnectionOwnerAddress()
	{
		if(isDriverStation)
		{
			if(controllerIPAddress == null)
			{
				RobotLog.e("LanAssistant: do not have RC IP address to give to the network library!");
				return null;
			}
			else
			{
				return controllerIPAddress;
			}
		}
		else
		{
			// wildcard address
			try
			{
				return Inet4Address.getByName("0.0.0.0");
			}
			catch(UnknownHostException e)
			{
				//should never happen
				e.printStackTrace();
			}
		}
		return null;
	}

	@Override
	public String getConnectionOwnerName()
	{
		return isDriverStation ? controllerIPAddress.getHostAddress() : "localhost";
	}

	@Override
	public String getConnectionOwnerMacAddress()
	{
		//do not verify mac address
		return null;
	}

	@Override
	public boolean isConnected()
	{
		return getConnectStatus() == ConnectStatus.CONNECTED;
	}

	@Override
	public String getDeviceName()
	{
		if(wifiService.getConnectionInfo() == null)
		{
			return "<no wifi connection>";
		}
		else
		{
			return "Wifi IP: " + Formatter.formatIpAddress(wifiService.getConnectionInfo().getIpAddress());
		}
	}

	@Override
	public String getInfo()
	{
		return "";
	}

	@Override
	public String getFailureReason()
	{
		if(isDriverStation && controllerIPAddress == null)
		{
			return "Failed to parse IP address preference data \"" + controllerIPAddressString + '\"';
		}
		else
		{
			return "";
		}
	}

	@Override
	public String getPassphrase()
	{
		return "";
	}

	@Override
	public ConnectStatus getConnectStatus()
	{
		if(connectionTypeKnown)
		{
			if(isDriverStation)
			{
				if(controllerIPAddress == null)
				{
					return ConnectStatus.ERROR;
				}
				else
				{
					return ConnectStatus.CONNECTED;
				}
			}
			else
			{
				return ConnectStatus.CONNECTED;
			}
		}
		else
		{
			return ConnectStatus.NOT_CONNECTED;
		}
	}
}
