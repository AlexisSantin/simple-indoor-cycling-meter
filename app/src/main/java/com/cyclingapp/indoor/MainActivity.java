package com.cyclingapp.indoor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements BluetoothConnectionManager.BluetoothDataListener {
    
    private static final String TAG = "CyclingApp";
    
    // Composants UI
    private TextView statusText;
    private TextView powerValue;
    private TextView cadenceValue;
    private TextView speedValue;
    private TextView distanceValue;
    private TextView caloriesValue;
    private TextView avgSpeedValue;
    private Button sessionButton;
    private Button historyButton;
    private ImageButton settingsButton;
    
    // Données de cyclisme
    private int currentPower = 0;
    private int currentCadence = 0;
    private double currentSpeed = 0.0;
    private double totalDistance = 0.0;
    private double totalCalories = 0.0;
    private double averageSpeed = 0.0;
    private long lastUpdateTime = 0;
    private long sessionStartTime = 0;
    
    // Gestion de session
    private Session currentSession = null;
    private boolean isRecording = false;
    private SessionDatabaseHelper dbHelper;
    private double totalPower = 0.0;
    private int powerSampleCount = 0;
    
    // Paramètres pour calcul vitesse
    private double speedCoefficient = 0.0053;
    private int userWeight = 75; // kg, poids par défaut
    private static final String PREFS_NAME = "CyclingAppPrefs";
    private static final String PREF_WEIGHT = "user_weight";
    
    // Handler pour mises à jour UI
    private Handler handler = new Handler(Looper.getMainLooper());
    
    // BroadcastReceiver pour changements de poids
    private BroadcastReceiver weightChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.cyclingapp.indoor.WEIGHT_CHANGED".equals(intent.getAction())) {
                int newWeight = intent.getIntExtra("weight", 75);
                userWeight = newWeight;
                updateSpeedCoefficient();
                Log.d(TAG, "Poids mis à jour: " + userWeight + " kg");
            }
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        dbHelper = new SessionDatabaseHelper(this);
        loadUserWeight();
        initializeViews();
        setupClickListeners();
        
        // S'enregistrer comme listener pour les données Bluetooth
        BluetoothConnectionManager.getInstance().addListener(this);
        
        // Enregistrer le receiver pour les changements de poids
        IntentFilter filter = new IntentFilter("com.cyclingapp.indoor.WEIGHT_CHANGED");
        registerReceiver(weightChangedReceiver, filter);
    }
    
    private void loadUserWeight() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        userWeight = prefs.getInt(PREF_WEIGHT, 75);
        updateSpeedCoefficient();
    }
    
    private void updateSpeedCoefficient() {
        speedCoefficient = 0.0053 * Math.pow(userWeight / 75.0, 0.33);
        Log.d(TAG, String.format("Poids: %d kg, Coefficient: %.5f", userWeight, speedCoefficient));
    }
    
    private void initializeViews() {
        statusText = findViewById(R.id.statusText);
        powerValue = findViewById(R.id.powerValue);
        cadenceValue = findViewById(R.id.cadenceValue);
        speedValue = findViewById(R.id.speedValue);
        distanceValue = findViewById(R.id.distanceValue);
        caloriesValue = findViewById(R.id.caloriesValue);
        avgSpeedValue = findViewById(R.id.avgSpeedValue);
        sessionButton = findViewById(R.id.sessionButton);
        historyButton = findViewById(R.id.historyButton);
        settingsButton = findViewById(R.id.settingsButton);
    }
    
    private void setupClickListeners() {
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
        
        historyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SessionHistoryActivity.class);
                startActivity(intent);
            }
        });
        
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            }
        });
    }
    
    // Implémentation de BluetoothDataListener
    @Override
    public void onDataReceived(int power, int cadence) {
        currentPower = power;
        currentCadence = cadence;
        
        long currentTime = System.currentTimeMillis();
        
        if (lastUpdateTime > 0) {
            double deltaTime = (currentTime - lastUpdateTime) / 1000.0;
            
            // Calcul vitesse
            currentSpeed = calculateSpeed(currentPower, currentCadence);
            
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
        
        double speedFromPower = Math.pow(power / speedCoefficient, 1.0 / 3.0);
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
                powerValue.setText(String.format("%d W", currentPower));
                cadenceValue.setText(String.format("%d RPM", currentCadence));
                speedValue.setText(String.format("%.1f km/h", currentSpeed));
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
        
        sessionButton.setText("Arrêter Session");
        Toast.makeText(this, "Session démarrée", Toast.LENGTH_SHORT).show();
    }
    
    private void stopSession() {
        if (currentSession != null) {
            currentSession.setEndTime(System.currentTimeMillis());
            currentSession.setDistance(totalDistance);
            currentSession.setCalories(totalCalories);
            currentSession.setUserWeight(userWeight);
            
            // Calculer puissance moyenne
            if (powerSampleCount > 0) {
                double avgPower = totalPower / powerSampleCount;
                currentSession.setAvgPower(avgPower);
            }
            
            currentSession.setAvgSpeed(averageSpeed);
            
            // Sauvegarder dans la base de données
            long id = dbHelper.saveSession(currentSession);
            if (id != -1) {
                Toast.makeText(this, "Session enregistrée", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Erreur d'enregistrement", Toast.LENGTH_SHORT).show();
            }
        }
        
        isRecording = false;
        currentSession = null;
        sessionButton.setText("Démarrer Session");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Se désinscrire comme listener
        BluetoothConnectionManager.getInstance().removeListener(this);
        
        // Désinscrire le receiver
        unregisterReceiver(weightChangedReceiver);
        
        // Fermer la base de données
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Recharger le poids au cas où il aurait changé
        loadUserWeight();
    }
}
