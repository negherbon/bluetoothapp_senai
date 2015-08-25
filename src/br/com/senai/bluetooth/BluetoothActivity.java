package br.com.senai.bluetooth;

import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

public class BluetoothActivity extends Activity {
	
	private BluetoothDiscovery bluetoothDiscovery;
	
	private BluetoothAdapter mBluetoothAdapter;
	
	private final int REQUEST_ENABLE_BT = 1;

	private ArrayAdapter<BluetoothDevice> pairedDevicesArrayAdapter;
	
	private ArrayAdapter<BluetoothDevice> newDevicesArrayAdapter;
	
	private BluetoothService bluetoothConnectionService;
	
	/**
     * String buffer for outgoing messages
     */
    private StringBuffer mOutStringBuffer;
	
	private BroadcastReceiver bluetoothDiscoveryReceiver = new BroadcastReceiver(){
	
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			
			if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)){
				
			}	
			
	        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
	            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

	            if (device.getBondState() != BluetoothDevice.BOND_BONDED)
	            	newDevicesArrayAdapter.add(device);
	        }
			
	        if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
	        	
	        }
	        
		}
		
	};
	
	private BroadcastReceiver pairedDeviceReceiver = new BroadcastReceiver(){

		@Override
		public void onReceive(Context context, Intent intent) {
			Log.i("bluetooth", "PAIRED DEVICE RECEIVER");
			refreshPairedDevices();
		}
		
	};

	private ListView pairedListView;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_bluetooth);
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		} else if (bluetoothConnectionService == null) {
			setupConnectionService();
			
			bluetoothDiscovery = new BluetoothDiscovery(this, bluetoothDiscoveryReceiver);
			
			Button scanButton = (Button) findViewById(R.id.scanDevices);
	        scanButton.setOnClickListener(new View.OnClickListener() {
	            public void onClick(View v) {
	            	newDevicesArrayAdapter.clear();
	                bluetoothDiscovery.start();
	            }
	        });
	        
	        Button sendMessage = (Button) findViewById(R.id.sendMessage);
	        sendMessage.setOnClickListener(new View.OnClickListener() {
	            public void onClick(View v) {
	            	sendMessage();
	            }
	        });
	        
	        managePairedDevices();
	        manageDiscoveryDevices();
		}
	}
	
	private void managePairedDevices() {
		pairedDevicesArrayAdapter = new ArrayAdapter<BluetoothDevice>(this, R.layout.device_name);
        
        pairedListView = (ListView) findViewById(R.id.pairedDevices);
        pairedListView.setAdapter(pairedDevicesArrayAdapter);

        setPairedDevicesOnListView();
        
        pairedListView.setOnItemClickListener( new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				
				final BluetoothDevice device = (BluetoothDevice) pairedListView.getItemAtPosition(position);
				
				new AlertDialog.Builder(BluetoothActivity.this)
				.setTitle("Desparear")
				.setMessage("Deseja desparear esse dispositivo " + device.getName() + "?")
				.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						//bluetoothConnectionService.unpairDevice(device);
						refreshPairedDevices();
					}
				})
				.setNegativeButton("Não", null)
				.show();
			}
        	
		} );
        
        IntentFilter connectedIntentFilter = new IntentFilter("DEVICE_CONNECTED");
        registerReceiver(pairedDeviceReceiver, connectedIntentFilter);
	}
	
	private void setPairedDevicesOnListView(){
		Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        
        if (pairedDevices.size() > 0){
        	for (BluetoothDevice device : pairedDevices) {
                pairedDevicesArrayAdapter.add(device);
            }
        }
	}
	
	private void refreshPairedDevices(){
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		pairedDevicesArrayAdapter.clear();
		setPairedDevicesOnListView();
	}
		
	private void manageDiscoveryDevices() {
		newDevicesArrayAdapter = new ArrayAdapter<BluetoothDevice>(this, R.layout.device_name);
        
        final ListView devicesDiscoverableList = (ListView) findViewById(R.id.devicesDiscoverable);
        devicesDiscoverableList.setAdapter(newDevicesArrayAdapter);
        
        devicesDiscoverableList.setOnItemClickListener( new OnItemClickListener() {
        	
			private BluetoothDevice device;

			@Override
			public void onItemClick(AdapterView<?> adapter, View view,
					int position, long id) {
				
				device = (BluetoothDevice) devicesDiscoverableList.getItemAtPosition(position);
				
				new AlertDialog.Builder(BluetoothActivity.this)
					.setTitle("Parear")
					.setMessage("Deseja parear com o dispositivo " + device.getName() + "?")
					.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
							bluetoothConnectionService.connect(device, true);
						}
					})
					.setNegativeButton("Não", null)
					.show();
			}
        	
		} );
	}
	
	private void setupConnectionService() {
		bluetoothConnectionService = new BluetoothService(this);
		mOutStringBuffer = new StringBuffer();
	}

	@Override
	protected void onResume() {
		super.onResume();

        if (bluetoothConnectionService != null)
            if (bluetoothConnectionService.getState() == BluetoothConnectionService.STATE_NONE)
            	bluetoothConnectionService.start();
	}
	
	@Override
    public void onDestroy() {
        super.onDestroy();

        if (mBluetoothAdapter != null)
			mBluetoothAdapter.cancelDiscovery();
		
		bluetoothDiscovery.unregister();
        
        if (bluetoothConnectionService != null)
        	bluetoothConnectionService.stop();
    }

	private void sendMessage() {
		/*String message = "Yeahhhhh :)";
        // Check that we're actually connected before trying anything
	
        if (bluetoothConnectionService.getState() != bluetoothConnectionService.STATE_CONNECTED) {
            Log.i("bluetoothConnectionService", "DEU PAUUUUU");
            return;
        }
 
        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            bluetoothConnectionService.write(send);
 
            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
        }*/
    }
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		switch (requestCode) {
			case REQUEST_ENABLE_BT:
				if (resultCode == Activity.RESULT_OK)
					onStart();
				else
					finish();
		}
		
	}
	
		
}
