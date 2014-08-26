package com.ketonax.constants;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Enumeration;

import com.ketonax.networking.MessageBuilder;

import edu.rutgers.winlab.jmfapi.GUID;

public final class MFNetworking {
	/* Commands To Devices */
	public static final String SEND_SONG_CMD = "/send_song";
	public static final String SEND_SONG_TO_USER_CMD = "/send_song_to_user";

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
	public static final String SONG_DOWNLOADED_NOTIFIER = "/song_downloaded";

	/* Response to devices */
	public static final String SONG_ON_LIST_RESPONSE = "/song_on_list";
	public static final String USER_ON_LIST_RESPONSE = "/user_on_list";

	/* Ping Communication */
	public static final String PING = "/ping";
	public static final String PING_RESPONSE = "/ping_response";

	/* Constants */
	public static final int DATA_LIMIT_SIZE = 1024;

	/* Separator string */
	public static final String SEPARATOR = ",";

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

	public static String getLocalIP() {

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

	public static String getIPString(SocketAddress socketAddress) {
		String ipString = null;

		/* Parse userSocketAddress */
		if (socketAddress != null) {
			String address[] = socketAddress.toString().split(":");
			ipString = address[0];

			/* Check to see if userIP contains '/' and remove it */
			if (ipString.startsWith("/"))
				ipString = ipString.replaceFirst("/", "");
		}
		return ipString;
	}

	public static int getPort(SocketAddress socketAddress) {

		String address[] = socketAddress.toString().split(":");
		int userPort = Integer.parseInt(address[1]);
		return userPort;
	}

	public static String buildSendSongCommand(String stationName,
			String songName) {

		String[] elements = { SEND_SONG_CMD, stationName, songName };
		MessageBuilder.buildMessage(elements, SEPARATOR);
		String message = MessageBuilder.buildMessage(elements, SEPARATOR);
		return message;
	}

	public static String buildSendSongToUserCommand(String stationName,
			String songName, GUID destinationUser) {

		String[] elements = { SEND_SONG_TO_USER_CMD, stationName, songName,
				Integer.toString(destinationUser.getGUID()) };
		String message = MessageBuilder.buildMessage(elements, SEPARATOR);
		return message;
	}

	public static String buildJoinStationCommand(String stationName) {

		String[] elements = { JOIN_STATION_CMD, stationName };
		String message = MessageBuilder.buildMessage(elements, SEPARATOR);
		return message;
	}

	public static String buildLeaveStationCommand(String stationName) {

		String[] elements = { LEAVE_STATION_CMD, stationName };
		String message = MessageBuilder.buildMessage(elements, SEPARATOR);
		return message;
	}

	public static String buildAddSongCommand(String stationName,
			String songName, String songLength) {

		String[] elements = { ADD_SONG_CMD, stationName, songName, songLength };
		String message = MessageBuilder.buildMessage(elements, SEPARATOR);
		return message;
	}

	public static String buildGetPlaylistCommand(String stationName) {

		String[] elements = { GET_PLAYLIST_CMD, stationName };
		String message = MessageBuilder.buildMessage(elements, SEPARATOR);
		return message;
	}

	public static String buildCreateStationCommand(String stationName) {

		String[] elements = { CREATE_STATION_CMD, stationName };
		String message = MessageBuilder.buildMessage(elements, SEPARATOR);
		return message;
	}

	public static String buildStationListRequestCommand() {

		String[] elements = { STATION_LIST_REQUEST_CMD };
		String message = MessageBuilder.buildMessage(elements, SEPARATOR);
		return message;
	}

	public static String buildStationListRequestResponse(String stationName,
			GUID stationID) {

		String[] elements = { STATION_LIST_REQUEST_RESPONSE, stationName,
				Integer.toString(stationID.getGUID()) };
		String message = MessageBuilder.buildMessage(elements, SEPARATOR);
		return message;
	}

	public static String buildStationKilledNotifier(String stationName,
			GUID stationID) {

		String[] elements = { STATION_KILLED_NOTIFIER, stationName,
				Integer.toString(stationID.getGUID()) };
		String message = MessageBuilder.buildMessage(elements, SEPARATOR);
		return message;
	}

	public static String buildStationAddedNotifier(String stationName,
			GUID stationGUID) {

		String[] elements = { STATION_ADDED_NOTIFIER, stationName,
				Integer.toString(stationGUID.getGUID()) };
		String message = MessageBuilder.buildMessage(elements, SEPARATOR);
		return message;
	}

	public static String buildSongAddedNotifier(String stationName,
			String songName) {

		String[] elements = { SONG_ADDED_NOTIFIER, stationName, songName };
		String message = MessageBuilder.buildMessage(elements, SEPARATOR);
		return message;
	}

	public static String buildSongRemovedNotifier(String stationName,
			String songName) {

		String[] elements = { SONG_REMOVED_NOTIFIER, stationName, songName };
		String message = MessageBuilder.buildMessage(elements, SEPARATOR);
		return message;
	}

	public static String buildUserAddedNotifier(String stationName, GUID userID) {

		String[] elements = { USER_ADDED_NOTIFIER, stationName,
				Integer.toString(userID.getGUID()) };
		String message = MessageBuilder.buildMessage(elements, SEPARATOR);
		return message;
	}

	public static String buildUserRemovedNotifier(String stationName,
			GUID userID) {

		String[] elements = { USER_REMOVED_NOTIFIER, stationName,
				Integer.toString(userID.getGUID()) };
		String message = MessageBuilder.buildMessage(elements, SEPARATOR);
		return message;
	}

	public static String buildCurrentlyPlayingNotifier(String stationName,
			String songName, GUID holderID, String songPosition) {

		String[] elements = { CURRENTLY_PLAYING_NOTIFIER, stationName,
				songName, Integer.toString(holderID.getGUID()), songPosition };
		String message = MessageBuilder.buildMessage(elements, SEPARATOR);
		return message;
	}

	public static String buildExitJukeboxNotifier() {

		String[] elements = { EXIT_JUKEBOX_NOTIFIER };
		String message = MessageBuilder.buildMessage(elements, SEPARATOR);
		return message;
	}

	public static String buildSongDownloadedNotifier(String stationName,
			GUID userID, String songName) {

		String[] elements = { SONG_DOWNLOADED_NOTIFIER, stationName,
				Integer.toString(userID.getGUID()), songName };
		String message = MessageBuilder.buildMessage(elements, SEPARATOR);
		return message;
	}

	public static String buildSongOnListResponse(String stationName,
			String songName) {

		String[] elements = { SONG_ON_LIST_RESPONSE, stationName, songName };
		String message = MessageBuilder.buildMessage(elements, SEPARATOR);
		return message;
	}

	public static String buildUserOnListResponse(String stationName, GUID userID) {

		String[] elements = { USER_ON_LIST_RESPONSE, stationName,
				Integer.toString(userID.getGUID()) };
		String message = MessageBuilder.buildMessage(elements, SEPARATOR);
		return message;
	}

	public static String buildPingMessage() {
		return PING;
	}

	public static String buildPingResponse(String stationName, GUID userID) {

		String[] elements = { PING_RESPONSE, stationName,
				Integer.toString(userID.getGUID()) };

		String message = MessageBuilder.buildMessage(elements, SEPARATOR);
		return message;
	}
}
