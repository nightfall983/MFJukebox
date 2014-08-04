package com.ketonax.station;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.ketonax.constants.MFNetworking;
import com.ketonax.networking.MessageBuilder;
import com.ketonax.networking.NamingService;

import edu.rutgers.winlab.jgnrs.JGNRS;
import edu.rutgers.winlab.jmfapi.GUID;
import edu.rutgers.winlab.jmfapi.JMFAPI;
import edu.rutgers.winlab.jmfapi.JMFException;

public class MFStation implements Runnable {

	private String stationName;
	private GUID stationGUID;

	private Queue<GUID> userList = null;
	private Queue<GUID> songQueue = null;
	private Queue<GUID> songsPlayedQueue = null;
	private Map<GUID, Integer> songLengthMap = null;
	private Map<GUID, GUID> songSourceMap = null;
	private Map<String, GUID> songIDMap = null;

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
		songQueue = new ConcurrentLinkedQueue<GUID>();
		songsPlayedQueue = new ConcurrentLinkedQueue<GUID>();
		songLengthMap = new ConcurrentHashMap<GUID, Integer>();
		songSourceMap = new ConcurrentHashMap<GUID, GUID>();
		songIDMap = new ConcurrentHashMap<String, GUID>();

		/* Setup gnrs */
		this.gnrs = gnrs;
	}

	public void run() {
		/** Station controls the flow of the playlist for its users. */

		log("Station is running.");

		stopRunning = false;
		while (stopRunning == false) {

			if (userList.isEmpty()) {
				halt();
			}

			Iterator<GUID> it = songQueue.iterator();

			while (it.hasNext() && !userList.isEmpty()) {
				GUID song = it.next();
				try {
					Thread.sleep(1000); /* Not sure why, but fixes songLengthMap */
					playSong(song);
				} catch (StationException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				songsPlayedQueue.add(song);
				songRemovedNotifier(song);
				log("The song \"" + song
						+ "\" has been removed from station queue");
				it.remove();
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
			} catch (IOException e) {
				e.printStackTrace();
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

		updateGNRS(); // Update gnrs with userList changes

		String[] elements = { MFNetworking.USER_REMOVED_NOTIFIER, stationName,
				Integer.toString(userGUID.getGUID()) };
		String notification = MessageBuilder.buildMessage(elements,
				MFNetworking.SEPERATOR);
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

		if (songIDMap.containsKey(songName))
			throw new StationException("Song (Song Name: " + songName
					+ ") is already on this station (Station Name: "
					+ stationName + ") playlist.");
		
		GUID songGUID = NamingService.assignGUID();

		songQueue.add(songGUID);
		songSourceMap.put(songGUID, userGUID);
		songIDMap.put(songName, songGUID);
		songLengthMap.put(songGUID, songLength);

		String notification = MFNetworking.SONG_ADDED_NOTIFIER + ","
				+ stationName + "," + songGUID;
		sendToAll(notification);
		log(songGUID + " has been added to the station.");
	}

	public void removeSong(GUID songGUID) throws StationException {
		/** This function removes songGUID from all lists and maps that hold it. */

		if (!songQueue.contains(songGUID))
			throw new StationException("In removeSong(), " + songGUID
					+ " is not on songQueue");

		if (!songSourceMap.containsKey(songGUID))
			throw new StationException("In removeSong(), " + songGUID
					+ " is not on songSourceMap");

		if (!songLengthMap.containsKey(songGUID))
			throw new StationException("In removeSong(), " + songGUID
					+ " is not on songLengthMap");

		songQueue.remove(songGUID);
		songSourceMap.remove(songGUID);
		songLengthMap.remove(songGUID);

		/* Build message and send */
		String[] elements = { MFNetworking.SONG_REMOVED_NOTIFIER, stationName,
				Integer.toString(songGUID.getGUID()) };
		String notification = MessageBuilder.buildMessage(elements,
				MFNetworking.SEPERATOR);
		sendToAll(notification);
		log(songGUID + " has been removed from the station");
	}

	public GUID getSongSource(GUID song) throws StationException {
		/** Returns the socket address of the user device holding the given song */

		if (!songSourceMap.containsKey(song))
			throw new StationException("In function getSongSource(), \"" + song
					+ "\" is not on songSourceMap.");

		return songSourceMap.get(song);
	}

	public int getSongLength(GUID song) throws StationException {
		/** Returns the song length in milliseconds */

		if (!songLengthMap.containsKey(song))
			throw new StationException("In function getSongSource(), \"" + song
					+ "\" is not on songLengthMap.");

		return songLengthMap.get(song);
	}

	/* Networking dependent functions */
	public void userAddedNotifier(GUID addeduserGUID) {
		/**
		 * This function notifies all devices that a new user has been added. It
		 * sends the socket address of the user to the devices.
		 */

		String[] elements = { MFNetworking.USER_ADDED_NOTIFIER, stationName,
				Integer.toString(addeduserGUID.getGUID()) };
		String notification = MessageBuilder.buildMessage(elements,
				MFNetworking.SEPERATOR);
		sendToAll(notification);
		log("User at " + addeduserGUID + " has been added to the station");
	}

	public void songAddedNotifier(GUID songGUID) {
		/**
		 * This function notifies all devices that a new song has been added to
		 * the queue.
		 */

		String[] elements = { MFNetworking.SONG_ADDED_NOTIFIER,
				Integer.toString(songGUID.getGUID()) };
		String notification = MessageBuilder.buildMessage(elements,
				MFNetworking.SEPERATOR);
		sendToAll(notification);
	}

	public void songRemovedNotifier(GUID song) {
		/**
		 * This function notifies all devices that a new song has been removed
		 * to the queue.
		 */

		String[] elements = { MFNetworking.SONG_REMOVED_NOTIFIER,
				Integer.toString(song.getGUID()) };
		String notification = MessageBuilder.buildMessage(elements,
				MFNetworking.SEPERATOR);
		sendToAll(notification);
	}

	public void sendPlaylist(GUID userGUID) throws IOException {
		/**
		 * This function sends each song on the playlist to a user. This
		 * function should only be used if the playlist is explicitly requested
		 * by a user. Station should default to notifying a device each time a
		 * song is added.
		 */

		String data = null;
		for (GUID s : songQueue) {
			String[] elements = { MFNetworking.SONG_ON_LIST_RESPONSE,
					stationName, Integer.toString(s.getGUID()) };
			data = MessageBuilder.buildMessage(elements, MFNetworking.SEPERATOR);
			sendToUser(data, userGUID);
		}

		log("Songs on station queue have been sent to the user at " + userGUID);
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
			String[] elements = { MFNetworking.USER_ON_LIST_RESPONSE,
					stationName, Integer.toString(user.getGUID()) };
			data = MessageBuilder.buildMessage(elements, MFNetworking.SEPERATOR);
			sendToUser(data, userGUID);
		}

		log("Socket address of users on station user list have been sent to the user at "
				+ userGUID);
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

	private void playSong(GUID song) throws StationException, IOException,
			InterruptedException {
		/* Establish a socket connection and play song with GUID songGUID */

		// Check to see if song is on songSourceMap
		if (!songSourceMap.containsKey(song))
			throw new StationException("In function playSong(), \"" + song
					+ "\" is not on songSourceMap.");

		int songLength = getSongLength(song);
		GUID songSource = getSongSource(song);

		/* Send command to device to play song */
		String[] commandElements = { MFNetworking.PLAY_SONG_CMD,
				Integer.toString(song.getGUID()) };
		String command = MessageBuilder.buildMessage(commandElements,
				MFNetworking.SEPERATOR);
		sendToUser(command, songSource);

		/* Send notification to all devices of current song playing */
		String[] notificationElements = {
				MFNetworking.CURRENTLY_PLAYING_NOTIFIER, stationName,
				Integer.toString(song.getGUID()),
				Integer.toString(songSource.getGUID()) };
		String notification = MessageBuilder.buildMessage(notificationElements,
				MFNetworking.SEPERATOR);
		sendToAll(notification);

		/* Display station queue status */
		log("Instructed user at \"" + songSource.getGUID() + "\" to play \""
				+ song + "\". Song length = " + songLengthMap.get(song)
				+ "ms. Songs played = " + songsPlayedQueue.size()
				+ ". Songs on queue = " + songQueue.size());

		Thread.sleep(songLength);
	}

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

	private void log(String message) {
		/** This function displays log messages on the console. */

		String logMessage = "[" + stationName + "] station log: " + message;
		System.out.println(logMessage);
	}
}
