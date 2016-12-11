package com.qualcomm.robotcore.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import com.qualcomm.robotcore.BuildConfig;
import com.qualcomm.robotcore.robocol.TelemetryMessage;
import com.qualcomm.robotcore.util.ReadWriteFile;
import com.qualcomm.robotcore.util.RobotLog;

import org.firstinspires.ftc.robotcore.internal.AppUtil;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class SoftApAssistant extends NetworkConnection {
	private static String DEFAULT_PASSWORD = null;
	private static String DEFAULT_SSID = null;
	private static final String NETWORK_PASSWORD_FILE = "FTC_RobotController_password.txt";
	private static final String NETWORK_SSID_FILE = "FTC_RobotController_SSID.txt";
	private static IntentFilter intentFilter;
	private static SoftApAssistant softApAssistant;
	private NetworkConnectionCallback callback;
	private Context context;
	private Event lastEvent;
	String password;
	private BroadcastReceiver receiver;
	private final List<ScanResult> scanResults;
	String ssid;
	private final WifiManager wifiManager;

	/* renamed from: com.qualcomm.robotcore.wifi.SoftApAssistant.1 */
	class ScanResultsReceiver extends BroadcastReceiver {
		ScanResultsReceiver() {
		}

		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			WifiInfo wifiInfo = SoftApAssistant.this.wifiManager.getConnectionInfo();
			RobotLog.i("onReceive(), action: " + action + ", wifiInfo: " + wifiInfo);
			if (wifiInfo.getSSID().equals(SoftApAssistant.this.ssid) && wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
				SoftApAssistant.this.sendEvent(Event.CONNECTION_INFO_AVAILABLE);
			}
			if ("android.net.wifi.SCAN_RESULTS".equals(action)) {
				SoftApAssistant.this.scanResults.clear();
				SoftApAssistant.this.scanResults.addAll(SoftApAssistant.this.wifiManager.getScanResults());
				RobotLog.i("Soft AP scanResults found: " + SoftApAssistant.this.scanResults.size());
				for (ScanResult scanResult : SoftApAssistant.this.scanResults) {
					RobotLog.i("    scanResult: " + scanResult.SSID);
				}
				SoftApAssistant.this.sendEvent(Event.PEERS_AVAILABLE);
			}
			if ("android.net.wifi.supplicant.STATE_CHANGE".equals(action) && wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
				SoftApAssistant.this.sendEvent(Event.CONNECTION_INFO_AVAILABLE);
			}
		}
	}

	static {
		softApAssistant = null;
		DEFAULT_PASSWORD = "password";
		DEFAULT_SSID = "FTC-1234";
	}

	public static synchronized SoftApAssistant getSoftApAssistant(Context context)
	{
		synchronized (SoftApAssistant.class) {
			if (softApAssistant == null)
			{
				softApAssistant = new SoftApAssistant(context);
			}
			intentFilter = new IntentFilter();
			intentFilter.addAction("android.net.wifi.SCAN_RESULTS");
			intentFilter.addAction("android.net.wifi.NETWORK_IDS_CHANGED");
			intentFilter.addAction("android.net.wifi.STATE_CHANGE");
			intentFilter.addAction("android.net.wifi.supplicant.STATE_CHANGE");
			intentFilter.addAction("android.net.wifi.supplicant.CONNECTION_CHANGE");
			intentFilter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
			softApAssistant = softApAssistant;
		}
		return softApAssistant;
	}

	private SoftApAssistant(Context context) {
		this.scanResults = new ArrayList();
		this.context = null;
		this.lastEvent = null;
		this.ssid = DEFAULT_SSID;
		this.password = DEFAULT_PASSWORD;
		this.callback = null;
		this.context = context;
		this.wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
	}

	public List<ScanResult> getScanResults() {
		return this.scanResults;
	}

	public NetworkType getNetworkType() {
		return NetworkType.SOFTAP;
	}

	public void enable() {
		if (this.receiver == null) {
			this.receiver = new ScanResultsReceiver();
		}
		this.context.registerReceiver(this.receiver, intentFilter);
	}

	public void disable() {
		try {
			this.context.unregisterReceiver(this.receiver);
		} catch (IllegalArgumentException e) {
		}
		this.lastEvent = null;
	}

	public void setCallback(NetworkConnectionCallback callback) {
		RobotLog.d("setting NetworkConnection callback: " + callback);
		this.callback = callback;
	}

	public void discoverPotentialConnections() {
		this.wifiManager.startScan();
	}

	public void cancelPotentialConnections() {
	}

	private WifiConfiguration buildConfig(String ssid, String pass) {
		WifiConfiguration myConfig = new WifiConfiguration();
		myConfig.SSID = ssid;
		myConfig.preSharedKey = pass;
		RobotLog.i("Setting up network, myConfig.SSID: " + myConfig.SSID + ", password: " + myConfig.preSharedKey);
		myConfig.status = 2;
		myConfig.allowedAuthAlgorithms.set(0);
		myConfig.allowedKeyManagement.set(1);
		myConfig.allowedProtocols.set(1);
		myConfig.allowedProtocols.set(0);
		myConfig.allowedGroupCiphers.set(2);
		myConfig.allowedGroupCiphers.set(3);
		myConfig.allowedPairwiseCiphers.set(1);
		myConfig.allowedPairwiseCiphers.set(2);
		return myConfig;
	}

	public void createConnection() {
		ReflectiveOperationException e;
		if (this.wifiManager.isWifiEnabled()) {
			this.wifiManager.setWifiEnabled(false);
		}
		File directory = AppUtil.FIRST_FOLDER;
		File fileSSID = new File(directory, NETWORK_SSID_FILE);
		if (!fileSSID.exists()) {
			ReadWriteFile.writeFile(directory, NETWORK_SSID_FILE, DEFAULT_SSID);
		}
		File filePassword = new File(directory, NETWORK_PASSWORD_FILE);
		if (!filePassword.exists()) {
			ReadWriteFile.writeFile(directory, NETWORK_PASSWORD_FILE, DEFAULT_PASSWORD);
		}
		String userSSID = ReadWriteFile.readFile(fileSSID);
		String userPass = ReadWriteFile.readFile(filePassword);
		if (userSSID.isEmpty() || userSSID.length() >= 15) {
			ReadWriteFile.writeFile(directory, NETWORK_SSID_FILE, DEFAULT_SSID);
		}
		if (userPass.isEmpty()) {
			ReadWriteFile.writeFile(directory, NETWORK_PASSWORD_FILE, DEFAULT_PASSWORD);
		}
		this.ssid = ReadWriteFile.readFile(fileSSID);
		this.password = ReadWriteFile.readFile(filePassword);
		WifiConfiguration wifiConfig = buildConfig(this.ssid, this.password);
		RobotLog.i("Advertising SSID: " + this.ssid + ", password: " + this.password);
		try {
			this.wifiManager.getClass().getMethod("setWifiApConfiguration", new Class[]{WifiConfiguration.class}).invoke(this.wifiManager, new Object[]{wifiConfig});
			Method enableAp = this.wifiManager.getClass().getMethod("setWifiApEnabled", new Class[]{WifiConfiguration.class, Boolean.TYPE});
			enableAp.invoke(this.wifiManager, new Object[]{null, Boolean.valueOf(false)});
			if (((Boolean) enableAp.invoke(this.wifiManager, new Object[]{wifiConfig, Boolean.valueOf(true)})).booleanValue()) {
				sendEvent(Event.AP_CREATED);
			}
		} catch (NoSuchMethodException e2) {
			e = e2;
			RobotLog.e(e.getMessage());
			e.printStackTrace();
		} catch (InvocationTargetException e3) {
			e = e3;
			RobotLog.e(e.getMessage());
			e.printStackTrace();
		} catch (IllegalAccessException e4) {
			e = e4;
			RobotLog.e(e.getMessage());
			e.printStackTrace();
		}
	}

	public void connect(String ssid, String password) {
		this.ssid = ssid;
		this.password = password;
		WifiConfiguration wifiConfig = buildConfig(String.format("\"%s\"", new Object[]{ssid}), String.format("\"%s\"", new Object[]{password}));
		WifiInfo wifiInfo = this.wifiManager.getConnectionInfo();
		RobotLog.i("Connecting to SoftAP, SSID: " + wifiConfig.SSID + ", supplicant state: " + wifiInfo.getSupplicantState());
		if (wifiInfo.getSSID().equals(wifiConfig.SSID) && wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
			sendEvent(Event.CONNECTION_INFO_AVAILABLE);
		}
		if (!wifiInfo.getSSID().equals(wifiConfig.SSID) || wifiInfo.getSupplicantState() != SupplicantState.COMPLETED) {
			int netId = this.wifiManager.addNetwork(wifiConfig);
			this.wifiManager.saveConfiguration();
			if (netId != -1) {
				for (WifiConfiguration i : this.wifiManager.getConfiguredNetworks()) {
					if (i.SSID != null && i.SSID.equals("\"" + ssid + "\"")) {
						this.wifiManager.disconnect();
						this.wifiManager.enableNetwork(i.networkId, true);
						this.wifiManager.reconnect();
						return;
					}
				}
			}
		}
	}

	public void connect(String ssid) {
		connect(ssid, DEFAULT_PASSWORD);
	}

	public InetAddress getConnectionOwnerAddress() {
		InetAddress address = null;
		try {
			address = InetAddress.getByName("192.168.43.1");
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return address;
	}

	public String getConnectionOwnerName() {
		RobotLog.d("ssid in softap assistant: " + this.ssid);
		return this.ssid.replace("\"", BuildConfig.VERSION_NAME);
	}

	public String getConnectionOwnerMacAddress() {
		return this.ssid.replace("\"", BuildConfig.VERSION_NAME);
	}

	public boolean isConnected() {
		WifiInfo wifiInfo = this.wifiManager.getConnectionInfo();
		RobotLog.i("isConnected(), current supplicant state: " + wifiInfo.getSupplicantState().toString());
		return wifiInfo.getSupplicantState() == SupplicantState.COMPLETED;
	}

	public String getDeviceName() {
		return this.ssid;
	}

	private boolean isSoftAccessPoint() {
		try {
			return ((Boolean) this.wifiManager.getClass().getMethod("isWifiApEnabled", new Class[0]).invoke(this.wifiManager, new Object[0])).booleanValue();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
			return false;
		} catch (InvocationTargetException e2) {
			e2.printStackTrace();
			return false;
		} catch (IllegalAccessException e3) {
			e3.printStackTrace();
			return false;
		}
	}

	public String getInfo() {
		StringBuilder s = new StringBuilder();
		WifiInfo wifiInfo = this.wifiManager.getConnectionInfo();
		s.append("Name: ").append(getDeviceName());
		if (isSoftAccessPoint()) {
			s.append("\nAccess Point SSID: ").append(getConnectionOwnerName());
			s.append("\nPassphrase: ").append(getPassphrase());
			s.append("\nAdvertising");
		} else if (isConnected()) {
			s.append("\nIP Address: ").append(getIpAddressAsString(wifiInfo.getIpAddress()));
			s.append("\nAccess Point SSID: ").append(getConnectionOwnerName());
			s.append("\nPassphrase: ").append(getPassphrase());
		} else {
			s.append("\nNo connection information");
		}
		return s.toString();
	}

	private String getIpAddressAsString(int ipAddress) {
		return String.format("%d.%d.%d.%d", new Object[]{Integer.valueOf(ipAddress & TelemetryMessage.cbTagMax), Integer.valueOf((ipAddress >> 8) & TelemetryMessage.cbTagMax), Integer.valueOf((ipAddress >> 16) & TelemetryMessage.cbTagMax), Integer.valueOf((ipAddress >> 24) & TelemetryMessage.cbTagMax)});
	}

	public String getFailureReason() {
		return null;
	}

	public String getPassphrase() {
		return this.password;
	}

	public ConnectStatus getConnectStatus() {
		switch(this.wifiManager.getConnectionInfo().getSupplicantState()) {
			case ASSOCIATING:
				return ConnectStatus.CONNECTING;
			case COMPLETED:
				return ConnectStatus.CONNECTED;
			case SCANNING:
				return ConnectStatus.NOT_CONNECTED;
			default:
				return ConnectStatus.NOT_CONNECTED;
		}
	}

	private void sendEvent(Event event) {
		if (this.lastEvent != event) {
			this.lastEvent = event;
			if (this.callback != null) {
				this.callback.onNetworkConnectionEvent(event);
			}
		}
	}
}
