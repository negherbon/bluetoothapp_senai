package br.com.senai.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import br.com.senai.bluetooth.receiver.BluetoothDiscoveryReceiver;

public class BluetoothDiscovery {

	private BluetoothActivity bluetoothActivity;
	
	private BluetoothDiscoveryReceiver bluetoothDiscoveryReceiver;

	private BluetoothAdapter bluetoothAdapter;
	
	public BluetoothDiscovery(BluetoothActivity bluetoothActivity, BroadcastReceiver receiver) {
		this.bluetoothDiscoveryReceiver = new BluetoothDiscoveryReceiver();
		this.bluetoothActivity = bluetoothActivity;
		this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		registerReceiver(receiver);
	}
	
	private void registerReceiver(BroadcastReceiver receiver) {
		IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
		bluetoothActivity.registerReceiver(receiver, filter);
	}

	public void start() {
		if (bluetoothAdapter.isDiscovering())
			bluetoothAdapter.cancelDiscovery();
		bluetoothAdapter.startDiscovery();
	}
	
	public void unregister(){
		bluetoothActivity.unregisterReceiver(bluetoothDiscoveryReceiver);
	}
	
}
