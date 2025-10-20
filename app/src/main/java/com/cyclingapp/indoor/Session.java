package com.cyclingapp.indoor;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Session {
    private long id;
    private long startTime;
    private long endTime;
    private double distance;
    private double calories;
    private double avgSpeed;
    private double avgPower;
    private int userWeight;
    
    public Session() {
        this.startTime = System.currentTimeMillis();
    }
    
    public Session(long id, long startTime, long endTime, double distance, 
                   double calories, double avgSpeed, double avgPower, int userWeight) {
        this.id = id;
        this.startTime = startTime;
        this.endTime = endTime;
        this.distance = distance;
        this.calories = calories;
        this.avgSpeed = avgSpeed;
        this.avgPower = avgPower;
        this.userWeight = userWeight;
    }
    
    // Getters
    public long getId() { return id; }
    public long getStartTime() { return startTime; }
    public long getEndTime() { return endTime; }
    public double getDistance() { return distance; }
    public double getCalories() { return calories; }
    public double getAvgSpeed() { return avgSpeed; }
    public double getAvgPower() { return avgPower; }
    public int getUserWeight() { return userWeight; }
    
    // Setters
    public void setId(long id) { this.id = id; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public void setDistance(double distance) { this.distance = distance; }
    public void setCalories(double calories) { this.calories = calories; }
    public void setAvgSpeed(double avgSpeed) { this.avgSpeed = avgSpeed; }
    public void setAvgPower(double avgPower) { this.avgPower = avgPower; }
    public void setUserWeight(int userWeight) { this.userWeight = userWeight; }
    
    // Méthodes utilitaires
    public long getDuration() {
        return endTime - startTime;
    }
    
    public String getDurationFormatted() {
        long durationMs = getDuration();
        long seconds = (durationMs / 1000) % 60;
        long minutes = (durationMs / (1000 * 60)) % 60;
        long hours = (durationMs / (1000 * 60 * 60));
        
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%dh %02dm", hours, minutes);
        } else {
            return String.format(Locale.getDefault(), "%dm %02ds", minutes, seconds);
        }
    }
    
    public String getDateFormatted() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        return sdf.format(new Date(startTime));
    }
    
    public String getShortDateFormatted() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        return sdf.format(new Date(startTime));
    }
}
