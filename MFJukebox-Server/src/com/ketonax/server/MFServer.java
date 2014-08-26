package com.ketonax.server;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.ketonax.constants.MFNetworking;
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
	private static Map<GUID, Boolean> pingResponseMap = null;
	private static Map<GUID, Integer> latencyMap = null;
	private static Map<GUID, Integer> pingCountMap = null;
	private static Map<GUID, ArrayList<Integer>> latencySampleSizeMap = null;
	private static int totalPingSampleSize = 100;
	private static int latencyTime = 0; // Milliseconds
	private static boolean pingStarted = false;

	public static void main(String[] args) {
		System.out.println("Jukebox server has started.");

		/* Initialize variables */
		SERVER_GUID = MFNetworking.SERVER_GUID;
		stationList = new LinkedList<MFStation>();
		stationMap = new HashMap<String, MFStation>();
		currentStationMap = new HashMap<GUID, MFStation>();
		pingResponseMap = new ConcurrentHashMap<GUID, Boolean>();
		latencyMap = new ConcurrentHashMap<GUID, Integer>();
		pingCountMap = new ConcurrentHashMap<GUID, Integer>();
		latencySampleSizeMap = new ConcurrentHashMap<GUID, ArrayList<Integer>>();
		allUsers = new ArrayList<GUID>();
		serverSocket = new JMFAPI();
		flag = new MFFlag();
		startServer();
	}

	public static void startServer() {
		try {
			flag.setValue(MFFlag.MF_MHOME);
			serverSocket.jmfopen("basic", flag, SERVER_GUID);
			setupGNRS();

			while (true) {

				/* Receive messages from devices */
				byte[] receiveData = new byte[MFNetworking.DATA_LIMIT_SIZE];
				int recvLen = 0;
				String userMessage = null;
				GUID userGUID = new GUID();

				if ((recvLen = serverSocket.jmfrecv_blk(userGUID, receiveData,
						receiveData.length)) > 0) {
					userMessage = new String(receiveData, 0, recvLen,
							Charset.defaultCharset());
				}

				// String userMessage = new String(receiveData).trim();
				if (userMessage == null) {
					log("Error receiving message from device; Moving on to other things...");
					continue;
				}
				log("Message recvd from device (GUID=" + userGUID.getGUID()
						+ "):\n'" + userMessage + "'");

				/* Parse received messages */
				if (userMessage.contains(",")) {
					String messageArray[] = userMessage.split(",");

					if (messageArray[0].equals(MFNetworking.CREATE_STATION_CMD)) {
						String stationName = messageArray[1];
						createStation(stationName, userGUID);
					} else if (messageArray[0]
							.equals(MFNetworking.ADD_SONG_CMD)) {
						String stationName = messageArray[1];
						String songName = messageArray[2];
						int songLength = Integer.parseInt(messageArray[3]);

						addSongToStation(userGUID, stationName, songName,
								songLength);
					} else if (messageArray[0]
							.equals(MFNetworking.LEAVE_STATION_CMD)) {
						String stationName = messageArray[1];
						leaveStation(userGUID, stationName);
					} else if (messageArray[0]
							.equals(MFNetworking.JOIN_STATION_CMD)) {
						String stationName = messageArray[1];
						joinStation(userGUID, stationName);
					} else if (messageArray[0]
							.equals(MFNetworking.GET_PLAYLIST_CMD)) {

						String stationName = messageArray[1];
						sendPlaylist(userGUID, stationName);
					}else if (messageArray[0]
							.equals(MFNetworking.SONG_DOWNLOADED_NOTIFIER)) {

						String stationName = messageArray[1];
						String songName = messageArray[2];
						songDownloadedNotifier(userGUID, stationName, songName);

					}

				} else if (userMessage.equals(MFNetworking.PING_RESPONSE)) {
					pingResponseMap.put(userGUID, true);
				}else if (userMessage
						.equals(MFNetworking.EXIT_JUKEBOX_NOTIFIER)) {

					removeUserFromServer(userGUID);
				} else if (userMessage
						.equals(MFNetworking.STATION_LIST_REQUEST_CMD)) {

					addUserToServer(userGUID);
					sendStationList(userGUID);
					log("Station list sent to " + userGUID);
				} else {
					System.err.println("Unrecognized message \"" + userMessage
							+ "\" passed to the server from" + userGUID);
				}

				/* Check to see if stations are running */
				Iterator<MFStation> it = stationList.iterator();
				while (it.hasNext()) {
					MFStation s = it.next();
					if (s.hasStopped()) {
						// Send updated station list to all users
						sendStationKilledNotifier(s);
						stationMap.remove(s.getName());
						it.remove();
						if (stationList.size() == 0)
							log("There are no stations available.");
					}
				}
			}
		} catch (JMFException e) {
			e.printStackTrace();
		} catch (StationException e) {
			e.printStackTrace();
		} catch (ServerException e) {
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

	private static void addUserToServer(GUID userID) {

		/* Add user to ALL_USERS list */
		if (!allUsers.contains(userID))
			allUsers.add(userID);

		if (!latencyMap.containsKey(userID))
			latencyMap.put(userID, 0);

		if (!pingCountMap.containsKey(userID))
			pingCountMap.put(userID, 0);

		if (!pingResponseMap.containsKey(userID))
			pingResponseMap.put(userID, false);

		if (!latencySampleSizeMap.containsKey(userID)) {
			ArrayList<Integer> pingValuesList = new ArrayList<Integer>(
					totalPingSampleSize);
			initPingArray(pingValuesList);
			latencySampleSizeMap.put(userID, pingValuesList);
		}

		/* Update the gnrs with the GUIDs of all users */
		updateGNRS();

		/* Start pinging devices. */
		if (pingStarted == false)
			startPinging();
	}

	private static void initPingArray(ArrayList<Integer> pingValuesList) {
		for (int i = 0; i < totalPingSampleSize; i++)
			pingValuesList.add(i, 0);
	}

	private static void removeUserFromServer(GUID userID)
			throws StationException {

		if (allUsers.contains(userID))
			allUsers.remove(userID);

		if (pingCountMap.containsKey(userID))
			pingCountMap.remove(userID);

		if (pingResponseMap.containsKey(userID))
			pingResponseMap.remove(userID);

		if (latencyMap.containsKey(userID))
			latencyMap.remove(userID);

		if (latencySampleSizeMap.containsKey(userID))
			latencySampleSizeMap.remove(userID);

		if (currentStationMap.containsKey(userID)) {
			MFStation targetStation = currentStationMap.get(userID);

			if (targetStation.hasUser(userID))
				/* Remove user from their current station */
				targetStation.removeUser(userID);

			currentStationMap.remove(userID);
		}

		/* Update the gnrs with the GUIDs of all users */
		updateGNRS();
		
		log("User at " + userID + " has disconnected.");
	}

	private static void startPinging() {

		log("Pinging started.");
		pingStarted = true;

		Thread pingerThread = new Thread() {
			Iterator<GUID> it;
			GUID user;

			public void run() {
				while (true) {
					it = allUsers.iterator();
					while (it.hasNext()) {
						String pingMessage = MFNetworking.buildPingMessage();
						user = (GUID) it.next();
						if (allUsers.contains(user))
							sendToUser(pingMessage, user);
						startLatencyTimer(user);

						try {
							sleep(1000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			}
		};

		pingerThread.start();
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

		/* Add user if the user is not on the allUsers list */
		addUserToServer(creatorID);

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

		if (!stationMap.containsKey(stationName)) {
			MFStation station = new MFStation(stationName, creatorID,
					serverSocket, gnrs);
			stationList.add(station);
			stationMap.put(station.getName(), station);
			currentStationMap.put(creatorID, station);

			/* Start new station thread and notify user of station creation */
			new Thread(station).start();

			sendStationAddedNotifier(station);
		} else
			throw new ServerException(stationName
					+ " station already on stationList.");
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
			addUserToServer(userID);

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

	public static void sendStationList(GUID userID) {
		/**
		 * Sends the a list of the station names to user.
		 * 
		 * @param userID
		 *            : The GUID of the user that will receive the stationList.
		 */

		for (MFStation s : stationList) {
			String message = MFNetworking.buildStationListRequestResponse(s
					.getName(), s.getGUID());
			try {
				serverSocket.jmfsend(message.getBytes(),
						message.getBytes().length, userID);
			} catch (JMFException e) {
				e.printStackTrace();
			}
		}
	}

	private static void sendStationKilledNotifier(MFStation station) {
		/** Sends a notifier that a station has been killed */

		String message = MFNetworking.buildStationKilledNotifier(station
				.getName(), station.getGUID());
		sendToAll(message);
		log("Notified all users that " + station.getName() + " has terminated.");
	}

	private static void sendStationAddedNotifier(MFStation station) {
		/**
		 * Sends a notifier to all devices that a station has been added. It
		 * includes the station name.
		 */

		String message = MFNetworking.buildStationAddedNotifier(
				station.getName(), station.getGUID());
		sendToAll(message);
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
			} catch (StationException e) {
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

	public static void songDownloadedNotifier(GUID userID, String stationName,
			String songName) throws ServerException {
		/**
		 * This method increments a variable in the station that checks whether
		 * the a song has been downloaded.
		 */

		MFStation targetStation = stationMap.get(stationName);
		if (targetStation != null) {
			int latency = latencyMap.get(userID);
			targetStation.latencyUpdate(userID, latency);
			targetStation.notifyDownloaded(userID, songName);
		} else {
			throw new ServerException("User at " + userID
					+ " sent notification to nonexistent station \""
					+ stationName + "\"");
		}
	}

	private static void startLatencyTimer(final GUID userID) {

		for (latencyTime = 0; latencyTime < 10000; latencyTime++) {
			if (pingResponseMap.containsKey(userID)) {
				if (pingResponseMap.get(userID) == true) {
					int pingCount = pingCountMap.get(userID);
					pingCount = pingCount % totalPingSampleSize;

					pingCount++;
					pingCountMap.put(userID, pingCount);

					ArrayList<Integer> pingValuesList = latencySampleSizeMap
							.get(userID);
					pingValuesList.remove(pingCount - 1); // Remove old
					pingValuesList.add(pingCount - 1, latencyTime);

					// log(Integer.toString(pingValuesList.get(pingCount - 1)));
					// //TODO test

					latencySampleSizeMap.put(userID, pingValuesList);
					int averageLatency = getAverageLatency(userID);
					latencyMap.put(userID, averageLatency);
					break;
				}
			}

			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		// log(Integer.toString(latencyMap.get(userID))); // TODO
		// Test
		/* Reset the ping response boolean for this user */
		pingResponseMap.put(userID, false);
	}

	private static int getAverageLatency(GUID userID) {

		int averageLatency = 0;
		ArrayList<Integer> pingValuesList = latencySampleSizeMap.get(userID);
		int pingSize = pingCountMap.get(userID);
		for (int i = 0; i < pingSize; i++)
			averageLatency += pingValuesList.get(i);

		averageLatency /= pingSize;
		// log("Average latency for user at \"" + userID + "\" is: "
		// + Integer.toString(averageLatency)); // TODO test
		return averageLatency;
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

	private static void sendToUser(String message, GUID userID) {
		/** Sends a message to a specified device. */

		try {
			serverSocket.jmfsend(message.getBytes(), message.length(), userID);
		} catch (JMFException e) {
			e.printStackTrace();
		}
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