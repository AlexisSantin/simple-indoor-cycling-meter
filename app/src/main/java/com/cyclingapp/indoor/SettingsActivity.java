package com.cyclingapp.indoor;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SettingsActivity extends AppCompatActivity {
    
    private static final String TAG = "SettingsActivity";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 2;
    private static final long SCAN_PERIOD = 10000;
    
    private static final UUID CYCLING_POWER_SERVICE_UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb");
    private static final UUID CYCLING_POWER_MEASUREMENT_UUID = UUID.fromString("00002A63-0000-1000-8000-00805f9b34fb");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    
    // UI
    private TextView statusText;
    private Button connectButton;
    private EditText weightInput;
    private Button saveWeightButton;
    
    // Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean scanning = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    // Données
    private int userWeight = 75;
    private static final String PREFS_NAME = "CyclingAppPrefs";
    private static final String PREF_WEIGHT = "user_weight";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        // Activer le bouton retour
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Paramètres");
        }
        
        loadUserWeight();
        initializeViews();
        initializeBluetooth();
        setupClickListeners();
        updateConnectionStatus();
    }
    
    private void loadUserWeight() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        userWeight = prefs.getInt(PREF_WEIGHT, 75);
    }
    
    private void saveUserWeight() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PREF_WEIGHT, userWeight);
        editor.apply();
    }
    
    private void initializeViews() {
        statusText = findViewById(R.id.statusText);
        connectButton = findViewById(R.id.connectButton);
        weightInput = findViewById(R.id.weightInput);
        saveWeightButton = findViewById(R.id.saveWeightButton);
        
        weightInput.setText(String.valueOf(userWeight));
    }
    
    private void initializeBluetooth() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        
        if (bluetoothAdapter == null) {
            showToast("Bluetooth non disponible");
            return;
        }
        
        // Vérifier et demander les permissions selon la version Android
        if (!hasRequiredPermissions()) {
            requestRequiredPermissions();
            return;
        }
        
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }
        
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    }
    
    private boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) 
                    == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) 
                    == PackageManager.PERMISSION_GRANTED;
        } else { // Android 7-11
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                    == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    private void requestRequiredPermissions() {
        List<String> permissions = new ArrayList<>();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Android 12+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else { // Android 7-11
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }
        
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, 
                permissions.toArray(new String[0]), 
                REQUEST_LOCATION_PERMISSION);
        }
    }
    
    private void setupClickListeners() {
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (BluetoothConnectionManager.getInstance().isConnected()) {
                    BluetoothConnectionManager.getInstance().disconnect();
                    updateConnectionStatus();
                } else {
                    startScan();
                }
            }
        });
        
        saveWeightButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    int newWeight = Integer.parseInt(weightInput.getText().toString());
                    if (newWeight >= 40 && newWeight <= 150) {
                        userWeight = newWeight;
                        saveUserWeight();
                        showToast("Poids sauvegardé: " + userWeight + " kg");
                        
                        // Informer MainActivity du changement
                        Intent intent = new Intent("com.cyclingapp.indoor.WEIGHT_CHANGED");
                        intent.putExtra("weight", userWeight);
                        sendBroadcast(intent);
                    } else {
                        showToast("Poids doit être entre 40 et 150 kg");
                        weightInput.setText(String.valueOf(userWeight));
                    }
                } catch (NumberFormatException e) {
                    showToast("Poids invalide");
                    weightInput.setText(String.valueOf(userWeight));
                }
            }
        });
    }
    
    private void updateConnectionStatus() {
        if (BluetoothConnectionManager.getInstance().isConnected()) {
            statusText.setText("Connecté");
            statusText.setTextColor(ContextCompat.getColor(this, R.color.green));
            connectButton.setText("Déconnecter");
        } else {
            statusText.setText("Déconnecté");
            statusText.setTextColor(ContextCompat.getColor(this, R.color.red));
            connectButton.setText("Connecter");
        }
    }
    
    private void startScan() {
        if (!hasRequiredPermissions()) {
            showToast("Permissions Bluetooth requises");
            requestRequiredPermissions();
            return;
        }
        
        if (!bluetoothAdapter.isEnabled()) {
            showToast("Bluetooth désactivé");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return;
        }
        
        if (bluetoothLeScanner == null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bluetoothLeScanner == null) {
                showToast("Scanner Bluetooth non disponible");
                return;
            }
        }
        
        if (!scanning) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanning = false;
                    try {
                        bluetoothLeScanner.stopScan(leScanCallback);
                    } catch (Exception e) {
                        Log.e(TAG, "Erreur arrêt scan: " + e.getMessage());
                    }
                    updateConnectionStatus();
                }
            }, SCAN_PERIOD);
            
            scanning = true;
            statusText.setText("Recherche...");
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark));
            connectButton.setText("Arrêter");
            
            try {
                bluetoothLeScanner.startScan(leScanCallback);
                Log.d(TAG, "Scan BLE démarré");
            } catch (SecurityException e) {
                Log.e(TAG, "Erreur permission: " + e.getMessage());
                showToast("Erreur de permission");
                scanning = false;
            }
        } else {
            scanning = false;
            try {
                bluetoothLeScanner.stopScan(leScanCallback);
            } catch (Exception e) {
                Log.e(TAG, "Erreur arrêt scan: " + e.getMessage());
            }
            updateConnectionStatus();
        }
    }
    
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            try {
                String deviceName = device.getName();
                
                if (deviceName != null && deviceName.toLowerCase().contains("stages")) {
                    Log.d(TAG, "Capteur Stages trouvé: " + deviceName);
                    scanning = false;
                    try {
                        bluetoothLeScanner.stopScan(leScanCallback);
                    } catch (Exception e) {
                        Log.e(TAG, "Erreur arrêt scan: " + e.getMessage());
                    }
                    
                    // Connecter via le BluetoothConnectionManager
                    BluetoothConnectionManager.getInstance().connect(SettingsActivity.this, device);
                    
                    // Attendre un peu puis mettre à jour le statut
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            updateConnectionStatus();
                            showToast("Connexion réussie !");
                        }
                    }, 2000);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Erreur permission: " + e.getMessage());
            }
        }
        
        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Erreur scan: " + errorCode);
            scanning = false;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateConnectionStatus();
                    showToast("Erreur de scan Bluetooth");
                }
            });
        }
    };
    
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateConnectionStatus();
    }
}
