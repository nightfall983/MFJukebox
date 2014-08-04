package com.ketonax.networking;

import java.util.ArrayList;

import edu.rutgers.winlab.jmfapi.GUID;

public class NamingService {

	/* The first 10 GUIDs are reserved for the server and the phones */
	private static int guid = 10;
	private static ArrayList<GUID> freedGUIDs = new ArrayList<GUID>();

	public static GUID assignGUID() {
		GUID availableID = null;

		if (!freedGUIDs.isEmpty())
			availableID = freedGUIDs.remove(0);
		else {
			if (guid < 9999999)
				guid++;
			availableID = new GUID(guid);
			guid++;// should replace with an algorithm
		}
		return availableID;
	}

	/**
	 * This method takes an GUID and recycles it by storing it as an available
	 * GUID
	 */
	public static void freeGUID(GUID id) {
		freedGUIDs.add(id);
	}
}
