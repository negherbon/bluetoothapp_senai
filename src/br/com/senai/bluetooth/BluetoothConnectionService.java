package br.com.senai.bluetooth;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BluetoothConnectionService {
	// Debugging
    private static final String TAG = "bluetooth";
 
    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "bluetoothConnectionSecure";

    private final BluetoothAdapter mAdapter;
	private AcceptThread mSecureAcceptThread;
    private ConnectThread mConnectThread;    
    private ConnectedThread mConnectedThread;
    private int mState;

	private Context context;
    
    public static final int STATE_NONE = 0;
    public static final int STATE_LISTEN = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 3;
    
	private static final UUID MY_UUID_SECURE = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
	
	public BluetoothConnectionService(Context context) {
		this.context = context;
		mAdapter = BluetoothAdapter.getDefaultAdapter();
	    mState = STATE_NONE;
	}
	
	/**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
	public synchronized void start(){
		Log.d(TAG, "start");
		 
        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
 
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
 
        setState(STATE_LISTEN);
 
        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = new AcceptThread();
            mSecureAcceptThread.start();
        }
	}
	
	/**
     * Stop all threads
     */
    public synchronized void stop() {
        Log.d(TAG, "stop");
 
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
 
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
 
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }
        setState(STATE_NONE);
    }
    
    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }
	
	public synchronized void connect(BluetoothDevice device){
		if (mState == STATE_CONNECTING){
			if (mConnectThread != null){
				mConnectThread.cancel();
				mConnectThread = null;
			}
		}
		
		if (mConnectedThread != null){
			mConnectedThread.cancel();
			mConnectedThread = null;
		}
		
		// Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
        setState(STATE_CONNECTING);
	}
	
	/**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device) {
        Log.d(TAG, "CONNECTTTTTTTEDDDDDDDDDDDDD");
 
        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
 
        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
 
        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }
 
        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        Log.i(TAG, "Connected to device " + device.getName());
        
        Intent intent = new Intent("DEVICE_CONNECTED");
        context.getApplicationContext().sendBroadcast(intent);
        
        setState(STATE_CONNECTED);
    }
	
	/**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        //Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        //Bundle bundle = new Bundle();
        //bundle.putString(Constants.TOAST, "Device connection was lost");
        //msg.setData(bundle);
        //mHandler.sendMessage(msg);
 
        // Start the service over to restart listening mode
        BluetoothConnectionService.this.start();
    }
    
    /**
     * Set the current state of the chat connection
     *
     * @param state An integer defining the current connection state
     */
    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;
    }
    
    /**
     * Return the current connection state.
     */
    public synchronized int getState() {
        return mState;
    }
    
    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
    	Log.i(TAG, "CONNECTION FAILED");
    	
        // Send a failure message back to the Activity
        //Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        //Bundle bundle = new Bundle();
        //bundle.putString(Constants.TOAST, "Unable to connect device");
        //msg.setData(bundle);
        //mHandler.sendMessage(msg);
 
        // Start the service over to restart listening mode
        BluetoothConnectionService.this.start();
    }
    
    public void unpairDevice(BluetoothDevice device) {
    	try {
    	    Method m = device.getClass()
    	        .getMethod("removeBond", (Class[]) null);
    	    m.invoke(device, (Object[]) null);
    	} catch (Exception e) {
    	    Log.e(TAG, e.getMessage());
    	}
	}
	
    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
	@SuppressLint("NewApi")
	private class AcceptThread extends Thread {
    	// The local server socket
        private final BluetoothServerSocket mmServerSocket;
        
        public AcceptThread() {
            BluetoothServerSocket tmp = null;
 
            // Create a new listening server socket
            try {
                tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE,
                        MY_UUID_SECURE);
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: listen() failed", e);
            }
            mmServerSocket = tmp;
        }
        
        public void run() {
            Log.d(TAG, "Socket Type: BEGIN mAcceptThread" + this);
            setName("AcceptThread");
 
            BluetoothSocket socket = null;
 
            // Listen to the server socket if we're not connected
            while (mState != STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket Type: accept() failed", e);
                    break;
                }
 
                // If a connection was accepted
                if (socket != null) {
                    synchronized (BluetoothConnectionService.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // Situation normal. Start the connected thread.
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
            Log.i(TAG, "END mAcceptThread, socket Type: ");
        }
        
        public void cancel() {
            Log.d(TAG, "Socket Type cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket Type close() of server failed", e);
            }
        }
    }
    
	private class ConnectThread extends Thread{
		private BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;
		private String mSocketType;
		
		public ConnectThread(BluetoothDevice device){
			mmDevice = device;
			BluetoothSocket tmp = null;
			
			try {
				tmp = device.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
			} catch(IOException e){
				Log.e(TAG, "Socket type " + mSocketType + "create() failed", e);
			}
			
			mmSocket = tmp;
		}
		
		public void run(){
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
            setName("ConnectThread" + mSocketType);
            
            mAdapter.cancelDiscovery();
            
            try {
            	mmSocket.connect();
            } catch(IOException e){
                try {
                	mmSocket = (BluetoothSocket) mmDevice.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(mmDevice,1);
                	mmSocket.connect();
                } catch (Exception e2) {
                    Log.e(TAG, "unable to close() " + mSocketType +
                            " socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }
            
            // Reset the ConnectThread because we're done
            synchronized (BluetoothConnectionService.this) {
                mConnectThread = null;
            }
            
            connected(mmSocket, mmDevice);
		}
		
		public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }
	}
	
    private class ConnectedThread extends Thread {
    	private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        
        public ConnectedThread(BluetoothSocket socket) {
            Log.d(TAG, "create ConnectedThread: ");
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
 
            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }
 
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }
        
        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;
 
            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    
                    // Send the obtained bytes to the UI Activity
                    //mHandler.obtainMessage(1, bytes, -1, buffer)
                      //      .sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    // Start the service over to restart listening mode
                    BluetoothConnectionService.this.start();
                    break;
                }
            }
        }
        
        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
 
                //mHandler.obtainMessage(1, -1, -1, buffer)
                  //      .sendToTarget();
            } catch (IOException e) {
                Log.e("bluetootg", "Exception during write", e);
            }
        }
        
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    	
    	
    }
    
	
	

}
