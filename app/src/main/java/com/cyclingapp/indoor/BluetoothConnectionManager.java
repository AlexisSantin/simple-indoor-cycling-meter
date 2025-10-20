package com.cyclingapp.indoor;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothConnectionManager {
    
    private static final String TAG = "BTConnectionManager";
    private static BluetoothConnectionManager instance;
    
    private static final UUID CYCLING_POWER_SERVICE_UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb");
    private static final UUID CYCLING_POWER_MEASUREMENT_UUID = UUID.fromString("00002A63-0000-1000-8000-00805f9b34fb");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    
    private BluetoothGatt bluetoothGatt;
    private boolean isConnected = false;
    private List<BluetoothDataListener> listeners = new ArrayList<>();
    
    public interface BluetoothDataListener {
        void onDataReceived(int power, int cadence);
        void onConnectionStateChanged(boolean connected);
    }
    
    private BluetoothConnectionManager() {}
    
    public static synchronized BluetoothConnectionManager getInstance() {
        if (instance == null) {
            instance = new BluetoothConnectionManager();
        }
        return instance;
    }
    
    public void addListener(BluetoothDataListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    public void removeListener(BluetoothDataListener listener) {
        listeners.remove(listener);
    }
    
    public boolean isConnected() {
        return isConnected;
    }
    
    public void connect(Context context, BluetoothDevice device) {
        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback);
            Log.d(TAG, "Connexion GATT initiée");
        } catch (SecurityException e) {
            Log.e(TAG, "Erreur permission: " + e.getMessage());
        }
    }
    
    public void disconnect() {
        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
                bluetoothGatt = null;
                isConnected = false;
                notifyConnectionStateChanged(false);
                Log.d(TAG, "Déconnexion");
            } catch (SecurityException e) {
                Log.e(TAG, "Erreur déconnexion: " + e.getMessage());
            }
        }
    }
    
    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d(TAG, "Connecté au GATT");
                isConnected = true;
                notifyConnectionStateChanged(true);
                
                try {
                    gatt.discoverServices();
                } catch (SecurityException e) {
                    Log.e(TAG, "Erreur découverte services: " + e.getMessage());
                }
                
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.d(TAG, "Déconnecté du GATT");
                isConnected = false;
                notifyConnectionStateChanged(false);
                bluetoothGatt = null;
            }
        }
        
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services découverts");
                BluetoothGattService service = gatt.getService(CYCLING_POWER_SERVICE_UUID);
                if (service != null) {
                    BluetoothGattCharacteristic characteristic = 
                        service.getCharacteristic(CYCLING_POWER_MEASUREMENT_UUID);
                    if (characteristic != null) {
                        try {
                            gatt.setCharacteristicNotification(characteristic, true);
                            
                            BluetoothGattDescriptor descriptor = 
                                characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID);
                            if (descriptor != null) {
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                gatt.writeDescriptor(descriptor);
                                Log.d(TAG, "Notifications activées");
                            }
                        } catch (SecurityException e) {
                            Log.e(TAG, "Erreur notification: " + e.getMessage());
                        }
                    }
                }
            }
        }
        
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (CYCLING_POWER_MEASUREMENT_UUID.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();
                if (data != null && data.length >= 4) {
                    // Parser les données
                    int flags = (data[0] & 0xFF) | ((data[1] & 0xFF) << 8);
                    int power = (data[2] & 0xFF) | ((data[3] & 0xFF) << 8);
                    if (power > 32767) power -= 65536;
                    power = Math.max(0, power);
                    
                    int cadence = 0;
                    int offset = 4;
                    if ((flags & 0x20) != 0 && data.length >= offset + 4) {
                        int crankRevolutions = (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
                        cadence = crankRevolutions % 256;
                    }
                    
                    notifyDataReceived(power, cadence);
                }
            }
        }
    };
    
    private void notifyDataReceived(int power, int cadence) {
        for (BluetoothDataListener listener : listeners) {
            listener.onDataReceived(power, cadence);
        }
    }
    
    private void notifyConnectionStateChanged(boolean connected) {
        for (BluetoothDataListener listener : listeners) {
            listener.onConnectionStateChanged(connected);
        }
    }
}
