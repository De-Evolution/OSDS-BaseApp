//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.firstinspires.ftc.robotcore.internal.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.net.wifi.p2p.WifiP2pDevice;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.qualcomm.robotcore.exception.RobotCoreException;
import com.qualcomm.robotcore.robocol.Command;
import com.qualcomm.robotcore.robocol.PeerDiscovery;
import com.qualcomm.robotcore.robocol.RobocolDatagram;
import com.qualcomm.robotcore.robocol.RobocolDatagramSocket;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.RobotLog;
import com.qualcomm.robotcore.wifi.NetworkConnection;
import com.qualcomm.robotcore.wifi.NetworkConnection.ConnectStatus;
import com.qualcomm.robotcore.wifi.NetworkConnection.Event;
import com.qualcomm.robotcore.wifi.NetworkConnection.NetworkConnectionCallback;
import com.qualcomm.robotcore.wifi.NetworkConnectionFactory;
import com.qualcomm.robotcore.wifi.NetworkType;
import com.qualcomm.robotcore.wifi.SoftApAssistant;
import com.qualcomm.robotcore.wifi.WifiDirectAssistant;

import org.firstinspires.ftc.robotcore.internal.network.RecvLoopRunnable.RecvLoopCallback;
import org.firstinspires.ftc.robotcore.internal.network.SendOnceRunnable.ClientCallback;
import org.firstinspires.ftc.robotcore.internal.network.SendOnceRunnable.Parameters;

import java.net.InetAddress;
import java.net.SocketException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class NetworkConnectionHandler {
	public static final String TAG = "NetworkConnectionHandler";
	private static final NetworkConnectionHandler theInstance = new NetworkConnectionHandler();
	@Nullable
	protected WifiLock wifiLock;
	protected boolean setupNeeded = true;
	protected Context context;
	protected ElapsedTime lastRecvPacket = new ElapsedTime();
	protected InetAddress remoteAddr;
	protected RobocolDatagramSocket socket;
	protected ScheduledExecutorService sendLoopService = Executors.newSingleThreadScheduledExecutor();
	protected ScheduledFuture<?> sendLoopFuture;
	protected SendOnceRunnable sendOnceRunnable;
	protected SetupRunnable setupRunnable;
	@Nullable
	protected String connectionOwner;
	@Nullable
	protected String connectionOwnerPassword;
	protected NetworkConnection networkConnection = null;
	protected final NetworkConnectionHandler.NetworkConnectionCallbackChainer theNetworkConnectionCallback = new NetworkConnectionHandler.NetworkConnectionCallbackChainer();
	protected RecvLoopRunnable recvLoopRunnable;
	protected final NetworkConnectionHandler.RecvLoopCallbackChainer theRecvLoopCallback = new NetworkConnectionHandler.RecvLoopCallbackChainer();
	protected final Object callbackLock = new Object();

	public NetworkConnectionHandler() {
	}

	public static NetworkConnectionHandler getInstance() {
		return theInstance;
	}

	public static WifiLock newWifiLock(Context context) {
		WifiManager wifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
		return wifiManager.createWifiLock(3, "");
	}

	public NetworkType getDefaultNetworkType(Context context) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		return NetworkType.fromString(preferences.getString("NETWORK_CONNECTION_TYPE", NetworkType.WIFIDIRECT.toString()));
	}

	public void init(@NonNull WifiLock wifiLock, @NonNull NetworkType networkType, @NonNull String owner, @NonNull String password, @NonNull Context context) {
		this.wifiLock = wifiLock;
		this.connectionOwner = owner;
		this.connectionOwnerPassword = password;
		this.context = context;
		this.shutdown();
		this.networkConnection = null;
		this.initNetworkConnection(networkType);
		this.startWifiAndDiscoverConnections();
	}

	public void init(@NonNull NetworkType networkType, @NonNull Context context) {
		this.context = context;
		this.initNetworkConnection(networkType);
	}

	private void initNetworkConnection(NetworkType networkType) {
		if(this.networkConnection != null && this.networkConnection.getNetworkType() != networkType) {
			this.shutdown();
			this.networkConnection = null;
		}

		if(this.networkConnection == null) {
			this.networkConnection = NetworkConnectionFactory.getNetworkConnection(networkType, this.context);
			Object var2 = this.callbackLock;
			synchronized(this.callbackLock) {
				this.networkConnection.setCallback(this.theNetworkConnectionCallback);
			}
		}

	}

	public void setRecvLoopRunnable(RecvLoopRunnable recvLoopRunnable) {
		Object var2 = this.callbackLock;
		synchronized(this.callbackLock) {
			this.recvLoopRunnable = recvLoopRunnable;
			this.recvLoopRunnable.setCallback(this.theRecvLoopCallback);
		}
	}

	public NetworkType getNetworkType() {
		return this.networkConnection.getNetworkType();
	}

	public void startWifiAndDiscoverConnections() {
		this.acquireWifiLock();
		this.networkConnection.enable();
		if(!this.networkConnection.isConnected()) {
			this.networkConnection.discoverPotentialConnections();
		}

	}

	public void startConnection(@NonNull String owner, @NonNull String password) {
		this.connectionOwner = owner;
		this.connectionOwnerPassword = password;
		this.networkConnection.connect(this.connectionOwner, this.connectionOwnerPassword);
	}

	public boolean connectedWithUnexpectedDevice()
	{
		if(this.connectionOwner != null && networkConnection.getConnectionOwnerMacAddress() != null && (!this.connectionOwner.equals(this.networkConnection.getConnectionOwnerMacAddress()))) {
			RobotLog.ee("NetworkConnectionHandler", "Network Connection - connected to " + this.networkConnection.getConnectionOwnerMacAddress() + ", expected " + this.connectionOwner);
			return true;
		} else {
			return false;
		}
	}

	public void acquireWifiLock() {
		if(this.wifiLock != null) {
			this.wifiLock.acquire();
		}

	}

	public boolean isNetworkConnected() {
		return this.networkConnection.isConnected();
	}

	public boolean isWifiDirect() {
		return this.networkConnection.getNetworkType().equals(NetworkType.WIFIDIRECT);
	}

	public void discoverPotentialConnections() {
		this.networkConnection.discoverPotentialConnections();
	}

	public void cancelConnectionSearch() {
		this.networkConnection.cancelPotentialConnections();
	}

	public String getFailureReason() {
		return this.networkConnection.getFailureReason();
	}

	public String getConnectionOwnerName() {
		return this.networkConnection.getConnectionOwnerName();
	}

	public String getDeviceName() {
		return this.networkConnection.getDeviceName();
	}

	public void stop() {
		this.networkConnection.disable();
		if(this.wifiLock != null && this.wifiLock.isHeld()) {
			this.wifiLock.release();
		}

	}

	public boolean connectingOrConnected() {
		ConnectStatus status = this.networkConnection.getConnectStatus();
		return status == ConnectStatus.CONNECTED || status == ConnectStatus.CONNECTING;
	}

	public boolean connectionMatches(String name) {
		return this.connectionOwner != null && this.connectionOwner.equals(name);
	}

	public synchronized CallbackResult handleConnectionInfoAvailable(SocketConnect socketConnect) {
		CallbackResult result = CallbackResult.HANDLED;
		if(this.networkConnection.isConnected() && this.setupNeeded) {
			this.setupNeeded = false;
			if(this.networkConnection.getNetworkType() == NetworkType.SOFTAP) {
				try {
					Thread.sleep(2000L);
				} catch (InterruptedException var6) {
					Thread.currentThread().interrupt();
				}
			}

			Object e = this.callbackLock;
			synchronized(this.callbackLock) {
				this.setupRunnable = new SetupRunnable(this.theRecvLoopCallback, this.networkConnection, this.lastRecvPacket, socketConnect);
			}

			(new Thread(this.setupRunnable)).start();
		}

		return result;
	}

	public synchronized CallbackResult handlePeersAvailable() {
		CallbackResult result = CallbackResult.NOT_HANDLED;
		NetworkType networkType = this.networkConnection.getNetworkType();
		switch(networkType) {
			case WIFIDIRECT:
				result = this.handleWifiDirectPeersAvailable();
				break;
			case SOFTAP:
				result = this.handleSoftAPPeersAvailable();
				break;
			case LOOPBACK:
			case UNKNOWN_NETWORK_TYPE:
				RobotLog.e("Unhandled peers available event: " + networkType.toString());
			default:
		}

		return result;
	}

	private CallbackResult handleSoftAPPeersAvailable() {
		CallbackResult result = CallbackResult.NOT_HANDLED;
		List scanResults = ((SoftApAssistant)this.networkConnection).getScanResults();
		Iterator i$ = scanResults.iterator();

		while(i$.hasNext()) {
			ScanResult scanResult = (ScanResult)i$.next();
			RobotLog.v(scanResult.SSID);
			if(scanResult.SSID.equalsIgnoreCase(this.connectionOwner)) {
				this.networkConnection.connect(this.connectionOwner, this.connectionOwnerPassword);
				result = CallbackResult.HANDLED;
				break;
			}
		}

		return result;
	}

	private CallbackResult handleWifiDirectPeersAvailable() {
		CallbackResult result = CallbackResult.NOT_HANDLED;
		List peers = ((WifiDirectAssistant)this.networkConnection).getPeers();
		Iterator i$ = peers.iterator();

		while(i$.hasNext()) {
			WifiP2pDevice peer = (WifiP2pDevice)i$.next();
			if(peer.deviceAddress.equalsIgnoreCase(this.connectionOwner)) {
				this.networkConnection.connect(peer.deviceAddress);
				result = CallbackResult.HANDLED;
				break;
			}
		}

		return result;
	}

	public synchronized void updateConnection(@NonNull RobocolDatagram packet, @Nullable Parameters parameters, ClientCallback clientCallback) throws RobotCoreException {
		if(packet.getAddress().equals(this.remoteAddr)) {
			if(this.sendOnceRunnable != null) {
				this.sendOnceRunnable.onPeerConnected(false);
			}

			if(clientCallback != null) {
				clientCallback.peerConnected(false);
			}

		} else {
			if(parameters == null) {
				parameters = new Parameters();
			}

			PeerDiscovery peerDiscovery = PeerDiscovery.forReceive();
			peerDiscovery.fromByteArray(packet.getData());
			this.remoteAddr = packet.getAddress();
			RobotLog.vv("PeerDiscovery", "new remote peer discovered: " + this.remoteAddr.getHostAddress());
			if(this.socket == null && this.setupRunnable != null) {
				this.socket = this.setupRunnable.getSocket();
			}

			if(this.socket != null) {
				try {
					this.socket.connect(this.remoteAddr);
				} catch (SocketException var6) {
					throw RobotCoreException.createChained(var6, "unable to connect to %s", new Object[]{this.remoteAddr.toString()});
				}

				if(this.sendLoopFuture == null || this.sendLoopFuture.isDone()) {
					RobotLog.vv("NetworkConnectionHandler", "starting sending loop");
					this.sendOnceRunnable = new SendOnceRunnable(this.context, clientCallback, this.socket, this.lastRecvPacket, parameters);
					this.sendLoopFuture = this.sendLoopService.scheduleAtFixedRate(this.sendOnceRunnable, 0L, 40L, TimeUnit.MILLISECONDS);
				}

				if(this.sendOnceRunnable != null) {
					this.sendOnceRunnable.onPeerConnected(true);
				}

				if(clientCallback != null) {
					clientCallback.peerConnected(true);
				}
			}

		}
	}

	public synchronized boolean removeCommand(Command cmd) {
		return this.sendOnceRunnable != null && this.sendOnceRunnable.removeCommand(cmd);
	}

	public synchronized void sendCommand(Command cmd) {
		if(this.sendOnceRunnable != null) {
			this.sendOnceRunnable.sendCommand(cmd);
		}

	}

	public CallbackResult processAcknowledgments(Command command) throws RobotCoreException {
		if(command.isAcknowledged()) {
			this.removeCommand(command);
			return CallbackResult.HANDLED;
		} else {
			command.acknowledge();
			this.sendCommand(command);
			return CallbackResult.NOT_HANDLED;
		}
	}

	public synchronized void sendDatagram(RobocolDatagram datagram) {
		if(this.socket != null && this.socket.getInetAddress() != null) {
			this.socket.send(datagram);
		}

	}

	public synchronized void clientDisconnect() {
		if(this.sendOnceRunnable != null) {
			this.sendOnceRunnable.clearCommands();
		}

		this.remoteAddr = null;
	}

	public synchronized void shutdown() {
		if(this.setupRunnable != null) {
			this.setupRunnable.shutdown();
			this.setupRunnable = null;
		}

		if(this.sendLoopFuture != null) {
			this.sendLoopFuture.cancel(true);
			this.sendOnceRunnable = null;
			this.sendLoopFuture = null;
		}

		if(this.socket != null) {
			this.socket.close();
			this.socket = null;
		}

		this.remoteAddr = null;
		this.setupNeeded = true;
	}

	public void pushNetworkConnectionCallback(@Nullable NetworkConnectionCallback callback) {
		Object var2 = this.callbackLock;
		synchronized(this.callbackLock) {
			this.theNetworkConnectionCallback.push(callback);
		}
	}

	public void removeNetworkConnectionCallback(@Nullable NetworkConnectionCallback callback) {
		Object var2 = this.callbackLock;
		synchronized(this.callbackLock) {
			this.theNetworkConnectionCallback.remove(callback);
		}
	}

	public void pushReceiveLoopCallback(@Nullable RecvLoopCallback callback) {
		Object var2 = this.callbackLock;
		synchronized(this.callbackLock) {
			this.theRecvLoopCallback.push(callback);
		}
	}

	public void removeReceiveLoopCallback(@Nullable RecvLoopCallback callback) {
		Object var2 = this.callbackLock;
		synchronized(this.callbackLock) {
			this.theRecvLoopCallback.remove(callback);
		}
	}

	protected class RecvLoopCallbackChainer implements RecvLoopCallback {
		protected final Object lock = new Object();
		protected final LinkedList<RecvLoopCallback> callbacks = new LinkedList();

		protected RecvLoopCallbackChainer() {
		}

		void push(@Nullable RecvLoopCallback callback) {
			Object var2 = this.lock;
			synchronized(this.lock) {
				this.remove(callback);
				if(callback != null && !this.callbacks.contains(callback)) {
					this.callbacks.push(callback);
				}

			}
		}

		void remove(@Nullable RecvLoopCallback callback) {
			Object var2 = this.lock;
			synchronized(this.lock) {
				if(callback != null) {
					this.callbacks.remove(callback);
				}

			}
		}

		public CallbackResult packetReceived(RobocolDatagram packet) throws RobotCoreException {
			Object var2 = this.lock;
			synchronized(this.lock) {
				Iterator i$ = this.callbacks.iterator();

				CallbackResult result;
				do {
					if(!i$.hasNext()) {
						return CallbackResult.NOT_HANDLED;
					}

					RecvLoopCallback callback = (RecvLoopCallback)i$.next();
					result = callback.packetReceived(packet);
				} while(!result.stopDispatch());

				return CallbackResult.HANDLED;
			}
		}

		public CallbackResult peerDiscoveryEvent(RobocolDatagram packet) throws RobotCoreException {
			Object var2 = this.lock;
			synchronized(this.lock) {
				Iterator i$ = this.callbacks.iterator();

				CallbackResult result;
				do {
					if(!i$.hasNext()) {
						return CallbackResult.NOT_HANDLED;
					}

					RecvLoopCallback callback = (RecvLoopCallback)i$.next();
					result = callback.peerDiscoveryEvent(packet);
				} while(!result.stopDispatch());

				return CallbackResult.HANDLED;
			}
		}

		public CallbackResult heartbeatEvent(RobocolDatagram packet, long tReceived) throws RobotCoreException {
			Object var4 = this.lock;
			synchronized(this.lock) {
				Iterator i$ = this.callbacks.iterator();

				CallbackResult result;
				do {
					if(!i$.hasNext()) {
						return CallbackResult.NOT_HANDLED;
					}

					RecvLoopCallback callback = (RecvLoopCallback)i$.next();
					result = callback.heartbeatEvent(packet, tReceived);
				} while(!result.stopDispatch());

				return CallbackResult.HANDLED;
			}
		}

		public CallbackResult commandEvent(Command command) throws RobotCoreException {
			Object var2 = this.lock;
			synchronized(this.lock) {
				boolean handled = false;
				Iterator callbackNames = this.callbacks.iterator();

				CallbackResult callback;
				do {
					if(!callbackNames.hasNext()) {
						if(!handled) {
							StringBuilder callbackNames1 = new StringBuilder();

							RecvLoopCallback callback1;
							for(Iterator i$1 = this.callbacks.iterator(); i$1.hasNext(); callbackNames1.append(callback1.getClass().getSimpleName())) {
								callback1 = (RecvLoopCallback)i$1.next();
								if(callbackNames1.length() > 0) {
									callbackNames1.append(",");
								}
							}

							RobotLog.vv("Robocol", "unable to process command %s callbacks=%s", new Object[]{command.getName(), callbackNames1.toString()});
						}

						return handled?CallbackResult.HANDLED:CallbackResult.NOT_HANDLED;
					}

					RecvLoopCallback i$ = (RecvLoopCallback)callbackNames.next();
					callback = i$.commandEvent(command);
					handled = handled || callback.isHandled();
				} while(!callback.stopDispatch());

				return CallbackResult.HANDLED;
			}
		}

		public CallbackResult telemetryEvent(RobocolDatagram packet) throws RobotCoreException {
			Object var2 = this.lock;
			synchronized(this.lock) {
				Iterator i$ = this.callbacks.iterator();

				CallbackResult result;
				do {
					if(!i$.hasNext()) {
						return CallbackResult.NOT_HANDLED;
					}

					RecvLoopCallback callback = (RecvLoopCallback)i$.next();
					result = callback.telemetryEvent(packet);
				} while(!result.stopDispatch());

				return CallbackResult.HANDLED;
			}
		}

		public CallbackResult gamepadEvent(RobocolDatagram packet) throws RobotCoreException {
			Object var2 = this.lock;
			synchronized(this.lock) {
				Iterator i$ = this.callbacks.iterator();

				CallbackResult result;
				do {
					if(!i$.hasNext()) {
						return CallbackResult.NOT_HANDLED;
					}

					RecvLoopCallback callback = (RecvLoopCallback)i$.next();
					result = callback.gamepadEvent(packet);
				} while(!result.stopDispatch());

				return CallbackResult.HANDLED;
			}
		}

		public CallbackResult emptyEvent(RobocolDatagram packet) throws RobotCoreException {
			Object var2 = this.lock;
			synchronized(this.lock) {
				Iterator i$ = this.callbacks.iterator();

				CallbackResult result;
				do {
					if(!i$.hasNext()) {
						return CallbackResult.NOT_HANDLED;
					}

					RecvLoopCallback callback = (RecvLoopCallback)i$.next();
					result = callback.emptyEvent(packet);
				} while(!result.stopDispatch());

				return CallbackResult.HANDLED;
			}
		}

		public CallbackResult reportGlobalError(String error, boolean recoverable) {
			Object var3 = this.lock;
			synchronized(this.lock) {
				Iterator i$ = this.callbacks.iterator();

				CallbackResult result;
				do {
					if(!i$.hasNext()) {
						return CallbackResult.NOT_HANDLED;
					}

					RecvLoopCallback callback = (RecvLoopCallback)i$.next();
					result = callback.reportGlobalError(error, recoverable);
				} while(!result.stopDispatch());

				return CallbackResult.HANDLED;
			}
		}
	}

	protected class NetworkConnectionCallbackChainer implements NetworkConnectionCallback {
		protected final Object lock = new Object();
		protected final LinkedList<NetworkConnectionCallback> callbacks = new LinkedList();

		protected NetworkConnectionCallbackChainer() {
		}

		void push(@Nullable NetworkConnectionCallback callback) {
			Object var2 = this.lock;
			synchronized(this.lock) {
				this.remove(callback);
				if(callback != null && !this.callbacks.contains(callback)) {
					this.callbacks.push(callback);
				}

			}
		}

		void remove(@Nullable NetworkConnectionCallback callback) {
			Object var2 = this.lock;
			synchronized(this.lock) {
				if(callback != null) {
					this.callbacks.remove(callback);
				}

			}
		}

		public CallbackResult onNetworkConnectionEvent(Event event) {
			Object var2 = this.lock;
			synchronized(this.lock) {
				Iterator i$ = this.callbacks.iterator();

				CallbackResult result;
				do {
					if(!i$.hasNext()) {
						return CallbackResult.NOT_HANDLED;
					}

					NetworkConnectionCallback callback = (NetworkConnectionCallback)i$.next();
					result = callback.onNetworkConnectionEvent(event);
				} while(!result.stopDispatch());

				return CallbackResult.HANDLED;
			}
		}
	}
}
