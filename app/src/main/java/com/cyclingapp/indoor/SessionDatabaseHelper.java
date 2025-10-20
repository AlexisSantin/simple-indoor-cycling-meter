package com.cyclingapp.indoor;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class SessionDatabaseHelper extends SQLiteOpenHelper {
    
    private static final String TAG = "SessionDB";
    private static final String DATABASE_NAME = "cycling_sessions.db";
    private static final int DATABASE_VERSION = 1;
    
    // Table et colonnes
    private static final String TABLE_SESSIONS = "sessions";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_START_TIME = "start_time";
    private static final String COLUMN_END_TIME = "end_time";
    private static final String COLUMN_DISTANCE = "distance";
    private static final String COLUMN_CALORIES = "calories";
    private static final String COLUMN_AVG_SPEED = "avg_speed";
    private static final String COLUMN_AVG_POWER = "avg_power";
    private static final String COLUMN_USER_WEIGHT = "user_weight";
    
    private static final String CREATE_TABLE = 
        "CREATE TABLE " + TABLE_SESSIONS + " (" +
        COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        COLUMN_START_TIME + " INTEGER NOT NULL, " +
        COLUMN_END_TIME + " INTEGER NOT NULL, " +
        COLUMN_DISTANCE + " REAL NOT NULL, " +
        COLUMN_CALORIES + " REAL NOT NULL, " +
        COLUMN_AVG_SPEED + " REAL NOT NULL, " +
        COLUMN_AVG_POWER + " REAL NOT NULL, " +
        COLUMN_USER_WEIGHT + " INTEGER NOT NULL" +
        ")";
    
    public SessionDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
        Log.d(TAG, "Base de données créée");
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SESSIONS);
        onCreate(db);
    }
    
    // Sauvegarder une session
    public long saveSession(Session session) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        values.put(COLUMN_START_TIME, session.getStartTime());
        values.put(COLUMN_END_TIME, session.getEndTime());
        values.put(COLUMN_DISTANCE, session.getDistance());
        values.put(COLUMN_CALORIES, session.getCalories());
        values.put(COLUMN_AVG_SPEED, session.getAvgSpeed());
        values.put(COLUMN_AVG_POWER, session.getAvgPower());
        values.put(COLUMN_USER_WEIGHT, session.getUserWeight());
        
        long id = db.insert(TABLE_SESSIONS, null, values);
        db.close();
        
        Log.d(TAG, "Session sauvegardée avec ID: " + id);
        return id;
    }
    
    // Récupérer toutes les sessions (triées par date décroissante)
    public List<Session> getAllSessions() {
        List<Session> sessions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        String query = "SELECT * FROM " + TABLE_SESSIONS + 
                      " ORDER BY " + COLUMN_START_TIME + " DESC";
        
        Cursor cursor = db.rawQuery(query, null);
        
        if (cursor.moveToFirst()) {
            do {
                Session session = new Session(
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_START_TIME)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_END_TIME)),
                    cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_DISTANCE)),
                    cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_CALORIES)),
                    cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_AVG_SPEED)),
                    cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_AVG_POWER)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_USER_WEIGHT))
                );
                sessions.add(session);
            } while (cursor.moveToNext());
        }
        
        cursor.close();
        db.close();
        
        Log.d(TAG, "Récupéré " + sessions.size() + " sessions");
        return sessions;
    }
    
    // Supprimer une session
    public void deleteSession(long id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_SESSIONS, COLUMN_ID + " = ?", 
                 new String[]{String.valueOf(id)});
        db.close();
        Log.d(TAG, "Session supprimée: " + id);
    }
    
    // Supprimer toutes les sessions
    public void deleteAllSessions() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_SESSIONS, null, null);
        db.close();
        Log.d(TAG, "Toutes les sessions supprimées");
    }
    
    // Obtenir le nombre de sessions
    public int getSessionCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_SESSIONS, null);
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();
        db.close();
        return count;
    }
}
