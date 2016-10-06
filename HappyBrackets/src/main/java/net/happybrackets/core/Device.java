package net.happybrackets.core;

import java.io.File;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Device {

	final static Logger logger = LoggerFactory.getLogger(Device.class);

	public  final String    myHostname;				    //the hostname for this PI (wifi)
	public  final String    myIP;
	public  final String    myMAC;					    	//the wlan MAC for this PI (wifi)
	public  final String    preferredInterface;
  private       String[]  validInterfaces;

  private static Device singleton = null;

  public static Device getInstance() {
      if(singleton == null) {
          singleton = new Device();
      }
      return singleton;
  }

	private Device() {
        logger.info("Beginning device network setup");
        String tmpHostname = null;
        String tmpIP = null;
		String tmpMAC = null;
		String tmpPreferedInterface = null;
		try {
			NetworkInterface netInterface;
			String operatingSystem = System.getProperty("os.name");
			logger.debug("Detected OS: " + operatingSystem);

            logger.debug("Interfaces:");
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            String favouriteInterfaceName = null;
            ArrayList<NetworkInterface> favouredInterfaces = new ArrayList<>();
            while (interfaces.hasMoreElements()) {
                netInterface = interfaces.nextElement();
                // Windows by default has a lot of extra interfaces,
                //  lets at least try and getInstance a real interface...
                if (isViableNetworkInterface(netInterface)) {
                    //collect all viable interfaces
                    favouredInterfaces.add(netInterface);
                    //favouriteInterfaceName = netInterface.getName();
                    logger.debug("    {} ({}) : VALID", netInterface.getName(), netInterface.getDisplayName());
                }
                else {
                    logger.debug("    {} ({}) : IGNORED", netInterface.getName(), netInterface.getDisplayName());
                }
            }
            if ( !favouredInterfaces.isEmpty() ) {
                logger.debug("Selecting from valid interfaces:");
                favouredInterfaces.forEach( (i) -> logger.debug("    {} ({})", i.getName(), i.getDisplayName()) );

                // Populate valid interfaces array
                validInterfaces = new String[favouredInterfaces.size()];
                for (int i = 0; i < validInterfaces.length ; i++) {
                    //if this list was longer we should use an iterator, but as it is only short this will do
                    validInterfaces[i] = favouredInterfaces.get(i).getName();
                }

                if (favouredInterfaces.size() == 1) {
                    netInterface = favouredInterfaces.get(0);
                } else if (operatingSystem.startsWith("Windows") || operatingSystem.startsWith("Linux")) {
                    favouredInterfaces.sort( (a, b) -> a.getName().compareToIgnoreCase(b.getName()) ); //sort interface by name
                    netInterface = favouredInterfaces.get(favouredInterfaces.size() - 1); //get last, this should be a wlan interface if available
                }
                else if (operatingSystem.startsWith("Mac OS")) {
                    favouredInterfaces.sort( (a, b) -> a.getName().compareToIgnoreCase(b.getName()) ); //sort interface by name
                    netInterface = favouredInterfaces.get(0);
                    //TODO We are hardcoding en0 as the chosen port (on Mac we insist of WiFi), but can we be smarter?
                    netInterface = NetworkInterface.getByName("en0");
//                    netInterface = NetworkInterface.getByName("lo0");
                }
                else {
                    logger.warn("Operating system {} is not expressly handled, defaulting to first favoured interface", operatingSystem);
                    netInterface = favouredInterfaces.get(0);
                }
            }
            else {
                // take a stab in the dark...
                if (operatingSystem.startsWith("Linux") || operatingSystem.startsWith("Windows")) {
                    netInterface = NetworkInterface.getByName("wlan0");
                }
                else if (operatingSystem.startsWith("Mac OS")) {
                    netInterface = NetworkInterface.getByName("en1");
//                    netInterface = NetworkInterface.getByName("lo0");
                }
                else {
                    logger.error("Unable to determine a network interface!");
                    netInterface = NetworkInterface.getByIndex(0); //Maybe the loopback?
                }
            }

            //report back
            logger.debug("Selected interface: {} ({})", netInterface.getName(), netInterface.getDisplayName());

			//Addresses
            ArrayList<InterfaceAddress> addresses = new ArrayList<>();
            netInterface.getInterfaceAddresses().forEach( (a) -> addresses.add(a) );
            addresses.sort( (a, b) -> a.getAddress().getHostAddress().compareTo(b.getAddress().getHostAddress()) );

            logger.debug("Available interface addresses:");
            addresses.forEach( (a) -> logger.debug("    {}", a.getAddress().getHostAddress()) );

            tmpHostname = addresses.get(0).getAddress().getHostName();
            tmpIP       = addresses.get(0).getAddress().getHostAddress();

			if(netInterface != null) {
				//collect our chosen network interface name
				tmpPreferedInterface = netInterface.getName();
				//getInstance MAC
				byte[] mac = netInterface.getHardwareAddress();
				StringBuilder builder = new StringBuilder();
				for (byte a : mac) {
					builder.append(String.format("%02x", a));
				}
				tmpMAC = builder.substring(0, builder.length());
			}
			//first attempt at hostname is to query the /etc/hostname file which should have
			//renamed itself (on the PI) before this Java code runs
			try {
				Scanner s = new Scanner(new File("/etc/hostname"));
				String line = s.next();
				if (line != null && !line.isEmpty() && !line.endsWith("-")) {
					tmpHostname = line;
				}
				s.close();
			} catch(Exception e) {/*Swallow this exception*/}
			//if we don't have the mac derive the MAC from the hostname
			if(tmpMAC == null && tmpHostname != null) {
				tmpMAC = tmpHostname.substring(8, 20);
			}
//
//			//If everything still isn't working lets try via our interface for an IP address
//			if (tmpHostname == null) {
//				String address = netInterface.getInetAddresses().nextElement().getHostAddress();
//				//strip off trailing interface name if present
//				if (address.contains("%")) {
//					tmpHostname = address.split("%")[0];
//				}
//				else {
//					tmpHostname = address;
//				}
//			}

            //strip off trailing interface name if present
            if (tmpIP.contains("%")) {
                tmpIP = tmpIP.split("%")[0];
            }

            //strip off trailing interface name if present
            if (tmpHostname.contains("%")) {
                tmpHostname = tmpHostname.split("%")[0];
            }

		} catch (Exception e) {
			logger.error("Error encountered when assessing interfaces and addresses!", e);
		}

		//ensure we have a local suffix
		// Windows won't care either way but *nix systems need it
		//If there are ':' we are probably dealing with a IPv6 address
		if (tmpHostname != null && !tmpHostname.contains(".") && !tmpHostname.contains(":")) {
			tmpHostname += ".local";	//we'll assume a .local extension is required if no extension exists
		}

		myHostname          = tmpHostname;
    myIP                = tmpIP;
		myMAC               = tmpMAC;
		preferredInterface  = tmpPreferedInterface;
		//report
		logger.debug("My hostname is:            {}", myHostname);
        logger.debug("My IP address is:          {}", myIP);
		logger.debug("My MAC address is:         {}", myMAC);
		logger.debug("My preferred interface is: {}", preferredInterface);
        logger.debug("Device network setup complete");
    }

	public static boolean isViableNetworkInterface(NetworkInterface ni) {
		try {
			if ( !ni.supportsMulticast()												) return false;
			if ( ni.isLoopback()																) return false;
			if ( !ni.isUp()										  								) return false;
			if ( ni.isVirtual()																	) return false;
			if ( ni.getDisplayName().matches(".*[Vv]irtual.*")	) return false; //try and catch out any interfaces which don't admit to being virtual
		} catch (SocketException e) {
			logger.error("Error checking interface {}", ni.getName(), e);
			return false;
		}
		return true;
	}

    // return a clone to prevent values from being mysteriously overwritten
    public String[] getValidInterfaces() { return validInterfaces.clone(); }

}
