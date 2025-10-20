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
    
    // Pour calcul de la cadence
    private int lastCrankRevolutions = -1;
    private int lastCrankTime = -1;
    
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
                
                // Réinitialiser les compteurs de cadence
                lastCrankRevolutions = -1;
                lastCrankTime = -1;
                lastCalculatedCadence = 0;
                
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
            Log.d(TAG, "onCharacteristicChanged appelé!");
            
            if (CYCLING_POWER_MEASUREMENT_UUID.equals(characteristic.getUuid())) {
                byte[] data = characteristic.getValue();
                Log.d(TAG, "Data length: " + (data != null ? data.length : 0));
                
                if (data != null && data.length >= 4) {
                    // Log des données brutes
                    StringBuilder hex = new StringBuilder();
                    for (byte b : data) {
                        hex.append(String.format("%02X ", b));
                    }
                    Log.d(TAG, "Data brute: " + hex.toString());
                    
                    // Parser les données selon le format Cycling Power Measurement
                    int flags = (data[0] & 0xFF) | ((data[1] & 0xFF) << 8);
                    Log.d(TAG, "Flags: 0x" + Integer.toHexString(flags));
                    
                    // Puissance instantanée (bytes 2-3)
                    int power = (data[2] & 0xFF) | ((data[3] & 0xFF) << 8);
                    if (power > 32767) power -= 65536;
                    power = Math.max(0, power);
                    Log.d(TAG, "Power parsé: " + power + " W");
                    
                    // Cadence depuis Crank Revolution Data (si présent)
                    int cadence = 0;
                    int offset = 4;
                    
                    // Bit 5 du flags indique la présence de Crank Revolution Data
                    boolean hasCrankData = (flags & 0x20) != 0;
                    Log.d(TAG, "Has crank data: " + hasCrankData + ", data length: " + data.length + ", need: " + (offset + 4));
                    
                    if (hasCrankData && data.length >= offset + 4) {
                        int currentRevolutions = (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
                        int currentTime = (data[offset + 2] & 0xFF) | ((data[offset + 3] & 0xFF) << 8);
                        Log.d(TAG, String.format("Revolutions: %d, Time: %d", currentRevolutions, currentTime));
                        
                        // Calculer la cadence (RPM) depuis les révolutions
                        cadence = calculateCadence(currentRevolutions, currentTime);
                    }
                    
                    Log.d(TAG, String.format("=== Données finales - Power: %d W, Cadence: %d RPM ===", power, cadence));
                    notifyDataReceived(power, cadence);
                } else {
                    Log.w(TAG, "Données trop courtes ou nulles");
                }
            } else {
                Log.w(TAG, "UUID incorrect: " + characteristic.getUuid());
            }
        }
    };
    
    private int lastCalculatedCadence = 0;
    
    private int calculateCadence(int currentRevolutions, int currentTime) {
        // Première mesure : initialiser sans calculer
        if (lastCrankRevolutions == -1 || lastCrankTime == -1) {
            lastCrankRevolutions = currentRevolutions;
            lastCrankTime = currentTime;
            return lastCalculatedCadence; // Retourner la dernière cadence connue
        }
        
        int revDelta = currentRevolutions - lastCrankRevolutions;
        int timeDelta = currentTime - lastCrankTime;
        
        // Gérer le rollover (dépassement de 65535)
        if (revDelta < 0) {
            revDelta += 65536;
        }
        if (timeDelta < 0) {
            timeDelta += 65536;
        }
        
        // Si pas de changement, retourner la dernière cadence
        if (revDelta == 0 || timeDelta == 0) {
            return lastCalculatedCadence;
        }
        
        // Calculer RPM seulement si les données sont cohérentes
        // timeDelta est en 1/1024 secondes
        double timeInSeconds = timeDelta / 1024.0;
        double rpm = (revDelta / timeInSeconds) * 60.0;
        
        // Filtrer les valeurs aberrantes (> 200 RPM impossible, < 20 RPM = arrêt)
        if (rpm > 0 && rpm <= 200) {
            lastCalculatedCadence = (int) Math.round(rpm);
        } else if (rpm < 20) {
            lastCalculatedCadence = 0; // Arrêt détecté
        }
        // Sinon, on garde la dernière valeur valide
        
        lastCrankRevolutions = currentRevolutions;
        lastCrankTime = currentTime;
        
        Log.d(TAG, String.format("Cadence calc - revDelta: %d, timeDelta: %d, RPM: %.1f, Final: %d", 
            revDelta, timeDelta, rpm, lastCalculatedCadence));
        
        return lastCalculatedCadence;
    }
    
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
