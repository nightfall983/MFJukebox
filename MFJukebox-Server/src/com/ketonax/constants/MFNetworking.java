package com.ketonax.constants;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import edu.rutgers.winlab.jmfapi.GUID;

public final class MFNetworking {
	/* Commands To Devices */
	public static final String PLAY_SONG_CMD = "/play_song";

	/* Commands from devices */
	public static final String JOIN_STATION_CMD = "/join_station";
	public static final String LEAVE_STATION_CMD = "/leave_station";
	public static final String ADD_SONG_CMD = "/add_song";
	public static final String GET_PLAYLIST_CMD = "/get_playlist";

	/* Commands To Server */
	public static final String CREATE_STATION_CMD = "/new_station";
	public static final String STATION_LIST_REQUEST_CMD = "/request_station_list";

	/* Notifications to devices */
	public static final String STATION_LIST_REQUEST_RESPONSE = "/station_list_response";
	public static final String STATION_KILLED_NOTIFIER = "/station_terminated";
	public static final String STATION_ADDED_NOTIFIER = "/station_added";
	public static final String SONG_ADDED_NOTIFIER = "/song_added";
	public static final String SONG_REMOVED_NOTIFIER = "/song_removed";
	public static final String USER_ADDED_NOTIFIER = "/user_added";
	public static final String USER_REMOVED_NOTIFIER = "/user_removed";
	public static final String CURRENTLY_PLAYING_NOTIFIER = "/currently_playing";

	/* Notifications to server */
	public static final String EXIT_JUKEBOX_NOTIFIER = "/jukebox_user_exit";

	/* Response to devices */
	public static final String SONG_ON_LIST_RESPONSE = "/song_on_list";
	public static final String USER_ON_LIST_RESPONSE = "/user_on_list";

	/* Constants */
	public static final int DATA_LIMIT_SIZE = 1024;

	/* Separator string */
	public static final String SEPERATOR = ",";

	/* Message identifier */
	public static final String JUKEBOX_MESSAGE_IDENTIFIER = "/";
	
	/* All users GUID */
	public static final GUID ALL_USERS = new GUID(3);
	
	/* Server IP address */
	public static final String SERVER_IP_STRING = "192.168.1.143";

	/* Server port */
	public static final int SERVER_PORT = 61001;

	/* Server GUID */
	public static final GUID SERVER_GUID = new GUID(2);
	
	/* Router INFO */
	public static final String ROUTER_IP = "192.168.1.1";
	public static final GUID ROUTER_GUID = new GUID(9999999);
	
	/* GNRS IP address */
	public static final String GNRS_IP_STRING = "192.168.1.1";

	/* GNRS ports */
	public static final int GNRS_PORT = 5000;
	public static final int GNRS_LISTEN_PORT = 3000;
	
	public static String getLocalIP(){
		
		String ip = null;
		Enumeration<NetworkInterface> interfaces = null;
		
		try {
			interfaces = NetworkInterface.getNetworkInterfaces();
		} catch (SocketException e) {
			e.printStackTrace();
		}
		
		while (interfaces.hasMoreElements()) {
			NetworkInterface current = interfaces.nextElement();
			
			try {
				if (!current.isUp() || current.isLoopback()
						|| current.isVirtual())
					continue;
			} catch (SocketException e) {
				e.printStackTrace();
			}
			
			Enumeration<InetAddress> addresses = current.getInetAddresses();
			while (addresses.hasMoreElements()) {
				InetAddress current_addr = addresses.nextElement();
				if (current_addr instanceof Inet4Address)
					ip = current_addr.getHostAddress();
			}
		}
		
		return ip;
	}
}
