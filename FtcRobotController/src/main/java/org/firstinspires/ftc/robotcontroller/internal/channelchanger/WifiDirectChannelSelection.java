/*
 * Copyright (c) 2014 Qualcomm Technologies Inc
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * (subject to the limitations in the disclaimer below) provided that the following conditions are
 * met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions
 * and the following disclaimer in the documentation and/or other materials provided with the
 * distribution.
 *
 * Neither the name of Qualcomm Technologies Inc nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written permission.
 *
 * NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE GRANTED BY THIS LICENSE. THIS
 * SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
 * THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.firstinspires.ftc.robotcontroller.internal.channelchanger;

import android.content.Context;
import android.net.wifi.WifiManager;

import com.qualcomm.ftccommon.DbgLog;
import com.qualcomm.robotcore.util.RobotLog;
import com.qualcomm.robotcore.util.RunShellCommand;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import eu.chainfire.libsuperuser.Shell;

/**
 * Class which uses root access to configure the Wifi Direct channel.
 * Methods are synchronous and need to be run in a seperate thread
 */
public class WifiDirectChannelSelection {

	private final List<String> getWifiStatusFilesCommands;
	private final List<String> writeWifiStatusFilesCommands;

	private final static String wpaFile = "wpa_supplicant.conf";
	private final static String p2pFile = "p2p_supplicant.conf";

	private final String saveDir;


	private final static int CONF_FILE_MAX_SIZE = 8 * 1024;


	private final WifiManager wifiManager;

	private final RunShellCommand shell = new RunShellCommand();

	public WifiDirectChannelSelection(Context context, WifiManager wifiManager) {

		this.saveDir = context.getFilesDir().getAbsolutePath() + "/";
		this.wifiManager = wifiManager;

		shell.enableLogging(true);

		//set up command lists
		getWifiStatusFilesCommands = Arrays.asList(
				"cp /data/misc/wifi/wpa_supplicant.conf " + saveDir + "/wpa_supplicant.conf",
				"cp /data/misc/wifi/p2p_supplicant.conf " + saveDir + "/p2p_supplicant.conf",
				"chmod 666 " + saveDir + "/*supplicant*");

		writeWifiStatusFilesCommands = Arrays.asList(
				"cp " + saveDir + "/p2p_supplicant.conf /data/misc/wifi/p2p_supplicant.conf",
				"cp " + saveDir + "/wpa_supplicant.conf /data/misc/wifi/wpa_supplicant.conf",
				"rm " + saveDir + "/*supplicant*",
				"chown system.wifi /data/misc/wifi/wpa_supplicant.conf",
				"chown system.wifi /data/misc/wifi/p2p_supplicant.conf");
	}

	public boolean config(int wifiClass, int wifiChannel) throws IOException {
		wifiManager.setWifiEnabled(false);

		Shell.SU.run(getWifiStatusFilesCommands);

		configureP2p(wifiClass, wifiChannel);
		forgetNetworks();

		Shell.SU.run(writeWifiStatusFilesCommands);

		boolean restarted = restartWpaSupplicant();

		wifiManager.setWifiEnabled(true);

		return restarted;
	}

	// This method has a heavy dependency on the Android version of 'ps'.
	// Returns true if it was able to restart or wpa_supplicant was not running, false if it failed to restart because it couldn't parse the ps output

	// Luckily, if it fails, the process can still work, just the user has to restart their device
	private boolean restartWpaSupplicant(){


		List<String> lines = Shell.SH.run("/system/bin/ps | grep \'wpa_supplicant\'");

		if(lines.size() != 1 ||!lines.get(0).contains("wpa_supplicant"))
		{
			DbgLog.error("Failed to restart wpa_supplicant! Unexpected ps output!");
			DbgLog.error("PS output: " + lines.toString());

			return false;
		}
		else if(lines.size() == 0)
		{
			DbgLog.msg("could not find wpa_supplicant PID, assuming it isn't running");
			return true;
		}
		else
		{
			int pid = -1;
			String[] tokens = lines.get(0).split("\\s+");

			try
			{
				pid = Integer.parseInt(tokens[1]); // if 'ps' changes format this call will fail
				Shell.SU.run("kill -HUP " + pid);
				return true;
			}
			catch(NumberFormatException ex)
			{
				DbgLog.error("Failed to restart wpa_supplicant! Couldn't parse ps output");
				return false;
			}
		}


	}

	private void forgetNetworks() {
		try {
			char[] buffer = new char[4 * 1024];

			FileReader fr = new FileReader(saveDir + wpaFile);
			int length = fr.read(buffer);
			fr.close();

			String s = new String(buffer, 0, length);
			RobotLog.v("WPA FILE: \n" + s);

			// remove all saved AP's
			s = s.replaceAll("(?s)network\\s*=\\{.*\\}", "");

			// remove blank lines
			s = s.replaceAll("(?m)^\\s+$", "");

			RobotLog.v("WPA REPLACE: \n" + s);

			FileWriter fw = new FileWriter(saveDir + wpaFile);
			fw.write(s);
			fw.close();
		} catch (FileNotFoundException e) {
			RobotLog.e("File not found: " + e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			RobotLog.e("FIO exception: " + e.toString());
			e.printStackTrace();
		}
	}

	private void configureP2p(int wifiClass, int wifiChannel) {
		try {
			char[] buffer = new char[CONF_FILE_MAX_SIZE];

			FileReader fr = new FileReader(saveDir + p2pFile);
			int length = fr.read(buffer);
			fr.close();

			String s = new String(buffer, 0, length);
			RobotLog.v("P2P FILE: \n" + s);

			// remove any old p2p settings
			s = s.replaceAll("p2p_listen_reg_class\\w*=.*", "");
			s = s.replaceAll("p2p_listen_channel\\w*=.*", "");
			s = s.replaceAll("p2p_oper_reg_class\\w*=.*", "");
			s = s.replaceAll("p2p_oper_channel\\w*=.*", "");
			s = s.replaceAll("p2p_pref_chan\\w*=.*", "");

			// remove all saved networks
			s = s.replaceAll("(?s)network\\s*=\\{.*\\}", "");

			// remove blank lines
			s = s.replaceAll("(?m)^\\s+$", "");

			// add our config items
			s += "p2p_oper_reg_class=" + wifiClass + "\n";
			s += "p2p_oper_channel=" + wifiChannel + "\n";
			s += "p2p_pref_chan=" + wifiClass + ":" + wifiChannel + "\n";

			RobotLog.v("P2P REPLACE: \n" + s);

			FileWriter fw = new FileWriter(saveDir + p2pFile);
			fw.write(s);
			fw.close();
		} catch (FileNotFoundException e) {
			RobotLog.e("File not found: " + e.toString());
			e.printStackTrace();
		} catch (IOException e) {
			RobotLog.e("FIO exception: " + e.toString());
			e.printStackTrace();
		}
	}

}
