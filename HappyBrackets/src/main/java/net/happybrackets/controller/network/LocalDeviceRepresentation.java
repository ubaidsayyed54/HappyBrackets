/*
 * Copyright 2016 Ollie Bown
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.happybrackets.controller.network;

import java.io.IOException;
import java.net.*;
import java.nio.channels.UnresolvedAddressException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import de.sciss.net.OSCListener;
import net.happybrackets.controller.config.ControllerConfig;
import de.sciss.net.OSCMessage;
import de.sciss.net.OSCServer;

import net.happybrackets.core.ErrorListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalDeviceRepresentation {

	final static Logger logger = LoggerFactory.getLogger(LocalDeviceRepresentation.class);

	public long lastTimeSeen;
	public final String deviceName;
	public final String hostname;
	public final String address;
	public List<String> preferredAddressStrings; 	//This list contains, in order of preference: address, hostname, deviceName, hostname.local or deviceName.local.
	private int id;
	private InetSocketAddress socketAddress;
	private final OSCServer server;
	public final boolean[] groups;
	private ControllerConfig config;

	public interface StatusUpdateListener {
		public void update(String state);
	}

	private List<StatusUpdateListener> statusUpdateListenerList;

	private List<ErrorListener> errorListenerList;

	private String log;
	public interface LogListener {
		public void newLogMessage(String message);
	}
	private List<LogListener> logListenerList;



	private String status = "Status unknown";

	public LocalDeviceRepresentation(String deviceName, String hostname, String addr, int id, OSCServer server, ControllerConfig config) {

		this.deviceName						= deviceName;
		this.hostname   					= hostname;
    	this.address    					= addr;
		this.socketAddress = null;
		this.id         					= id;
		this.server     					= server;
		this.config     					= config;
		groups          					= new boolean[4];
		statusUpdateListenerList  = new ArrayList<>();
		logListenerList = new ArrayList<>();
		errorListenerList = new ArrayList<>();

		// Set-up log monitor.
		log = "";
		server.addOSCListener(new OSCListener() {
			@Override
			public void messageReceived(OSCMessage msg, SocketAddress source, long timestamp) {
				if (msg.getName().equals("/device/log") && ((Integer) msg.getArg(0)) == id) {
					String newLogOutput = (String) msg.getArg(1);
					log = log + "\n" + newLogOutput;
					logger.debug("Received new log output from device {} ({}): {}", hostname, id, newLogOutput);
					for (LogListener listener : logListenerList) {
						listener.newLogMessage(newLogOutput);
					}
				}
			}
		});
	}

	public void setID(int id) {
		this.id = id;
	}

	public int getID() {
		return id;
	}

	private void lazySetupAddressStrings() {
		if(preferredAddressStrings == null) {
			preferredAddressStrings = new LinkedList<>();
			preferredAddressStrings.add(deviceName + ".local");
			preferredAddressStrings.add(address);
			preferredAddressStrings.add(hostname + ".local");
			preferredAddressStrings.add(hostname);
			preferredAddressStrings.add(deviceName);
		}
	}

	public synchronized void send(String msgName, Object... args) {
		if(hostname.startsWith("Virtual Test Device")) {
			return;
		}
		OSCMessage msg = new OSCMessage(msgName, args);
		lazySetupAddressStrings();
		boolean success = false;
		int count = 0;
		while(!success) {
			try {
				if (socketAddress == null) {
					socketAddress = new InetSocketAddress(preferredAddressStrings.get(0), config.getControlToDevicePort());
				}
				server.send(msg, socketAddress);
				success = true;
			} catch (UnresolvedAddressException | IOException e1) {
				logger.error("Error sending to device {} using address {}! (Setting socketAddress back to null).",
						deviceName, preferredAddressStrings.get(0), e1);
				//set the socketAddress back to null as it will need to be rebuilt
				socketAddress = null;
				//rotate the preferredAddressStrings list to try the next one in the list
				String failedString = preferredAddressStrings.remove(0);
				preferredAddressStrings.add(failedString);
				if(count > 4) break;
				count++;
			}
		}
	}

	public synchronized void send(byte[]... data) {
		lazySetupAddressStrings();
		boolean success = false;
		int count = 0;
		boolean possibleIPvIssue = false;
		List<Exception> exceptions = new ArrayList<>(5);
		while(!success) {
			try {
				Socket s = new Socket(preferredAddressStrings.get(0), ControllerConfig.getInstance().getCodeToDevicePort());
				for (byte[] d : data) {
					s.getOutputStream().write(d);
				}
				s.close();
				success = true;
				logger.debug("Success sending to device {} using address {}!",
						deviceName, preferredAddressStrings.get(0));
			} catch (IOException | IllegalArgumentException e1) {
				logger.error("Error sending to device {} using address {}! (Setting socketAddress back to null).",
						deviceName, preferredAddressStrings.get(0), e1);
				//set the socketAddress back to null as it will need to be rebuilt
				socketAddress = null;
				//rotate the preferredAddressStrings list to try the next one in the list
				String failedString = preferredAddressStrings.remove(0);	//remove from front
				preferredAddressStrings.add(failedString);		//add to end

				exceptions.add(e1);
				possibleIPvIssue |= e1 instanceof java.net.SocketException && e1.getMessage().contains("rotocol");
				if(count > 4) break;
				count++;
			}
		}

		if (possibleIPvIssue) {
			logger.error("It looks like there might be an IPv4/IPv6 incompatibility, try setting the JVM option -Djava.net.preferIPv6Addresses=true or -Djava.net.preferIPv4Addresses=true");
		}
		// Communicate the errors to the plugin gui if it's running (and anything else that's listening).
		exceptions.forEach((e) -> sendError("Error sending to device!", e));
	}

	public void addStatusUpdateListener(StatusUpdateListener listener) {
		statusUpdateListenerList.add(listener);
	}

	public void addErrorListener(ErrorListener listener) {
		errorListenerList.add(listener);
	}
	public void removeErrorListener(ErrorListener listener) {
		errorListenerList.remove(listener);
	}

	public void setStatus(String arg) {
		status = arg;
		for(StatusUpdateListener statusUpdateListener : statusUpdateListenerList) {
			statusUpdateListener.update(status);
		}
	}

	private void sendError(String description, Exception ex) {
		for (ErrorListener l : errorListenerList) {
			l.errorOccurred(this.getClass(), description, ex);
		}
	}

	public void addLogListener(LogListener listener) {
		logListenerList.add(listener);
	}
	public void removeLogListener(LogListener listener) {
		logListenerList.remove(listener);
	}

	public String getDeviceLog() {
		return log;
	}
}
