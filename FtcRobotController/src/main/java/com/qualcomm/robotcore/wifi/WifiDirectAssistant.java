package com.qualcomm.robotcore.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.GroupInfoListener;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.os.Looper;

import com.qualcomm.robotcore.BuildConfig;
import com.qualcomm.robotcore.hardware.configuration.ModernRoboticsConstants;
import com.qualcomm.robotcore.util.RobotLog;

import junit.framework.Assert;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class WifiDirectAssistant extends NetworkConnection {
	public static final String TAG = "WifiDirect";
	private static WifiDirectAssistant wifiDirectAssistant;
	private NetworkConnectionCallback callback;
	private int clients;
	private ConnectStatus connectStatus;
	private final WifiDirectConnectionInfoListener connectionListener;
	private Context context;
	private String deviceMacAddress;
	private String deviceName;
	private int failureReason;
	private boolean groupFormed;
	private final WifiDirectGroupInfoListener groupInfoListener;
	private String groupInterface;
	private String groupNetworkName;
	private InetAddress groupOwnerAddress;
	private String groupOwnerMacAddress;
	private String groupOwnerName;
	private final IntentFilter intentFilter;
	private boolean isWifiP2pEnabled;
	private Event lastEvent;
	private String passphrase;
	private final WifiDirectPeerListListener peerListListener;
	private final List<WifiP2pDevice> peers;
	private WifiP2pBroadcastReceiver receiver;
	private final Channel wifiP2pChannel;
	private final WifiP2pManager wifiP2pManager;

	/* renamed from: com.qualcomm.robotcore.wifi.WifiDirectAssistant.1 */
	class PeerDiscoveryListener implements ActionListener {
		PeerDiscoveryListener() {
		}

		public void onSuccess() {
			WifiDirectAssistant.this.sendEvent(Event.DISCOVERING_PEERS);
			RobotLog.dd(WifiDirectAssistant.TAG, "discovering peers");
		}

		public void onFailure(int reason) {
			String reasonStr = WifiDirectAssistant.failureReasonToString(reason);
			WifiDirectAssistant.this.failureReason = reason;
			RobotLog.e("Wifi Direct failure while trying to discover peers - reason: " + reasonStr);
			WifiDirectAssistant.this.sendEvent(Event.ERROR);
		}
	}

	/* renamed from: com.qualcomm.robotcore.wifi.WifiDirectAssistant.2 */
	class GroupCreationListener implements ActionListener {
		GroupCreationListener() {
		}

		public void onSuccess() {
			WifiDirectAssistant.this.sendEvent(Event.GROUP_CREATED);
			RobotLog.dd(WifiDirectAssistant.TAG, "created group");
		}

		public void onFailure(int reason) {
			if (reason == 2) {
				RobotLog.dd(WifiDirectAssistant.TAG, "cannot create group, does group already exist?");
				return;
			}
			String reasonStr = WifiDirectAssistant.failureReasonToString(reason);
			WifiDirectAssistant.this.failureReason = reason;
			RobotLog.e("Wifi Direct failure while trying to create group - reason: " + reasonStr);
			WifiDirectAssistant.this.connectStatus = ConnectStatus.ERROR;
			WifiDirectAssistant.this.sendEvent(Event.ERROR);
		}
	}

	/* renamed from: com.qualcomm.robotcore.wifi.WifiDirectAssistant.3 */
	class ConnectionListener implements ActionListener {
		ConnectionListener() {
		}

		public void onSuccess() {
			RobotLog.dd(WifiDirectAssistant.TAG, "connect started");
			WifiDirectAssistant.this.sendEvent(Event.CONNECTING);
		}

		public void onFailure(int reason) {
			String reasonStr = WifiDirectAssistant.failureReasonToString(reason);
			WifiDirectAssistant.this.failureReason = reason;
			RobotLog.dd(WifiDirectAssistant.TAG, "connect cannot start - reason: " + reasonStr);
			WifiDirectAssistant.this.sendEvent(Event.ERROR);
		}
	}

	private class WifiDirectConnectionInfoListener implements ConnectionInfoListener {
		private WifiDirectConnectionInfoListener() {
		}

		public void onConnectionInfoAvailable(WifiP2pInfo info) {
			WifiDirectAssistant.this.wifiP2pManager.requestGroupInfo(WifiDirectAssistant.this.wifiP2pChannel, WifiDirectAssistant.this.groupInfoListener);
			WifiDirectAssistant.this.groupOwnerAddress = info.groupOwnerAddress;
			RobotLog.dd(WifiDirectAssistant.TAG, "group owners address: " + WifiDirectAssistant.this.groupOwnerAddress.toString());
			if (info.groupFormed && info.isGroupOwner) {
				RobotLog.dd(WifiDirectAssistant.TAG, "group formed, this device is the group owner (GO)");
				WifiDirectAssistant.this.connectStatus = ConnectStatus.GROUP_OWNER;
				WifiDirectAssistant.this.sendEvent(Event.CONNECTED_AS_GROUP_OWNER);
			} else if (info.groupFormed) {
				RobotLog.dd(WifiDirectAssistant.TAG, "group formed, this device is a client");
				WifiDirectAssistant.this.connectStatus = ConnectStatus.CONNECTED;
				WifiDirectAssistant.this.sendEvent(Event.CONNECTED_AS_PEER);
			} else {
				RobotLog.dd(WifiDirectAssistant.TAG, "group NOT formed, ERROR: " + info.toString());
				WifiDirectAssistant.this.failureReason = 0;
				WifiDirectAssistant.this.connectStatus = ConnectStatus.ERROR;
				WifiDirectAssistant.this.sendEvent(Event.ERROR);
			}
		}
	}

	private class WifiDirectGroupInfoListener implements GroupInfoListener {
		private WifiDirectGroupInfoListener() {
		}

		public void onGroupInfoAvailable(WifiP2pGroup group) {
			if (group != null) {
				if (group.isGroupOwner()) {
					WifiDirectAssistant.this.groupOwnerMacAddress = WifiDirectAssistant.this.deviceMacAddress;
					WifiDirectAssistant.this.groupOwnerName = WifiDirectAssistant.this.deviceName;
				} else {
					WifiP2pDevice go = group.getOwner();
					WifiDirectAssistant.this.groupOwnerMacAddress = go.deviceAddress;
					WifiDirectAssistant.this.groupOwnerName = go.deviceName;
				}
				WifiDirectAssistant.this.groupInterface = group.getInterface();
				WifiDirectAssistant.this.groupNetworkName = group.getNetworkName();
				WifiDirectAssistant.this.passphrase = group.getPassphrase();
				WifiDirectAssistant.this.passphrase = WifiDirectAssistant.this.passphrase != null ? WifiDirectAssistant.this.passphrase : BuildConfig.VERSION_NAME;
				RobotLog.vv(WifiDirectAssistant.TAG, "connection information available");
				RobotLog.vv(WifiDirectAssistant.TAG, "connection information - groupOwnerName = " + WifiDirectAssistant.this.groupOwnerName);
				RobotLog.vv(WifiDirectAssistant.TAG, "connection information - groupOwnerMacAddress = " + WifiDirectAssistant.this.groupOwnerMacAddress);
				RobotLog.vv(WifiDirectAssistant.TAG, "connection information - groupInterface = " + WifiDirectAssistant.this.groupInterface);
				RobotLog.vv(WifiDirectAssistant.TAG, "connection information - groupNetworkName = " + WifiDirectAssistant.this.groupNetworkName);
				WifiDirectAssistant.this.sendEvent(Event.CONNECTION_INFO_AVAILABLE);
			}
		}
	}

	private class WifiDirectPeerListListener implements PeerListListener {
		private WifiDirectPeerListListener() {
		}

		public void onPeersAvailable(WifiP2pDeviceList peerList) {
			WifiDirectAssistant.this.peers.clear();
			WifiDirectAssistant.this.peers.addAll(peerList.getDeviceList());
			RobotLog.vv(WifiDirectAssistant.TAG, "peers found: " + WifiDirectAssistant.this.peers.size());
			for (WifiP2pDevice peer : WifiDirectAssistant.this.peers) {
				RobotLog.vv(WifiDirectAssistant.TAG, "    peer: " + peer.deviceAddress + " " + peer.deviceName);
			}
			WifiDirectAssistant.this.sendEvent(Event.PEERS_AVAILABLE);
		}
	}

	private class WifiP2pBroadcastReceiver extends BroadcastReceiver {
		private WifiP2pBroadcastReceiver() {
		}

		public void onReceive(Context context, Intent intent) {
			boolean z = true;
			String action = intent.getAction();
			if ("android.net.wifi.p2p.STATE_CHANGED".equals(action)) {
				int state = intent.getIntExtra("wifi_p2p_state", -1);
				WifiDirectAssistant wifiDirectAssistant = WifiDirectAssistant.this;
				if (state != 2) {
					z = false;
				}
				wifiDirectAssistant.isWifiP2pEnabled = z;
				RobotLog.dd(WifiDirectAssistant.TAG, "broadcast: state - enabled: " + WifiDirectAssistant.this.isWifiP2pEnabled);
			} else if ("android.net.wifi.p2p.PEERS_CHANGED".equals(action)) {
				RobotLog.dd(WifiDirectAssistant.TAG, "broadcast: peers changed");
				WifiDirectAssistant.this.wifiP2pManager.requestPeers(WifiDirectAssistant.this.wifiP2pChannel, WifiDirectAssistant.this.peerListListener);
			} else if ("android.net.wifi.p2p.CONNECTION_STATE_CHANGE".equals(action)) {
				NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra("networkInfo");
				WifiP2pInfo wifip2pinfo = (WifiP2pInfo) intent.getParcelableExtra("wifiP2pInfo");
				WifiP2pGroup wifiP2pGroup = (WifiP2pGroup) intent.getParcelableExtra("p2pGroupInfo");
				RobotLog.dd(WifiDirectAssistant.TAG, "broadcast: connection changed: connectStatus=%s networkInfo.state=%s", WifiDirectAssistant.this.connectStatus, networkInfo.getState());
				WifiDirectAssistant.dump(networkInfo);
				WifiDirectAssistant.dump(wifip2pinfo);
				WifiDirectAssistant.dump(wifiP2pGroup);
				if (!networkInfo.isConnected()) {
					WifiDirectAssistant.this.connectStatus = ConnectStatus.NOT_CONNECTED;
					if (!WifiDirectAssistant.this.groupFormed) {
						WifiDirectAssistant.this.discoverPeers();
					}
					if (WifiDirectAssistant.this.isConnected()) {
						RobotLog.vv(WifiDirectAssistant.TAG, "disconnecting");
						WifiDirectAssistant.this.sendEvent(Event.DISCONNECTED);
					}
					WifiDirectAssistant.this.groupFormed = wifip2pinfo.groupFormed;
				} else if (!WifiDirectAssistant.this.isConnected()) {
					WifiDirectAssistant.this.wifiP2pManager.requestConnectionInfo(WifiDirectAssistant.this.wifiP2pChannel, WifiDirectAssistant.this.connectionListener);
					WifiDirectAssistant.this.wifiP2pManager.stopPeerDiscovery(WifiDirectAssistant.this.wifiP2pChannel, null);
				}
			} else if ("android.net.wifi.p2p.THIS_DEVICE_CHANGED".equals(action)) {
				RobotLog.dd(WifiDirectAssistant.TAG, "broadcast: this device changed");
				WifiDirectAssistant.this.onWifiP2pThisDeviceChanged((WifiP2pDevice) intent.getParcelableExtra("wifiP2pDevice"));
			}
		}
	}

	static {
		wifiDirectAssistant = null;
	}

	public static void dump(NetworkInfo info) {
		Assert.assertNotNull(info);
		RobotLog.vv(TAG, "NetworkInfo: %s", info.toString());
	}

	public static void dump(WifiP2pInfo info) {
		Assert.assertNotNull(info);
		RobotLog.vv(TAG, "WifiP2pInfo: %s", info.toString());
	}

	public static void dump(WifiP2pGroup info) {
		Assert.assertNotNull(info);
		RobotLog.vv(TAG, "WifiP2pGroup: %s", info.toString().replace("\n ", ", "));
	}

	public static synchronized WifiDirectAssistant getWifiDirectAssistant(Context context)
	{
		synchronized (WifiDirectAssistant.class) {
			if(wifiDirectAssistant == null)
			{
				wifiDirectAssistant = new WifiDirectAssistant(context);
			}
		}
		return wifiDirectAssistant;
	}

	private WifiDirectAssistant(Context context) {
		this.peers = new ArrayList();
		this.context = null;
		this.isWifiP2pEnabled = false;
		this.failureReason = 0;
		this.connectStatus = ConnectStatus.NOT_CONNECTED;
		this.lastEvent = null;
		this.deviceMacAddress = BuildConfig.VERSION_NAME;
		this.deviceName = BuildConfig.VERSION_NAME;
		this.groupOwnerAddress = null;
		this.groupOwnerMacAddress = BuildConfig.VERSION_NAME;
		this.groupOwnerName = BuildConfig.VERSION_NAME;
		this.groupInterface = BuildConfig.VERSION_NAME;
		this.groupNetworkName = BuildConfig.VERSION_NAME;
		this.passphrase = BuildConfig.VERSION_NAME;
		this.groupFormed = false;
		this.clients = 0;
		this.callback = null;
		this.context = context;
		this.intentFilter = new IntentFilter();
		this.intentFilter.addAction("android.net.wifi.p2p.STATE_CHANGED");
		this.intentFilter.addAction("android.net.wifi.p2p.PEERS_CHANGED");
		this.intentFilter.addAction("android.net.wifi.p2p.CONNECTION_STATE_CHANGE");
		this.intentFilter.addAction("android.net.wifi.p2p.THIS_DEVICE_CHANGED");
		this.wifiP2pManager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
		this.wifiP2pChannel = this.wifiP2pManager.initialize(context, Looper.getMainLooper(), null);
		this.receiver = new WifiP2pBroadcastReceiver();
		this.connectionListener = new WifiDirectConnectionInfoListener();
		this.peerListListener = new WifiDirectPeerListListener();
		this.groupInfoListener = new WifiDirectGroupInfoListener();
	}

	public NetworkType getNetworkType() {
		return NetworkType.WIFIDIRECT;
	}

	public synchronized void enable() {
		this.clients++;
		RobotLog.vv(TAG, "There are " + this.clients + " Wifi Direct Assistant Clients (+)");
		if (this.clients == 1) {
			RobotLog.vv(TAG, "Enabling Wifi Direct Assistant");
			if (this.receiver == null) {
				this.receiver = new WifiP2pBroadcastReceiver();
			}
			this.context.registerReceiver(this.receiver, this.intentFilter);
		}
	}

	public synchronized void disable() {
		this.clients--;
		RobotLog.vv(TAG, "There are " + this.clients + " Wifi Direct Assistant Clients (-)");
		if (this.clients == 0) {
			RobotLog.vv(TAG, "Disabling Wifi Direct Assistant");
			this.wifiP2pManager.stopPeerDiscovery(this.wifiP2pChannel, null);
			this.wifiP2pManager.cancelConnect(this.wifiP2pChannel, null);
			try {
				this.context.unregisterReceiver(this.receiver);
			} catch (IllegalArgumentException e) {
			}
			this.lastEvent = null;
			this.connectStatus = ConnectStatus.NOT_CONNECTED;
		}
	}

	public void discoverPotentialConnections() {
		discoverPeers();
	}

	public void createConnection() {
		createGroup();
	}

	public void cancelPotentialConnections() {
		cancelDiscoverPeers();
	}

	public String getInfo() {
		StringBuilder s = new StringBuilder();
		if (isEnabled()) {
			s.append("Name: ").append(getDeviceName());
			if (isGroupOwner()) {
				s.append("\nIP Address").append(getGroupOwnerAddress().getHostAddress());
				s.append("\nPassphrase: ").append(getPassphrase());
				s.append("\nGroup Owner");
			} else if (isConnected()) {
				s.append("\nGroup Owner: ").append(getGroupOwnerName());
				s.append("\nConnected");
			} else {
				s.append("\nNo connection information");
			}
		}
		return s.toString();
	}

	public synchronized boolean isEnabled() {
		return this.clients > 0;
	}

	public ConnectStatus getConnectStatus() {
		return this.connectStatus;
	}

	public List<WifiP2pDevice> getPeers() {
		return new ArrayList(this.peers);
	}

	public NetworkConnectionCallback getCallback() {
		return this.callback;
	}

	public void setCallback(NetworkConnectionCallback callback) {
		this.callback = callback;
	}

	public String getDeviceMacAddress() {
		return this.deviceMacAddress;
	}

	public String getDeviceName() {
		return this.deviceName;
	}

	public InetAddress getConnectionOwnerAddress() {
		return getGroupOwnerAddress();
	}

	public InetAddress getGroupOwnerAddress() {
		return this.groupOwnerAddress;
	}

	public String getConnectionOwnerMacAddress() {
		return getGroupOwnerMacAddress();
	}

	private String getGroupOwnerMacAddress() {
		return this.groupOwnerMacAddress;
	}

	public String getConnectionOwnerName() {
		return getGroupOwnerName();
	}

	public String getGroupOwnerName() {
		return this.groupOwnerName;
	}

	public String getPassphrase() {
		return this.passphrase;
	}

	public String getGroupInterface() {
		return this.groupInterface;
	}

	public String getGroupNetworkName() {
		return this.groupNetworkName;
	}

	public boolean isWifiP2pEnabled() {
		return this.isWifiP2pEnabled;
	}

	public boolean isConnected() {
		return this.connectStatus == ConnectStatus.CONNECTED || this.connectStatus == ConnectStatus.GROUP_OWNER;
	}

	public boolean isGroupOwner() {
		return this.connectStatus == ConnectStatus.GROUP_OWNER;
	}

	public void discoverPeers() {
		this.wifiP2pManager.discoverPeers(this.wifiP2pChannel, new PeerDiscoveryListener());
	}

	public void cancelDiscoverPeers() {
		RobotLog.dd(TAG, "stop discovering peers");
		this.wifiP2pManager.stopPeerDiscovery(this.wifiP2pChannel, null);
	}

	public void createGroup() {
		this.wifiP2pManager.createGroup(this.wifiP2pChannel, new GroupCreationListener());
	}

	public void removeGroup() {
		this.wifiP2pManager.removeGroup(this.wifiP2pChannel, null);
	}

	public void connect(String deviceAddress, String notSupported) {
		throw new UnsupportedOperationException("This method is not supported for this class");
	}

	public void connect(String deviceAddress) {
		if (this.connectStatus == ConnectStatus.CONNECTING || this.connectStatus == ConnectStatus.CONNECTED) {
			RobotLog.dd(TAG, "connection request to " + deviceAddress + " ignored, already connected");
			return;
		}
		RobotLog.dd(TAG, "connecting to " + deviceAddress);
		this.connectStatus = ConnectStatus.CONNECTING;
		WifiP2pConfig config = new WifiP2pConfig();
		config.deviceAddress = deviceAddress;
		config.wps.setup = 0;
		config.groupOwnerIntent = 1;
		this.wifiP2pManager.connect(this.wifiP2pChannel, config, new ConnectionListener());
	}

	private void onWifiP2pThisDeviceChanged(WifiP2pDevice wifiP2pDevice) {
		this.deviceName = wifiP2pDevice.deviceName;
		this.deviceMacAddress = wifiP2pDevice.deviceAddress;
		RobotLog.vv(TAG, "device information: " + this.deviceName + " " + this.deviceMacAddress);
	}

	public String getFailureReason() {
		return failureReasonToString(this.failureReason);
	}

	public static String failureReasonToString(int reason) {
		switch (reason) {
			case 0:
				return "ERROR";
			case 1:
				return "P2P_UNSUPPORTED";
			case ModernRoboticsConstants.NUMBER_OF_PWM_CHANNELS /*2*/:
				return "BUSY";
			default:
				return "UNKNOWN (reason " + reason + ")";
		}
	}

	private void sendEvent(Event event) {
		if (this.lastEvent != event || this.lastEvent == Event.PEERS_AVAILABLE) {
			this.lastEvent = event;
			if (this.callback != null) {
				this.callback.onNetworkConnectionEvent(event);
			}
		}
	}
}
