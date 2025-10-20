package com.cyclingapp.indoor;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class SessionHistoryAdapter extends RecyclerView.Adapter<SessionHistoryAdapter.SessionViewHolder> {
    
    private List<Session> sessions;
    private OnSessionClickListener listener;
    
    public interface OnSessionClickListener {
        void onDeleteClick(Session session);
    }
    
    public SessionHistoryAdapter(List<Session> sessions, OnSessionClickListener listener) {
        this.sessions = sessions;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session, parent, false);
        return new SessionViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        Session session = sessions.get(position);
        
        holder.dateText.setText(session.getDateFormatted());
        holder.durationText.setText(session.getDurationFormatted());
        holder.distanceText.setText(String.format(Locale.getDefault(), "%.2f km", session.getDistance()));
        holder.caloriesText.setText(String.format(Locale.getDefault(), "%.0f kcal", session.getCalories()));
        holder.avgSpeedText.setText(String.format(Locale.getDefault(), "%.1f km/h", session.getAvgSpeed()));
        holder.avgPowerText.setText(String.format(Locale.getDefault(), "%.0f W", session.getAvgPower()));
        
        holder.deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onDeleteClick(session);
                }
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return sessions.size();
    }
    
    static class SessionViewHolder extends RecyclerView.ViewHolder {
        TextView dateText;
        TextView durationText;
        TextView distanceText;
        TextView caloriesText;
        TextView avgSpeedText;
        TextView avgPowerText;
        Button deleteButton;
        
        public SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.sessionDate);
            durationText = itemView.findViewById(R.id.sessionDuration);
            distanceText = itemView.findViewById(R.id.sessionDistance);
            caloriesText = itemView.findViewById(R.id.sessionCalories);
            avgSpeedText = itemView.findViewById(R.id.sessionAvgSpeed);
            avgPowerText = itemView.findViewById(R.id.sessionAvgPower);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }
}
