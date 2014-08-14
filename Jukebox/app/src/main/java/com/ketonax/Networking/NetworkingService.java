package com.ketonax.Networking;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.ketonax.Constants.AppConstants;
import com.ketonax.Constants.Networking;
import com.ketonax.jukebox.Util.Mp3Info;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;

import edu.rutgers.winlab.jgnrs.JGNRS;
import edu.rutgers.winlab.jmfapi.GUID;
import edu.rutgers.winlab.jmfapi.JMFAPI;
import edu.rutgers.winlab.jmfapi.JMFException;
import edu.rutgers.winlab.jmfapi.MFFlag;

/**
 * Created by nightfall on 7/13/14.
 */

public class NetworkingService extends Service {

    static Messenger mClient;
    boolean keepRunning = true;
    private JMFAPI socket = null;
    private JGNRS gnrs = null;
    private static Sender sender = null;
    private MFReceiver receiver = null;
    private ServerSocket serverSocket = null;
    private TCP_Receiver tcpReceiver = null;
    private Messenger mMessenger = new Messenger(new IncomingHandler());

    /* Network Variables */
    private InetAddress serverAddress = null;
    private String currentStation;

    public NetworkingService() {

    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        socket = new JMFAPI();
        init();
        setupGNRS();
        receiver = new MFReceiver();
        receiver.start();
        tcpReceiver = new TCP_Receiver();
        tcpReceiver.start();
        socket = new JMFAPI();
        sender = new Sender();
    }

    @Override
    public void onDestroy() {
        keepRunning = false;

        if (!serverSocket.isClosed()) {
            try {
                serverSocket.close();
                serverSocket = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        super.onDestroy();
    }

    public static void sendSong(GUID userID, Mp3Info song) {
        sender.sendMusicToUser(userID, song);
    }

    public static void sendSongToAll(GUID stationID, Mp3Info song){
        sender.sendMusicToAll(stationID, song);
    }

    private void init() {

        MFFlag flag = new MFFlag();
        flag.setValue(MFFlag.MF_MHOME);

        if (!socket.isOpen()) {
            try {
                socket.jmfopen("basic", flag, Networking.deviceID);
            } catch (JMFException e) {
                e.printStackTrace();
            }
        }
    }

    private void setupGNRS() {
        gnrs = new JGNRS();
        gnrs.setGNRS(Networking.GNRS_IP_STRING + ":" + Networking.GNRS_PORT,
                Networking.getLocalIP() + ":" + Networking.GNRS_LISTEN_PORT);
    }

    private class Sender {
        /**
         * Used for sending data to Jukebox Server
         */
        private AsyncTask<Void, Void, Void> asyncSender;

        public void send(final String message) {
            asyncSender = new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {

                    byte[] sendData = message.getBytes(Charset.defaultCharset());

                    try {
                        socket.jmfsend(sendData, sendData.length, Networking.SERVER_GUID);
                    } catch (JMFException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            };

            if (Build.VERSION.SDK_INT >= 11) {
                asyncSender.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                asyncSender.execute();
            }
        }

        public void sendMusicToUser(final GUID userGUID, final Mp3Info song) {
            asyncSender = new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {

                    File file = new File(song.getUrl());

                                        /* Check file validity */
                    if (file.isFile()) {

                        try {
                            DataInputStream inputStream = new DataInputStream(new FileInputStream(file));
                            long length = (int) file.length();
                            byte[] fileBytes = new byte[(int) length];
                            int read = 0;
                            int numRead = 0;
                            while (read < fileBytes.length && (numRead = inputStream.read(fileBytes,
                                    read, fileBytes.length - read)) >= 0) {
                                read = read + numRead;
                            }

                            try {
                                socket.jmfsend(fileBytes, fileBytes.length, userGUID);
                            } catch (JMFException e) {
                                e.printStackTrace();
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        Log.e(AppConstants.APP_TAG, song.getUrl() + " is not a path to a file.");
                    }
                    return null;
                }
            };

            if (Build.VERSION.SDK_INT >= 11) {
                asyncSender.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                asyncSender.execute();
            }
        }

        public void sendMusicToAll(final GUID stationGUID, final Mp3Info song) {
            asyncSender = new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {

                    File file = new File(song.getUrl());

                                        /* Check file validity */
                    if (file.isFile()) {

                        try {
                            DataInputStream inputStream = new DataInputStream(new FileInputStream(file));
                            long length = (int) file.length();
                            byte[] fileBytes = new byte[(int) length];
                            int read = 0;
                            int numRead = 0;
                            while (read < fileBytes.length && (numRead = inputStream.read(fileBytes,
                                    read, fileBytes.length - read)) >= 0) {
                                read = read + numRead;
                            }

                            try {
                                socket.jmfsend(fileBytes, fileBytes.length, stationGUID);
                            } catch (JMFException e) {
                                e.printStackTrace();
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        Log.e(AppConstants.APP_TAG, song.getUrl() + " is not a path to a file.");
                    }
                    return null;
                }
            };

            if (Build.VERSION.SDK_INT >= 11) {
                asyncSender.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                asyncSender.execute();
            }
        }
    }

    private class MFReceiver extends Thread {

        public MFReceiver() {
        }

        @Override
        public void run() {
            try {
                while (keepRunning) {

                    byte[] receiveData = new byte[Networking.DATA_LIMIT_SIZE];
                    int recvLen = 0;
                    String message = null;
                    GUID userGUID = new GUID();

                    if ((recvLen = socket.jmfrecv_blk(userGUID, receiveData,
                            receiveData.length)) > 0) {

                        if (userGUID == Networking.SERVER_GUID) {
                            message = new String(receiveData, 0, recvLen, Charset.defaultCharset());

                                            /* Parse messages and respond accordingly */
                            if (message.startsWith(Networking.JUKEBOX_MESSAGE_IDENTIFIER)) {

                                if (message.contains(Networking.SEPARATOR)) {
                                    String[] msgArray = message.split(Networking.SEPARATOR);

                                    if (msgArray[0].equals(Networking.STATION_KILLED_NOTIFIER)) {
                                        Log.i(AppConstants.APP_TAG,
                                                Networking.STATION_KILLED_NOTIFIER);
                                        String stationName = msgArray[1];
                                        Bundle bundle = new Bundle();
                                        bundle.putString(AppConstants.STATION_NAME_KEY,
                                                stationName);
                                        Message msg = Message.obtain(null,
                                                AppConstants.STATION_KILLED_NOTIFIER);
                                        msg.setData(bundle);
                                        try {
                                            if (mClient != null) {
                                                mClient.send(msg);
                                            }
                                        } catch (RemoteException e) {
                                            e.printStackTrace();
                                        }
                                    } else if (msgArray[0].equals(Networking
                                            .STATION_ADDED_NOTIFIER)) {
                                        Log.i(AppConstants.APP_TAG,
                                                Networking.STATION_ADDED_NOTIFIER);
                                        String stationName = msgArray[1];
                                        int stationID = Integer.parseInt(msgArray[2]);
                                        Bundle bundle = new Bundle();
                                        bundle.putString(AppConstants.STATION_NAME_KEY,
                                                stationName);
                                        bundle.putInt(AppConstants.STATION_GUID_KEY, stationID);
                                        Message msg = Message.obtain(null,
                                                AppConstants.STATION_ADDED_NOTIFIER);
                                        msg.setData(bundle);
                                        try {
                                            if (mClient != null) {
                                                mClient.send(msg);
                                            }
                                        } catch (RemoteException e) {
                                            e.printStackTrace();
                                        }
                                    } else if (msgArray[0].equals(Networking.SONG_ADDED_NOTIFIER)) {
                                        Log.i(AppConstants.APP_TAG, Networking.SONG_ADDED_NOTIFIER);
                                        String stationName = msgArray[1];
                                        String songName = msgArray[2];
                                        Message msg = Message.obtain(null,
                                                AppConstants.SONG_ADDED_NOTIFIER);
                                        Bundle bundle = new Bundle();
                                        bundle.putString(AppConstants.STATION_NAME_KEY,
                                                stationName);
                                        bundle.putString(AppConstants.SONG_NAME_KEY, songName);
                                        msg.setData(bundle);
                                        try {
                                            if (mClient != null) {
                                                mClient.send(msg);
                                            }
                                        } catch (RemoteException e) {
                                            e.printStackTrace();
                                        }
                                    } else if (msgArray[0].equals(Networking
                                            .SONG_REMOVED_NOTIFIER)) {
                                        Log.i(AppConstants.APP_TAG,
                                                Networking.SONG_REMOVED_NOTIFIER);
                                        String stationName = msgArray[1];
                                        String songName = msgArray[2];
                                        Message msg = Message.obtain(null,
                                                AppConstants.SONG_REMOVED_NOTIFIER);
                                        Bundle bundle = new Bundle();
                                        bundle.putString(AppConstants.STATION_NAME_KEY,
                                                stationName);
                                        bundle.putString(AppConstants.SONG_NAME_KEY, songName);
                                        msg.setData(bundle);
                                        try {
                                            if (mClient != null) {
                                                mClient.send(msg);
                                            }
                                        } catch (RemoteException e) {
                                            e.printStackTrace();
                                        }
                                    } else if (msgArray[0].equals(Networking
                                            .SONG_ON_LIST_RESPONSE)) {
                                        String stationName = msgArray[1];
                                        String songName = msgArray[2];
                                        Message msg = Message.obtain(null,
                                                AppConstants.SONG_ON_LIST_RESPONSE);
                                        Bundle bundle = new Bundle();
                                        bundle.putString(AppConstants.STATION_NAME_KEY,
                                                stationName);
                                        bundle.putString(AppConstants.SONG_NAME_KEY, songName);
                                        msg.setData(bundle);
                                        try {
                                            if (mClient != null) {
                                                mClient.send(msg);
                                            }
                                        } catch (RemoteException e) {
                                            e.printStackTrace();
                                        }
                                    } else if (msgArray[0].equals(Networking.USER_ADDED_NOTIFIER)) {
                                        Log.i(AppConstants.APP_TAG, Networking.USER_ADDED_NOTIFIER);
                                        String stationName = msgArray[1];
                                        int userID = Integer.parseInt(msgArray[2]);

                                        Bundle bundle = new Bundle();
                                        bundle.putString(AppConstants.STATION_NAME_KEY,
                                                stationName);
                                        bundle.putInt(AppConstants.USER_GUID, userID);
                                        Message msg = Message.obtain(null,
                                                AppConstants.USER_ADDED_NOTIFIER);
                                        msg.setData(bundle);
                                        try {
                                            if (mClient != null) {
                                                mClient.send(msg);
                                            }
                                        } catch (RemoteException e) {
                                            e.printStackTrace();
                                        }
                                    } else if (msgArray[0].equals(Networking
                                            .USER_REMOVED_NOTIFIER)) {
                                        Log.i(AppConstants.APP_TAG,
                                                Networking.USER_REMOVED_NOTIFIER);
                                        String stationName = msgArray[1];
                                        int userID = Integer.parseInt(msgArray[2]);


                                        Bundle bundle = new Bundle();
                                        bundle.putString(AppConstants.STATION_NAME_KEY,
                                                stationName);
                                        bundle.putInt(AppConstants.USER_GUID, userID);
                                        Message msg = Message.obtain(null,
                                                AppConstants.USER_REMOVED_NOTIFIER);
                                        msg.setData(bundle);
                                        try {
                                            if (mClient != null) {
                                                mClient.send(msg);
                                            }
                                        } catch (RemoteException e) {
                                            e.printStackTrace();
                                        }
                                    } else if (msgArray[0].equals(Networking
                                            .CURRENTLY_PLAYING_NOTIFIER)) {
                                        Log.i(AppConstants.APP_TAG,
                                                Networking.CURRENTLY_PLAYING_NOTIFIER);
                                        String stationName = msgArray[1];
                                        String currentSongPlaying = msgArray[2];
                                        String songHolderAddress[] = msgArray[3].split(":");
                                        int trackPosition = Integer.parseInt(msgArray[4]);
                                        String songHolderIPString = songHolderAddress[0];

                                        // Check to see if userIP contains '/' and remove it
                                        if (songHolderIPString.startsWith("/")) {
                                            songHolderIPString = songHolderIPString.replaceFirst
                                                    ("/", "");
                                        }
                                        Message msg = Message.obtain(null,
                                                AppConstants.CURRENTLY_PLAYING_NOTIFIER);
                                        Bundle bundle = new Bundle();
                                        bundle.putString(AppConstants.STATION_NAME_KEY,
                                                stationName);
                                        bundle.putString(AppConstants.SONG_NAME_KEY,
                                                currentSongPlaying);
                                        bundle.putString(AppConstants.USER_IP_KEY,
                                                songHolderIPString);
                                        bundle.putInt(AppConstants.TRACK_POSITION_KEY,
                                                trackPosition);
                                        msg.setData(bundle);
                                        try {
                                            if (mClient != null) {
                                                mClient.send(msg);
                                            }
                                        } catch (RemoteException e) {
                                            e.printStackTrace();
                                        }
                                    } else if (msgArray[0].equals(Networking.SEND_SONG_CMD)) {
                                        Log.i(AppConstants.APP_TAG, "Received send song command.");
                                        String stationName = msgArray[1];
                                        String songName = msgArray[2];
                                        Message msg = Message.obtain(null,
                                                AppConstants.SEND_SONG_CMD);
                                        Bundle bundle = new Bundle();
                                        bundle.putString(AppConstants.STATION_NAME_KEY,
                                                stationName);
                                        bundle.putString(AppConstants.SONG_NAME_KEY, songName);
                                        msg.setData(bundle);
                                        try {
                                            if (mClient != null) {
                                                mClient.send(msg);
                                            }
                                        } catch (RemoteException e) {
                                            e.printStackTrace();
                                        }
                                    } else if (msgArray[0].equals(Networking
                                            .SEND_SONG_TO_USER_CMD)) {
                                        Log.i(AppConstants.APP_TAG, "Received send song to user command.");
                                        String stationName = msgArray[1];
                                        String songName = msgArray[2];
                                        int userID = Integer.parseInt(msgArray[3]);
                                        Message msg = Message.obtain(null,
                                                AppConstants.SEND_SONG_TO_USER_CMD);
                                        Bundle bundle = new Bundle();
                                        bundle.putString(AppConstants.STATION_NAME_KEY,
                                                stationName);
                                        bundle.putString(AppConstants.SONG_NAME_KEY, songName);
                                        bundle.putInt(AppConstants.USER_GUID, userID);
                                        msg.setData(bundle);
                                        try {
                                            if (mClient != null) {
                                                mClient.send(msg);
                                            }
                                        } catch (RemoteException e) {
                                            e.printStackTrace();
                                        }
                                    } else if (msgArray[0].equals(Networking
                                            .STATION_LIST_REQUEST_RESPONSE)) {
                                        Log.i(AppConstants.APP_TAG,
                                                Networking.STATION_LIST_REQUEST_RESPONSE);
                                        String stationName = msgArray[1];
                                        Bundle bundle = new Bundle();
                                        bundle.putString(AppConstants.STATION_NAME_KEY,
                                                stationName);
                                        Message msg = Message.obtain(null,
                                                AppConstants.STATION_LIST_REQUEST_RESPONSE);
                                        msg.setData(bundle);
                                        try {
                                            if (mClient != null) {
                                                mClient.send(msg);
                                            }
                                        } catch (RemoteException e) {
                                            e.printStackTrace();
                                        }
                                    } else if (msgArray[0].equals(Networking
                                            .USER_ON_LIST_RESPONSE)) {
                                        String stationName = msgArray[1];
                                        int userID = Integer.parseInt(msgArray[2]);

                                        Bundle bundle = new Bundle();
                                        bundle.putString(AppConstants.STATION_NAME_KEY,
                                                stationName);
                                        bundle.putInt(AppConstants.USER_GUID, userID);
                                        Message msg = Message.obtain(null,
                                                AppConstants.USER_ON_LIST_RESPONSE);
                                        msg.setData(bundle);
                                        try {
                                            if (mClient != null) {
                                                mClient.send(msg);
                                            }
                                        } catch (RemoteException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                } else if (message.equals(Networking.PING)) {
                                    Message msg = Message.obtain(null, AppConstants.PING);
                                    if (mClient != null) {
                                        try {
                                            mClient.send(msg);
                                        } catch (RemoteException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                } else {
                                    Log.e(AppConstants.APP_TAG, "Unknown message received from " +
                                            "server: " + message);
                                }
                            } else {
                                Log.e(AppConstants.APP_TAG, "Unknown message received from " +
                                        "server: " + message);
                            }
                        } else {
                            //TODO Receive music here
                        }
                    }
                }
            } catch (JMFException e) {
                e.printStackTrace();
            }
        }
    }

    private class TCP_Receiver extends Thread {

        String statusSuccess = "Success";
        String statusError = "Error";
        private ObjectInputStream objectInputStream = null;
        private boolean isConnected = false;
        private FileEvent fileEvent;
        private File downloadedFile;
        private File cacheDir;
        private FileOutputStream fileOutputStream = null;

        public TCP_Receiver() {

        }

        @Override
        public void run() {

            Log.d(AppConstants.APP_TAG, "TCP receiver has started.");

            try {
                serverSocket = new ServerSocket(Networking.TCP_PORT_NUMBER);
            } catch (IOException e) {
                e.printStackTrace();
            }

            while (keepRunning) {

                /* Connect serverSocket and initialize objectInputStream */
                try {

                    Socket connectionSocket = serverSocket.accept();
                    objectInputStream = new ObjectInputStream(connectionSocket.getInputStream());
                    isConnected = true;
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (isConnected) {
                    downloadFile();
                }
            }
        }

        private void downloadFile() {

            try {
                fileEvent = (FileEvent) objectInputStream.readObject();
                if (fileEvent.getStatus().equals(statusError)) {
                    Log.e(AppConstants.APP_TAG, "Download error occurred");
                } else {
                    String fileName = fileEvent.getFilename();
                    //downloadedFile = File.createTempFile(fileName, null,
                    //getApplicationContext().getCacheDir());
                    cacheDir = new File(getApplicationContext().getCacheDir(), fileName);
                    cacheDir.mkdirs();
                    downloadedFile = new File(cacheDir, fileName);

                    /* Save downloaded file */
                    saveFile();

                    /* Give MainActivity the song name and path */
                    songDownloadedNotifier();
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void saveFile() {
            /** Writes file to temporary cache storage if it doesn't already exist.*/

            try {
                if (!downloadedFile.exists()) {
                    downloadedFile.createNewFile();
                    fileOutputStream = new FileOutputStream(downloadedFile);
                    fileOutputStream.write(fileEvent.getFileData());
                    fileOutputStream.close();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void songDownloadedNotifier() {
            /**This method notifies the main activity that the current song to be played has been
             *  downloaded.
             * */

            Message msg = Message.obtain(null, AppConstants.SONG_DOWNLOADED);
            Bundle bundle = new Bundle();
            bundle.putString(AppConstants.SONG_NAME_KEY, fileEvent.getSongName());
            bundle.putString(AppConstants.SONG_PATH_KEY, downloadedFile.getPath());
            msg.setData(bundle);
            try {
                if (mClient != null) {
                    mClient.send(msg);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /* Receive messages from client */
    public class IncomingHandler extends Handler {
        String messageToSend = null;

        public IncomingHandler() {

            try {
                serverAddress = InetAddress.getByName(Networking.SERVER_IP_STRING);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case AppConstants.MSG_REGISTER_CLIENT:
                    mClient = msg.replyTo;
                    break;
                case AppConstants.MSG_UNREGISTER_CLIENT:
                    mClient = null;
                    break;
                case AppConstants.CREATE_STATION_CMD: {
                    Bundle bundle = msg.getData();
                    String stationName = bundle.getString(AppConstants.STATION_NAME_KEY);
                    currentStation = stationName;
                    messageToSend = Networking.buildCreateStationCommand(stationName);
                    sender.send(messageToSend);
                }
                break;
                case AppConstants.STATION_LIST_REQUEST_CMD: {
                    messageToSend = Networking.STATION_LIST_REQUEST_CMD;
                    sender.send(messageToSend);
                }
                break;
                case AppConstants.JOIN_STATION_CMD: {
                    Bundle bundle = msg.getData();
                    String stationName = bundle.getString(AppConstants.STATION_NAME_KEY);
                    currentStation = stationName;
                    messageToSend = Networking.buildJoinStationCommand(stationName);
                    sender.send(messageToSend);
                }
                break;
                case AppConstants.LEAVE_STATION_CMD: {
                    Bundle bundle = msg.getData();
                    String stationName = bundle.getString(AppConstants.STATION_NAME_KEY);
                    messageToSend = Networking.buildLeaveStationCommand(stationName);
                    sender.send(messageToSend);
                    currentStation = null;
                }
                break;
                case AppConstants.ADD_SONG_CMD: {
                    Bundle bundle = msg.getData();
                    String stationName = bundle.getString(AppConstants.STATION_NAME_KEY);
                    String songName = bundle.getString(AppConstants.SONG_NAME_KEY);
                    String songLength = bundle.getString(AppConstants.SONG_LENGTH_KEY);
                    messageToSend = Networking.buildAddSongCommand(stationName, songName,
                            songLength);
                    sender.send(messageToSend);
                }
                break;
                case AppConstants.GET_PLAYLIST_CMD: {
                    Bundle bundle = msg.getData();
                    String stationName = bundle.getString(AppConstants.STATION_NAME_KEY);
                    messageToSend = Networking.buildGetPlaylistCommand(stationName);
                    sender.send(messageToSend);
                }
                break;
                case AppConstants.SONG_DOWNLOADED_NOTIFIER: {
                    Bundle bundle = msg.getData();
                    String stationName = bundle.getString(AppConstants.STATION_NAME_KEY);
                    String notifier = Networking.buildSongDownloadedNotifier(stationName);
                    sender.send(notifier);
                }
                break;
                case AppConstants.EXIT_JUKEBOX_NOTIFIER: {
                    Bundle bundle = msg.getData();
                    String exitCommand = Networking.buildExitJukeboxNotifier();
                    sender.send(exitCommand);
                    currentStation = null;
                }
                break;
                case AppConstants.PING_RESPONSE: {
                    Bundle bundle = msg.getData();
                    String response = bundle.getString(AppConstants.PING_RESPONSE_KEY);
                    sender.send(response);
                }
            }
        }
    }
}