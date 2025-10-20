package com.cyclingapp.indoor;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class SessionHistoryActivity extends AppCompatActivity {
    
    private RecyclerView recyclerView;
    private SessionHistoryAdapter adapter;
    private SessionDatabaseHelper dbHelper;
    private TextView emptyView;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_history);
        
        // Activer le bouton retour
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Historique des sessions");
        }
        
        dbHelper = new SessionDatabaseHelper(this);
        
        recyclerView = findViewById(R.id.sessionsRecyclerView);
        emptyView = findViewById(R.id.emptyView);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        loadSessions();
    }
    
    private void loadSessions() {
        List<Session> sessions = dbHelper.getAllSessions();
        
        if (sessions.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            
            adapter = new SessionHistoryAdapter(sessions, new SessionHistoryAdapter.OnSessionClickListener() {
                @Override
                public void onDeleteClick(Session session) {
                    dbHelper.deleteSession(session.getId());
                    loadSessions(); // Recharger la liste
                }
            });
            recyclerView.setAdapter(adapter);
        }
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
    protected void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}
