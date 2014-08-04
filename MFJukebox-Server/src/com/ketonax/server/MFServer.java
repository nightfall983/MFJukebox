package com.ketonax.server;

import java.nio.charset.Charset;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.ketonax.constants.MFNetworking;
import com.ketonax.networking.MessageBuilder;
import com.ketonax.networking.ServerException;
import com.ketonax.station.MFStation;
import com.ketonax.station.StationException;

import edu.rutgers.winlab.jgnrs.JGNRS;
import edu.rutgers.winlab.jmfapi.GUID;
import edu.rutgers.winlab.jmfapi.JMFAPI;
import edu.rutgers.winlab.jmfapi.JMFException;
import edu.rutgers.winlab.jmfapi.MFFlag;

public class MFServer {

	/* Used to store a mapping of station GUID and the station object. */
	static Map<String, MFStation> stationMap = null;

	/* Used to store a mapping of userGUID and the user's current station. */
	static Map<GUID, MFStation> currentStationMap = null;

	static List<MFStation> stationList = null;
	static ArrayList<GUID> allUsers = null;

	/* Networking */
	static GUID SERVER_GUID;
	static JGNRS gnrs = null;
	static JMFAPI serverSocket = null;
	static MFFlag flag = null;

	public static void main(String[] args) {
		System.out.println("Jukebox server has started.");

		/* Initialize variables */
		SERVER_GUID = MFNetworking.SERVER_GUID;
		stationList = new LinkedList<MFStation>();
		stationMap = new HashMap<String, MFStation>();
		currentStationMap = new HashMap<GUID, MFStation>();
		allUsers = new ArrayList<GUID>();
		serverSocket = new JMFAPI();
		flag = new MFFlag();

		try {
			flag.setValue(MFFlag.MF_MHOME);
			serverSocket.jmfopen("basic", flag, SERVER_GUID);
			setupGNRS();

			while (true) {

				/* Check to see if stations are running */
				Iterator<MFStation> it = stationList.iterator();
				while (it.hasNext()) {
					MFStation s = it.next();
					if (s.hasStopped()) {
						String[] elements = {
								MFNetworking.STATION_KILLED_NOTIFIER,
								s.getName() };
						String message = MessageBuilder.buildMessage(elements,
								MFNetworking.SEPERATOR);
						//sendToAll(message);
						it.remove();
						log("Notified all users that " + s.getName()
								+ " has terminated.");
					}

				}
				if (stationList.size() == 0)
					System.out.println("There are no stations available.");

					/* Receive messages from devices */
					byte[] receiveData = new byte[MFNetworking.DATA_LIMIT_SIZE];
					int recvLen = 0;
					String userMessage = null;
					GUID userGUID = new GUID();
					if((recvLen = serverSocket.jmfrecv_blk(userGUID, receiveData, receiveData.length)) > 0){
						userMessage = new String(receiveData, 0, recvLen, Charset.defaultCharset());
					}

					//String userMessage = new String(receiveData).trim();
					if(userMessage == null){
						System.out.println("Error receiving message from device; Moving on to other things...");
						continue;
					}
					System.out.println("Message recvd from device (GUID=" + userGUID.getGUID() + "):\n'" + userMessage + "'"); 
					/* Parse received messages */
					if (userMessage.contains(",")) {
						String messageArray[] = userMessage.split(",");

						/**
						 * The sender GUID should always be the second index (1)
						 * in messageArray
						 */
						//GUID userGUID = new GUID(Integer.parseInt(messageArray[1]));

						if (messageArray[0]
								.equals(MFNetworking.CREATE_STATION_CMD)) {
							String stationName = messageArray[2];

							try {
								createStation(stationName, userGUID);
							} catch (ServerException e) {
								e.printStackTrace();
							}
						} else if (messageArray[0]
								.equals(MFNetworking.ADD_SONG_CMD)) {
							String stationName = messageArray[2];
							String songName = messageArray[3];
							int songLength = Integer.parseInt(messageArray[4]);

							try {
								addSongToStation(userGUID, stationName,
										songName, songLength);
							} catch (ServerException e) {
								e.printStackTrace();
							}
						} else if (messageArray[0]
								.equals(MFNetworking.LEAVE_STATION_CMD)) {
							String stationName = messageArray[2];
							leaveStation(userGUID, stationName);
						} else if (messageArray[0]
								.equals(MFNetworking.JOIN_STATION_CMD)) {
							String stationName = messageArray[2];
							try {
								joinStation(userGUID, stationName);
							} catch (ServerException e) {
								e.printStackTrace();
							}
						} else if (messageArray[0]
								.equals(MFNetworking.GET_PLAYLIST_CMD)) {

							String stationName = messageArray[2];
							try {
								sendPlaylist(userGUID, stationName);
							} catch (ServerException e) {
								e.printStackTrace();
							}
						}
						
					} else if (userMessage.equals(MFNetworking.EXIT_JUKEBOX_NOTIFIER)) {

						if (allUsers.contains(userGUID))
							allUsers.remove(userGUID);
						if (currentStationMap.containsKey(userGUID))
							currentStationMap.remove(userGUID);
					} else if (userMessage
							.equals(MFNetworking.STATION_LIST_REQUEST_CMD)) {

						if (!allUsers.contains(userGUID))
							allUsers.add(userGUID);
						sendStationList(userGUID);
						log("Station list sent to " + userGUID);
					} else {
						System.err
								.println("Unrecognized message \""
										+ userMessage
										+ "\" passed to the server from"
										+ userGUID);
					}
			}
		} catch (JMFException e) {
			e.printStackTrace();
		} finally {
			try {
				if (serverSocket.isOpen())
					serverSocket.jmfclose();
			} catch (JMFException e) {
				e.printStackTrace();
			}
		}
	}

	public static void createStation(String stationName, GUID creatorID)
			throws ServerException {
		/**
		 * This method creates a new station with the provided stationName.
		 * 
		 * @param stationName
		 *            : The name of the station to be created.
		 * @param creatorID
		 *            : The GUID of the station creator.
		 */

		/*
		 * Check if the creator is currently in a different station. If so,
		 * remove creator from that station.
		 */
		if (currentStationMap.containsKey(creatorID)) {
			MFStation oldStation = currentStationMap.get(creatorID);

			try {
				if (oldStation.hasUser(creatorID))
					oldStation.removeUser(creatorID);
			} catch (StationException e) {
				System.err.println(e.getMessage());
			}

			/* Remove map to the old station */
			currentStationMap.remove(creatorID);
		}

		/* Add user to ALL_USERS list */
		if (!allUsers.contains(creatorID))
			allUsers.add(creatorID);

		if (!stationMap.containsKey(stationName)) {
			MFStation station = new MFStation(stationName, creatorID,
					serverSocket, gnrs);
			stationList.add(station);
			stationMap.put(station.getName(), station);
			currentStationMap.put(creatorID, station);

			/* Start new station thread and notify user of station creation */
			new Thread(station).start();

			/* Update the gnrs with the GUIDs of all users */
			//updateGNRS();

			/* Send notification to all users that a new station has been added */
			String[] elements = { MFNetworking.STATION_ADDED_NOTIFIER,
					stationName, Integer.toString(station.getGUID().getGUID()) };
			String notification = MessageBuilder.buildMessage(elements,
					MFNetworking.SEPERATOR);
			//sendToAll(notification);
		} else
			throw new ServerException(stationName
					+ " station already on stationList.");
	}

	public static void sendStationList(GUID userID) {
		/**
		 * Sends the a list of the station names to user.
		 * 
		 * @param userID
		 *            : The GUID of the user that will receive the stationList.
		 */

		for (MFStation s : stationList) {
			String[] elements = { MFNetworking.STATION_LIST_REQUEST_RESPONSE,
					s.getName(), Integer.toString(s.getGUID().getGUID()) };
			String message = MessageBuilder.buildMessage(elements,
					MFNetworking.SEPERATOR);
			try {
				serverSocket.jmfsend(message.getBytes(),
						message.getBytes().length, userID);
			} catch (JMFException e) {
				e.printStackTrace();
			}
		}
	}

	public static void joinStation(GUID userID, String stationName)
			throws ServerException {
		/**
		 * This method adds a user to the station with the name: stationName
		 * 
		 * @param userID
		 *            : The GUID of the user joining the station.
		 * @param stationName
		 *            : The name of the station the user is joining.
		 */

		if (!stationMap.containsKey(stationName)) {
			throw new ServerException("User at " + userID
					+ " attempted to join nonexistent station: " + stationName);
		} else {
			/* Add user if the user is not on the allUsers list */
			if (!allUsers.contains(userID))
				allUsers.add(userID);

			MFStation targetStation = stationMap.get(stationName);

			/* Check for user's old station */
			if (currentStationMap.containsKey(userID)) {
				MFStation oldStation = currentStationMap.get(userID);
				try {
					oldStation.removeUser(userID);

					// Remove old pairing
					currentStationMap.remove(userID);
				} catch (StationException e) {
					e.printStackTrace();
				}
			}
			currentStationMap.put(userID, targetStation);
			try {
				targetStation.addUser(userID);
			} catch (StationException e) {
				System.err.println(e.getMessage());
			}
		}
	}

	public static void sendPlaylist(GUID userID, String stationName)
			throws ServerException {
		/**
		 * This method sends each song on a station's playlist to the requesting
		 * user.
		 * 
		 * @param userID
		 *            : The GUID of the requesting user.
		 * @param stationName
		 *            : The name of the station of which the user is requesting
		 *            the playlist.
		 */

		if (stationMap.containsKey(stationName)) {
			MFStation targetStation = stationMap.get(stationName);
			try {
				targetStation.sendPlaylist(userID);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			throw new ServerException(
					"Requesting playlist from nonexistent station.");
		}
	}

	public static void leaveStation(GUID userID, String stationName) {
		/**
		 * This method removes a user from a station.
		 * 
		 * @param userID
		 *            : The GUID of the user leaving the station.
		 * @param stationName
		 *            : The name of the station the user is leaving.
		 */

		if (stationMap.containsKey(stationName)) {
			MFStation targetStation = stationMap.get(stationName);

			if (currentStationMap.containsKey(userID))
				currentStationMap.remove(userID);

			try {
				if (targetStation.hasUser(userID))
					targetStation.removeUser(userID);
			} catch (StationException e) {
				System.err.println(e.getMessage());
			}
		}
	}

	public static void addSongToStation(GUID userID, String stationName,
			String songName, int songLength) throws ServerException {
		/**
		 * This method adds a song to a station.
		 * 
		 * @param userID
		 *            : The GUID of the user adding a song to the station.
		 * @param stationName
		 *            : The name of the station the user is adding the song to.
		 */

		/*
		 * Check whether a station with the name stationName exists
		 */
		if (stationMap.containsKey(stationName)) {
			MFStation targetStation = stationMap.get(stationName);
			try {
				targetStation.addSong(userID, songName, songLength);
			} catch (StationException e) {
				e.printStackTrace();
			}
		} else {
			throw new ServerException("User at " + userID
					+ " attempted to add a song to nonexistent station: "
					+ stationName);
		}
	}

	/* Private methods */
	private static void setupGNRS() {
		gnrs = new JGNRS();
		gnrs.setGNRS(
				MFNetworking.GNRS_IP_STRING + ":" + MFNetworking.GNRS_PORT,
				MFNetworking.getLocalIP() + ":" + MFNetworking.GNRS_LISTEN_PORT);
	}

	private static void updateGNRS() {
		int[] nas = new int[allUsers.size()];
		int i = 0;

		for (GUID user : allUsers) {
			nas[i] = user.getGUID();
			i++;
		}

		gnrs.add(MFNetworking.ALL_USERS.getGUID(), nas);
	}

	private static void sendToAll(String message) {
		/** Sends a message to all users. */

		try {
			serverSocket.jmfsend(message.getBytes(), message.length(),
					MFNetworking.ALL_USERS);
		} catch (JMFException e) {
			e.printStackTrace();
		}
	}

	private static void log(String message) {
		/** This function displays log messages on the console. */

		String logMessage = "Server log: " + message;
		System.out.println(logMessage);
	}
}