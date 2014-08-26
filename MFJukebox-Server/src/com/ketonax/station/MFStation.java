package com.ketonax.station;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.ketonax.constants.MFNetworking;
import com.ketonax.networking.NamingService;

import edu.rutgers.winlab.jgnrs.JGNRS;
import edu.rutgers.winlab.jmfapi.GUID;
import edu.rutgers.winlab.jmfapi.JMFAPI;
import edu.rutgers.winlab.jmfapi.JMFException;

public class MFStation implements Runnable {

	private String stationName;
	private GUID stationGUID;

	private Queue<GUID> userList = null;
	private Queue<String> songQueue = null;
	private Queue<String> songsPlayedQueue = null;
	private Map<String, Integer> songLengthMap = null;
	private Map<String, GUID> songSourceMap = null;
	private Map<String, Integer> songDownloadedMap = null;
	private Map<GUID, Integer> latencyMap = null;

	private boolean isPlaying = false;
	private String currentSong = null;
	private int trackPosition = 0;
	private int playSongTimeout = 30; // Seconds
	private boolean stopRunning = false;

	/* Networking */
	JGNRS gnrs = null;
	JMFAPI serverSocket = null;

	public MFStation(String stationName, GUID creator, JMFAPI serverSocket,
			JGNRS gnrs) {

		/* Initialize variables */
		this.stationName = stationName;
		this.serverSocket = serverSocket;
		stationGUID = NamingService.assignGUID();
		userList = new ConcurrentLinkedQueue<GUID>();
		songQueue = new ConcurrentLinkedQueue<String>();
		songsPlayedQueue = new ConcurrentLinkedQueue<String>();
		songLengthMap = new ConcurrentHashMap<String, Integer>();
		songSourceMap = new ConcurrentHashMap<String, GUID>();
		songDownloadedMap = new ConcurrentHashMap<String, Integer>();
		latencyMap = new HashMap<GUID, Integer>();

		/* Setup gnrs */
		this.gnrs = gnrs;

		try {
			addUser(creator);
			log("Station is running.");
			log("User at " + creator.getGUID() + " has created station: "
					+ stationName);
		} catch (StationException e) {
			e.printStackTrace();
		}
	}

	public void run() {
		/** Station controls the flow of the playlist for its users. */

		log("Station is running.");

		stopRunning = false;
		while (stopRunning == false) {

			if (userList.isEmpty()) {
				halt();
			}

			if (isPlaying == false && !songQueue.isEmpty()) {
				currentSong = songQueue.element();

				try {
					/* Wait for 1 second, then play the next song */
					Thread.sleep(1000);

					/*
					 * Wait for notification from users that song has been
					 * downloaded or timeout has occured.
					 */
					preparePlayback();
					playSong(currentSong);
					songsPlayedQueue.add(currentSong);
				} catch (StationException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}

		log(stationName + " has stopped running.");
	}

	public void halt() {
		stopRunning = true;
	}

	public boolean hasStopped() {
		return stopRunning;
	}

	public boolean isEmpty() {
		return userList.isEmpty();
	}

	public boolean hasUser(GUID userGUID) {
		return userList.contains(userGUID);
	}

	@Override
	public String toString() {
		return stationName;
	}

	public String getName() {
		return stationName;
	}

	public GUID getGUID() {
		return stationGUID;
	}

	public void addUser(GUID userGUID) throws StationException {
		/** This function adds a user to the station user list. */

		if (!userList.contains(userGUID)) {
			userList.add(userGUID);
			log("User at \"" + userGUID + "\" has joined.");

			updateGNRS(); // Update gnrs with userList changes

			try {
				sendPlaylist(userGUID);
				sendUserList(userGUID);
			} catch (IOException e) {
				e.printStackTrace();
			}

			String notification = MFNetworking.buildUserAddedNotifier(
					stationName, userGUID);
			// sendMulticastMessage(notification);
			sendToAll(notification);

			/*
			 * If user is added while the current song is playing tell song
			 * holder to send the song to the user.
			 */
			if (isPlaying) {
				GUID songHolder = getSongSource(currentSong);
				if (songHolder != userGUID) {
					String sendSongCommand = MFNetworking
							.buildSendSongToUserCommand(stationName,
									currentSong, userGUID);
					sendToUser(sendSongCommand, songHolder);
				} else {
					try {
						playSongCatchUp(currentSong, userGUID);
					} catch (IOException e) {
						e.printStackTrace();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		} else
			throw new StationException("User is already on the list");
	}

	public void removeUser(GUID userGUID) throws StationException {
		/**
		 * This function removes a user from userList. Note that the user might
		 * still be responsible for streaming songs to other devices in the
		 * background.
		 */

		if (!userList.contains(userGUID))
			throw new StationException("User (Address: " + userGUID
					+ ") is not on station (Station Name: " + stationName
					+ ") list.");
		userList.remove(userGUID);
		latencyMap.remove(userGUID);

		updateGNRS(); // Update gnrs with userList changes

		String notification = MFNetworking.buildUserRemovedNotifier(
				stationName, userGUID);
		sendToAll(notification);
		log("User at " + userGUID + " has been removed.");
	}

	public void addSong(GUID userGUID, String songName, int songLength)
			throws StationException {
		/**
		 * This station adds a song name to the stations song queue. It also
		 * keeps a map of the song source and song length with respect to the
		 * song name.
		 */

		if (!userList.contains(userGUID))
			throw new StationException("Cannot add music. User (Address: "
					+ userGUID
					+ ") is not part of this station (Station Name: "
					+ stationName + ").");

		if (songQueue.contains(songName))
			throw new StationException("Song (Song Name: " + songName
					+ ") is already on this station (Station Name: "
					+ stationName + ") playlist.");

		songQueue.add(songName);
		songSourceMap.put(songName, userGUID);
		songLengthMap.put(songName, songLength);

		String notification = MFNetworking.buildSongAddedNotifier(stationName,
				songName);
		sendToAll(notification);

		log("\"" + songName + "\" has been added to the station by user at "
				+ userGUID.getGUID() + "." + " Songs on queue = "
				+ songQueue.size());

		/* Tell the user to send the song to other devices */
		String command = MFNetworking.buildSendSongCommand(stationName,
				songName);
		sendToUser(command, userGUID);

		log("Instructed user at address \"" + userGUID.getGUID()
				+ "\" to send \"" + currentSong + "\" to station users.");
	}

	public void removeSong(String songName) throws StationException {
		/** This function removes songGUID from all lists and maps that hold it. */

		if (!songQueue.contains(songName))
			throw new StationException("In removeSong(), " + songName
					+ " is not on songQueue");

		if (!songSourceMap.containsKey(songName))
			throw new StationException("In removeSong(), " + songName
					+ " is not on songSourceMap");

		if (!songLengthMap.containsKey(songName))
			throw new StationException("In removeSong(), " + songName
					+ " is not on songLengthMap");

		songQueue.remove(songName);
		// songSourceMap.remove(songGUID);
		// songLengthMap.remove(songGUID);

		/* Build message and send */
		String notification = MFNetworking.buildSongRemovedNotifier(
				stationName, songName);
		sendToAll(notification);
		log(songName + " has been removed from the station"
				+ " Songs on queue = " + songQueue.size());
	}

	public GUID getSongSource(String song) throws StationException {
		/** Returns the socket address of the user device holding the given song */

		if (!songSourceMap.containsKey(song))
			throw new StationException("In function getSongSource(), \"" + song
					+ "\" is not on songSourceMap.");

		return songSourceMap.get(song);
	}

	public int getSongLength(String song) throws StationException {
		/** Returns the song length in milliseconds */

		if (!songLengthMap.containsKey(song))
			throw new StationException("In function getSongSource(), \"" + song
					+ "\" is not on songLengthMap.");

		return songLengthMap.get(song);
	}

	public void latencyUpdate(GUID userID, int latency) {
		latencyMap.put(userID, latency);
		// log(Integer.toString(latency)); // TODO test
	}

	private void preparePlayback() {
		/**
		 * This method checks to see that the current song is ready to play. It
		 * checks to see that the number of times the song has been downloaded
		 * matches the userList size.
		 */

		// log("readyToPlay started. Checking for " + currentSong); // TODO test

		if (isPlaying == false) {

			int i = 0;
			while (i < playSongTimeout) {
				try {
					int downloadCount = songDownloadedMap.get(currentSong);
					// log(Integer.toString(downloadCount)); //TODO Test
					if (downloadCount == userList.size() - 1)
						break;
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				i++;
			}
			// log("readyToPlay stopped."); // TODO test
		}
	}

	public boolean songIsPlaying() {
		return isPlaying;
	}

	public void resetTrackPosition() {
		trackPosition = 0;
	}

	public void startPlaybackTimer() throws StationException,
			InterruptedException {

		if (!songLengthMap.containsKey(currentSong))
			throw new StationException(
					"No song length information for the current song \""
							+ currentSong + "\"");

		resetTrackPosition();
		final int songLength = getSongLength(currentSong);
		isPlaying = true;

		final Timer timer = new Timer();
		// log("Playback timer started."); TODO test
		timer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				++trackPosition;
				if (trackPosition == songLength) {
					timer.cancel();
					isPlaying = false;

					log("playback timer stopped");
				}
			}
		}, 0, 1);
		// removeSong(currentSong); //TODO
		// log("playback timer stopped.");
	}

	/* Networking dependent functions */

	private void updateGNRS() {
		/**
		 * This method updates the gnrs with the network addresses associated
		 * with this station
		 */

		int[] nas = new int[userList.size()];
		int i = 0;
		for (GUID user : userList) {
			nas[i] = user.getGUID();
			i++;
		}
		gnrs.add(stationGUID.getGUID(), nas);
	}

	public void userAddedNotifier(GUID addeduserGUID) {
		/**
		 * This function notifies all devices that a new user has been added. It
		 * sends the socket address of the user to the devices.
		 */

		String notification = MFNetworking.buildUserAddedNotifier(stationName,
				addeduserGUID);
		sendToAll(notification);
		log("User at " + addeduserGUID + " has been added to the station");
	}

	public void songAddedNotifier(String songName) {
		/**
		 * This function notifies all devices that a new song has been added to
		 * the queue.
		 */

		String notification = MFNetworking.buildSongAddedNotifier(stationName,
				songName);
		sendToAll(notification);
	}

	public void songRemovedNotifier(String songName) {
		/**
		 * This function notifies all devices that a new song has been removed
		 * to the queue.
		 */

		String notification = MFNetworking.buildSongRemovedNotifier(
				stationName, songName);
		sendToAll(notification);
	}

	public void sendUserList(GUID userGUID) throws IOException {
		/**
		 * This function sends each user on userList to a requesting user. This
		 * function should only be used if the userList is explicitly requested
		 * by a user. Station should default to notifying a device each time a
		 * user is added.
		 */

		String data = null;
		for (GUID user : userList) {
			data = MFNetworking.buildUserOnListResponse(stationName, user);
			sendToUser(data, userGUID);
		}

		log("Socket address of users on station user list have been sent to the user at "
				+ userGUID);
	}

	public void sendPlaylist(GUID userGUID) throws IOException,
			StationException {
		/**
		 * This function sends each song on the playlist to a user. This
		 * function should only be used if the playlist is explicitly requested
		 * by a user. Station should default to notifying a device each time a
		 * song is added.
		 */

		String data = null;
		for (String songName : songQueue) {
			data = MFNetworking.buildSongOnListResponse(stationName, songName);
			sendToUser(data, userGUID);

			/*
			 * If user is added while the current song is playing tell song
			 * holder to send the song to the user.
			 */
			if (isPlaying) {
				if (songName.equals(currentSong))
					continue;

				GUID songHolder = getSongSource(songName);
				if (songHolder != userGUID) {
					String sendSongCommand = MFNetworking
							.buildSendSongToUserCommand(stationName, songName,
									userGUID);
					sendToUser(sendSongCommand, songHolder);
				}
			}

			log("Songs on station queue have been sent to the user at "
					+ userGUID);
		}
	}

	public void notifyDownloaded(GUID userID, String songName) {

		log("User at \"" + userID.getGUID() + "\" has finished downloading "
				+ songName + ".");
		try {
			if (isPlaying == true && songName.equals(currentSong)) {
				playSongCatchUp(currentSong, userID);
			} else {
				int currentDownloadCount = songDownloadedMap.get(songName);
				songDownloadedMap.put(songName, ++currentDownloadCount);
			}
		} catch (StationException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public void sendToUser(String message, GUID userGUID) {
		/** Sends a message to a specified device. */

		byte[] dataToSend = message.getBytes();
		try {
			serverSocket.jmfsend(dataToSend, dataToSend.length, userGUID);
		} catch (JMFException e) {
			e.printStackTrace();
		}
	}

	public void sendToAll(String message) {
		/** This function sends message to all devices. */

		for (GUID user : userList)
			sendToUser(message, user);
	}

	/* Private Methods */

	private void playSong(String songName) throws StationException,
			IOException, InterruptedException {
		/* Establish a socket connection and play song with GUID songGUID */

		// Check to see if song is on songSourceMap
		if (!songSourceMap.containsKey(songName))
			throw new StationException("In function playSong(), \"" + songName
					+ "\" is not on songSourceMap.");

		notifyCurrentlyPlaying();
		/* Display station queue status */
		log("Instructed station users " + "to play \"" + songName
				+ "\". Song length = " + songLengthMap.get(songName)
				+ "ms. Songs played = " + songsPlayedQueue.size()
				+ ". Songs on queue = " + songQueue.size());

		startPlaybackTimer();
		Thread.sleep(getSongLength(currentSong));
		removeSong(currentSong);
	}

	private void notifyCurrentlyPlaying() {
		GUID songSource = songSourceMap.get(currentSong);

		for (GUID user : userList) {

			int userLatency = 0;
			if (latencyMap.containsKey(user))
				userLatency = latencyMap.get(user);
			int startPosition = 0;

			/* Send notification to all devices of current song playing */
			String notification = MFNetworking.buildCurrentlyPlayingNotifier(
					stationName, currentSong, songSource,
					Integer.toString(startPosition + userLatency));
			sendToUser(notification, user);
		}
	}

	@SuppressWarnings("unused")
	private void playSongCatchUp(String songName, GUID userID)
			throws StationException, IOException, InterruptedException {
		/**
		 * Establish a socket connection and tells a specific user to play song
		 * at the current trackPosition
		 */

		// Check to see if song is on songSourceMap
		if (!songSourceMap.containsKey(songName))
			throw new StationException("In function playSongCatchUp(), \""
					+ songName + "\" is not on songSourceMap.");

		int songLength = getSongLength(songName);
		GUID songSource = getSongSource(songName);

		/* Send notification to a devices about current song playing */
		int userLatency = 0;
		if (latencyMap.containsKey(userID))
			userLatency = latencyMap.get(userID);
		else
			log("Latency map doesn't contain address: " + userID.getGUID());

		int startPosition = trackPosition;
		String notification = MFNetworking.buildCurrentlyPlayingNotifier(
				stationName, songName, songSource,
				Integer.toString(startPosition + userLatency));
		// sendMulticastMessage(notification);
		sendToUser(notification, userID);

		/* Display station queue status */
		log("Instructed user at address \"" + userID.getGUID()
				+ "\" to play \"" + songName + "\". Start position = "
				+ startPosition + ". Latency factor = " + userLatency
				+ ". Song length = " + songLengthMap.get(songName)
				+ "ms. Songs played = " + songsPlayedQueue.size()
				+ ". Songs on queue = " + songQueue.size());
	}

	private void log(String message) {
		/** This function displays log messages on the console. */

		String logMessage = "[" + stationName + "] station log: " + message;
		System.out.println(logMessage);
	}
}
