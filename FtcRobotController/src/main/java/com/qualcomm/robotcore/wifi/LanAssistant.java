package com.qualcomm.robotcore.wifi;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.support.annotation.NonNull;
import android.text.format.Formatter;

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
	public void setCallback(@NonNull NetworkConnectionCallback var1)
	{
		this.callback = callback;
	}

	@Override
	public void discoverPotentialConnections()
	{

	}

	@Override
	public void cancelPotentialConnections()
	{

	}

	@Override
	public void createConnection()
	{

	}

	@Override
	public void connect(String var1)
	{

	}

	@Override
	public void connect(String var1, String var2)
	{

	}

	@Override
	public InetAddress getConnectionOwnerAddress()
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
		return null;
	}

	@Override
	public String getConnectionOwnerName()
	{
		return null;
	}

	@Override
	public String getConnectionOwnerMacAddress()
	{
		return null;
	}

	@Override
	public boolean isConnected()
	{
		return true;
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
		return null;
	}

	@Override
	public String getFailureReason()
	{
		return null;
	}

	@Override
	public String getPassphrase()
	{
		return null;
	}

	@Override
	public ConnectStatus getConnectStatus()
	{
		return ConnectStatus.CONNECTED;
	}
}
