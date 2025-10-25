package com.cyclingapp.indoor;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.content.res.ColorStateList;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements BluetoothConnectionManager.BluetoothDataListener {
    private boolean pendingBluetoothActivation = false;
    
    private static final String TAG = "CyclingApp";
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 100;
    private static final long SCAN_PERIOD = 10000; // 10 secondes
    
    // Composants UI
    private TextView statusText;
    private TextView powerValue;
    private TextView cadenceValue;
    private TextView speedValue;
    private TextView distanceValue;
    private TextView caloriesValue;
    private TextView avgSpeedValue;
    private Switch bluetoothSwitch;
    private Button sessionButton;
    private Button historyButton;
    private ImageButton calibrationButton;
    
    // Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean scanning = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    // Données de cyclisme
    private int currentPower = 0;
    private int currentCadence = 0;
    private double currentSpeed = 0.0;
    private double totalDistance = 0.0;
    private double totalCalories = 0.0;
    private double averageSpeed = 0.0;
    private long lastUpdateTime = 0;
    private long sessionStartTime = 0;
    
    // Gestion de la persistance des valeurs (éviter sauts à 0)
    private long lastValidDataTime = 0;
    private static final long DATA_TIMEOUT_MS = 3000; // 3 secondes
    
    // Gestion de session
    private Session currentSession = null;
    private boolean isRecording = false;
    private SessionDatabaseHelper dbHelper;
    private double totalPower = 0.0;
    private int powerSampleCount = 0;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        dbHelper = new SessionDatabaseHelper(this);
        initializeViews();
        initializeBluetooth();
        setupClickListeners();
        
        // Charger la calibration du capteur
        BluetoothConnectionManager.getInstance().loadCalibration(this);
        
        // S'enregistrer comme listener pour les données Bluetooth
        BluetoothConnectionManager.getInstance().addListener(this);
        
        // Connexion automatique si Bluetooth activé
        if (bluetoothSwitch.isChecked()) {
            startAutoConnect();
    }
    }

    private void initializeViews() {
        statusText = findViewById(R.id.statusText);
        powerValue = findViewById(R.id.powerValue);
        cadenceValue = findViewById(R.id.cadenceValue);
        speedValue = findViewById(R.id.speedValue);
        distanceValue = findViewById(R.id.distanceValue);
        caloriesValue = findViewById(R.id.caloriesValue);
        avgSpeedValue = findViewById(R.id.avgSpeedValue);
        bluetoothSwitch = findViewById(R.id.bluetoothSwitch);
        sessionButton = findViewById(R.id.sessionButton);
        historyButton = findViewById(R.id.historyButton);
        calibrationButton = findViewById(R.id.calibrationButton);
        
        // Initialiser l'état du switch selon la connexion
        bluetoothSwitch.setChecked(BluetoothConnectionManager.getInstance().isConnected());
        
        // Initialiser le style du bouton session (vert = démarrer)
        sessionButton.setBackgroundColor(ContextCompat.getColor(this, R.color.green));
    }
    
    private void initializeBluetooth() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter == null) {
            showToast("Bluetooth non disponible");
            bluetoothSwitch.setEnabled(false);
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            // Demander l'activation du Bluetooth
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            pendingBluetoothActivation = true;
            startActivityForResult(enableBtIntent, REQUEST_BLUETOOTH_PERMISSIONS);
            showToast("Activation Bluetooth demandée");
            bluetoothSwitch.setEnabled(false);
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
    }
    
    private void setupClickListeners() {
        // Switch Bluetooth
        bluetoothSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!hasRequiredPermissions()) {
                    requestRequiredPermissions();
                    bluetoothSwitch.setChecked(false);
                    return;
                }
                if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    pendingBluetoothActivation = true;
                    startActivityForResult(enableBtIntent, REQUEST_BLUETOOTH_PERMISSIONS);
                    showToast("Activation Bluetooth demandée");
                    bluetoothSwitch.setChecked(false);
                    return;
                }
                startAutoConnect();
            } else {
                BluetoothConnectionManager.getInstance().disconnect();
                stopScan();
            }
        });

        // Bouton Session
        sessionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRecording) {
                    stopSession();
                } else {
                    startSession();
                }
            }
        });

        // Bouton Historique
        historyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SessionHistoryActivity.class);
                startActivity(intent);
            }
        });

        // Bouton Calibration
        calibrationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CalibrationActivity.class);
                startActivity(intent);
            }
        });
    }

        
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                bluetoothSwitch.setEnabled(true);
                if (pendingBluetoothActivation) {
                    bluetoothSwitch.setChecked(true);
                    startAutoConnect();
                    pendingBluetoothActivation = false;
                }
                showToast("Bluetooth activé");
            } else {
                bluetoothSwitch.setEnabled(true);
                bluetoothSwitch.setChecked(false);
                showToast("Bluetooth non activé");
            }
        }
    }
    
    // Gestion Bluetooth et Scan
    
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
                REQUEST_BLUETOOTH_PERMISSIONS);
        }
    }
    
    private void startAutoConnect() {
        if (!hasRequiredPermissions()) {
            requestRequiredPermissions();
            return;
        }
        
        if (BluetoothConnectionManager.getInstance().isConnected()) {
            updateConnectionStatus(true);
            return;
        }
        
        updateConnectionStatus(false, "Recherche...");
        startScan();
    }
    
    private void startScan() {
        if (scanning) return;
        
        if (bluetoothLeScanner == null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bluetoothLeScanner == null) {
                showToast("Scanner Bluetooth non disponible");
                bluetoothSwitch.setChecked(false);
                return;
            }
        }
        
        // Arrêter le scan après SCAN_PERIOD
        handler.postDelayed(() -> {
            stopScan();
            if (!BluetoothConnectionManager.getInstance().isConnected()) {
                showToast("Aucun capteur Stages trouvé");
                bluetoothSwitch.setChecked(false);
            }
        }, SCAN_PERIOD);
        
        scanning = true;
        try {
            bluetoothLeScanner.startScan(leScanCallback);
            Log.d(TAG, "Scan BLE démarré");
        } catch (SecurityException e) {
            Log.e(TAG, "Erreur permission scan: " + e.getMessage());
            showToast("Erreur permission Bluetooth");
            scanning = false;
            bluetoothSwitch.setChecked(false);
        }
    }
    
    private void stopScan() {
        if (!scanning) return;
        
        scanning = false;
        try {
            if (bluetoothLeScanner != null) {
                bluetoothLeScanner.stopScan(leScanCallback);
                Log.d(TAG, "Scan BLE arrêté");
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Erreur arrêt scan: " + e.getMessage());
        }
    }
    
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            try {
                String deviceName = device.getName();
                
                if (deviceName != null && deviceName.toLowerCase().contains("stages")) {
                    Log.i(TAG, "Capteur Stages trouvé: " + deviceName);
                    stopScan();
                    
                    // Connecter via le BluetoothConnectionManager
                    BluetoothConnectionManager.getInstance().connect(MainActivity.this, device);
                    
                    // Attendre connexion
                    handler.postDelayed(() -> {
                        if (BluetoothConnectionManager.getInstance().isConnected()) {
                            updateConnectionStatus(true);
                            showToast("Connecté à " + deviceName);
                        }
                    }, 2000);
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Erreur permission device: " + e.getMessage());
            }
        }
        
        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Scan failed: " + errorCode);
            scanning = false;
            showToast("Erreur scan Bluetooth");
            bluetoothSwitch.setChecked(false);
        }
    };
    @Override
    public void onDataReceived(int power, int cadence) {
        long currentTime = System.currentTimeMillis();
        
        // Vérifier si on a des données valides
        boolean hasValidData = (power > 0 || cadence > 0);
        
        if (hasValidData) {
            lastValidDataTime = currentTime;
            
            // Mettre à jour seulement les valeurs > 0
            if (power > 0) {
                currentPower = power;
            }
            if (cadence > 0) {
                currentCadence = cadence;
            }
        } else {
            // Aucune donnée valide : vérifier le timeout
            if (currentTime - lastValidDataTime > DATA_TIMEOUT_MS) {
                // Timeout : considérer comme arrêt
                currentPower = 0;
                currentCadence = 0;
                currentSpeed = 0.0;
            }
            // Sinon, garder les dernières valeurs
        }
        
        if (lastUpdateTime > 0) {
            double deltaTime = (currentTime - lastUpdateTime) / 1000.0;
            
            // Calculer vitesse (ne retournera 0 que si timeout atteint)
            if (currentPower > 0 || currentCadence > 0) {
                currentSpeed = calculateSpeed(currentPower, currentCadence);
            }
            
            // Calcul distance
            double distance = currentSpeed * deltaTime / 3600.0;
            totalDistance += distance;
            
            // Calcul calories
            double calories = calculateCalories(currentPower, deltaTime);
            totalCalories += calories;
            
            // Calcul vitesse moyenne
            if (sessionStartTime > 0) {
                double sessionDuration = (currentTime - sessionStartTime) / 3600000.0;
                if (sessionDuration > 0) {
                    averageSpeed = totalDistance / sessionDuration;
                }
            }
            
            // Mise à jour session si enregistrement actif
            if (isRecording && currentSession != null) {
                totalPower += currentPower;
                powerSampleCount++;
            }
        } else {
            // Première mise à jour
            lastValidDataTime = currentTime;
        }
        
        lastUpdateTime = currentTime;
        updateUI();
    }
    
    @Override
    public void onConnectionStateChanged(boolean isConnected) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isConnected) {
                    statusText.setText("Connecté");
                    statusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                } else {
                    statusText.setText("Déconnecté");
                    statusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                }
            }
        });
    }
    
    private double calculateSpeed(int power, int cadence) {
        if (cadence == 0 || power == 0) {
            return 0.0;
        }
        
        // Formule simplifiée basée uniquement sur puissance et cadence
        // Estimation: vitesse ≈ racine cubique de la puissance × facteur de cadence
        double speedFromPower = Math.pow(power / 3.0, 1.0 / 3.0);
        double cadenceInfluence = Math.min(cadence / 90.0, 1.2);
        return speedFromPower * cadenceInfluence;
    }
    
    private double calculateCalories(int power, double deltaTime) {
        // Calories = Puissance (watts) × temps (secondes) × 0.0002388
        return power * deltaTime * 0.2388;
    }
    
    private void updateUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Limiter les valeurs pour l'affichage
                int displayCadence = Math.min(currentCadence, 200); // Max 200 RPM
                double displaySpeed = Math.max(0, currentSpeed); // Jamais négatif
                
                powerValue.setText(String.format("%d W", currentPower));
                cadenceValue.setText(String.format("%d RPM", displayCadence));
                speedValue.setText(String.format("%.1f km/h", displaySpeed));
                distanceValue.setText(String.format("%.2f km", totalDistance));
                caloriesValue.setText(String.format("%.0f kcal", totalCalories));
                avgSpeedValue.setText(String.format("%.1f km/h", averageSpeed));
            }
        });
    }
    
    private void startSession() {
        currentSession = new Session();
        currentSession.setStartTime(System.currentTimeMillis());
        sessionStartTime = System.currentTimeMillis();
        isRecording = true;
        
        // Réinitialiser les métriques
        totalDistance = 0.0;
        totalCalories = 0.0;
        averageSpeed = 0.0;
        totalPower = 0.0;
        powerSampleCount = 0;
        lastUpdateTime = 0;
        
        runOnUiThread(() -> {
            sessionButton.setText("Arrêter Session");
            sessionButton.setBackgroundResource(R.drawable.bg_button_red);
            Toast.makeText(MainActivity.this, "Session démarrée", Toast.LENGTH_SHORT).show();
        });
    }
    
    private void stopSession() {
        if (currentSession != null) {
            currentSession.setEndTime(System.currentTimeMillis());
            currentSession.setDistance(totalDistance);
            currentSession.setCalories(totalCalories);
            
            // Calculer puissance moyenne
            if (powerSampleCount > 0) {
                double avgPower = totalPower / powerSampleCount;
                currentSession.setAvgPower(avgPower);
            }
            
            currentSession.setAvgSpeed(averageSpeed);
            
            // Sauvegarder dans la base de données
            long id = dbHelper.saveSession(currentSession);
            if (id != -1) {
                showToast("Session enregistrée");
            } else {
                showToast("Erreur d'enregistrement");
            }
        }
        
        isRecording = false;
        currentSession = null;
        runOnUiThread(() -> {
            sessionButton.setText("Démarrer Session");
            sessionButton.setBackgroundResource(R.drawable.bg_button_green);
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Se désinscrire comme listener
        BluetoothConnectionManager.getInstance().removeListener(this);
        
        // Arrêter le scan Bluetooth si actif
        if (scanning) {
            stopScan();
        }
        
        // Fermer la base de données
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
    }
    
    // Méthodes utilitaires
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    private void updateConnectionStatus(boolean connected) {
        updateConnectionStatus(connected, null);
    }
    
    private void updateConnectionStatus(boolean connected, String customMessage) {
        runOnUiThread(() -> {
            if (customMessage != null) {
                statusText.setText(customMessage);
                statusText.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            } else if (connected) {
                statusText.setText("Connecté");
                statusText.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            } else {
                statusText.setText("Déconnecté");
                statusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            }
        });
    }
}
