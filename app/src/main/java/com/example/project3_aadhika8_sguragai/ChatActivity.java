package com.example.project3_aadhika8_sguragai;

import com.example.project3_aadhika8_sguragai.data.*;
import com.example.project3_aadhika8_sguragai.sense.OpenAIService;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView recyclerMessages;
    private EditText editMessage;
    private FloatingActionButton fabSend;
    private LinearLayout layoutTyping;
    private BottomNavigationView bottomNavigation;

    private ChatMessageAdapter adapter;
    private AppDatabase db;
    private ExpenseDao expenseDao;
    private ChatMessageDao chatMessageDao;
    private OpenAIService openAIService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        initViews();
        initDatabase();
        setupRecyclerView();
        setupClickListeners();
        setupBottomNavigation();

        loadChatHistory();
        showWelcomeMessageIfNeeded();
    }

    private void initViews() {
        recyclerMessages = findViewById(R.id.recyclerMessages);
        editMessage = findViewById(R.id.editMessage);
        fabSend = findViewById(R.id.fabSend);
        layoutTyping = findViewById(R.id.layoutTyping);
        bottomNavigation = findViewById(R.id.bottomNavigation);
    }

    private void initDatabase() {
        db = AppDatabase.getInstance(getApplicationContext());
        expenseDao = db.expenseDao();
        chatMessageDao = db.chatMessageDao();

        // Initialize OpenAI service with API key from BuildConfig
        String apiKey = BuildConfig.OPENAI_API_KEY;
        openAIService = new OpenAIService(apiKey);
    }

    private void setupRecyclerView() {
        int userBgColor = getColor(R.color.primary);
        int aiBgColor = getColor(R.color.surface_variant);
        int userTextColor = getColor(R.color.on_primary);
        int aiTextColor = getColor(R.color.text_primary);

        adapter = new ChatMessageAdapter(userBgColor, aiBgColor, userTextColor, aiTextColor);
        
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerMessages.setLayoutManager(layoutManager);
        recyclerMessages.setAdapter(adapter);
    }

    private void setupClickListeners() {
        fabSend.setOnClickListener(v -> sendMessage());

        editMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });
    }

    private void setupBottomNavigation() {
        bottomNavigation.setSelectedItemId(R.id.nav_chat);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_charts) {
                startActivity(new Intent(this, ChartsActivity.class));
                return true;
            } else if (id == R.id.nav_chat) {
                return true;
            } else if (id == R.id.nav_summary) {
                startActivity(new Intent(this, SummaryActivity.class));
                return true;
            }
            return false;
        });
    }

    private void loadChatHistory() {
        List<ChatMessage> messages = chatMessageDao.getAllMessages();
        adapter.setMessages(messages);
        scrollToBottom();
    }

    private void showWelcomeMessageIfNeeded() {
        if (chatMessageDao.getMessageCount() == 0) {
            String welcome = "Hi! I'm your Budget Buddy AI assistant. 👋\n\n" +
                    "I have access to all your expense data and can help you:\n\n" +
                    "• Analyze your spending patterns\n" +
                    "• Find unnecessary expenses\n" +
                    "• Compare spending across categories\n" +
                    "• Give personalized savings tips\n\n" +
                    "Try asking me:\n" +
                    "\"What was my biggest expense this month?\"\n" +
                    "\"Where am I overspending?\"\n" +
                    "\"How much did I spend on food?\"";
            
            ChatMessage welcomeMsg = new ChatMessage(welcome, false);
            chatMessageDao.insert(welcomeMsg);
            adapter.addMessage(welcomeMsg);
            scrollToBottom();
        }
    }

    private void sendMessage() {
        String text = editMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        // Clear input
        editMessage.setText("");

        // Add user message
        ChatMessage userMessage = new ChatMessage(text, true);
        chatMessageDao.insert(userMessage);
        adapter.addMessage(userMessage);
        scrollToBottom();

        // Show typing indicator
        layoutTyping.setVisibility(View.VISIBLE);
        fabSend.setEnabled(false);

        // Build comprehensive expense context with ALL transaction data
        String expenseContext = buildDetailedExpenseContext();

        // Send to AI
        openAIService.sendMessage(text, expenseContext, new OpenAIService.ChatCallback() {
            @Override
            public void onSuccess(String response) {
                runOnUiThread(() -> {
                    layoutTyping.setVisibility(View.GONE);
                    fabSend.setEnabled(true);

                    ChatMessage aiMessage = new ChatMessage(response, false);
                    chatMessageDao.insert(aiMessage);
                    adapter.addMessage(aiMessage);
                    scrollToBottom();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    layoutTyping.setVisibility(View.GONE);
                    fabSend.setEnabled(true);

                    String errorResponse = "I'm having trouble connecting right now. " +
                            "Please make sure you have an internet connection and try again.\n\n" +
                            "Error: " + error;
                    ChatMessage aiMessage = new ChatMessage(errorResponse, false);
                    chatMessageDao.insert(aiMessage);
                    adapter.addMessage(aiMessage);
                    scrollToBottom();
                });
            }
        });
    }

    /**
     * Builds a comprehensive expense context that includes ALL individual transactions.
     * This allows the AI to answer specific questions about individual expenses.
     */
    private String buildDetailedExpenseContext() {
        StringBuilder context = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat displayFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());

        Calendar cal = Calendar.getInstance();
        String today = sdf.format(cal.getTime());
        
        // Get current month range
        cal.set(Calendar.DAY_OF_MONTH, 1);
        String monthStart = sdf.format(cal.getTime());
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        String monthEnd = sdf.format(cal.getTime());

        // Get last month range
        cal = Calendar.getInstance();
        cal.add(Calendar.MONTH, -1);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        String lastMonthStart = sdf.format(cal.getTime());
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        String lastMonthEnd = sdf.format(cal.getTime());

        // === BUDGET INFO ===
        Double budget = db.budgetDao().getBudget();
        if (budget != null) {
            context.append("=== BUDGET ===\n");
            context.append("Monthly budget: $").append(String.format(Locale.getDefault(), "%.2f", budget)).append("\n\n");
        }

        // === THIS MONTH'S EXPENSES (Individual transactions) ===
        context.append("=== THIS MONTH'S EXPENSES (All Transactions) ===\n");
        List<Expense> thisMonthExpenses = expenseDao.getExpensesInRange(monthStart, monthEnd);
        
        if (thisMonthExpenses != null && !thisMonthExpenses.isEmpty()) {
            // Sort by amount (highest first) for analysis
            thisMonthExpenses.sort((a, b) -> Double.compare(b.amount, a.amount));
            
            double totalThisMonth = 0;
            for (Expense e : thisMonthExpenses) {
                totalThisMonth += e.amount;
                String catName = e.category != null ? e.category.name() : "OTHER";
                String note = (e.note != null && !e.note.isEmpty()) ? " - " + e.note : "";
                context.append(String.format(Locale.getDefault(), 
                    "• %s: %s - $%.2f (%s)%s\n",
                    e.date, e.title, e.amount, catName, note));
            }
            
            context.append(String.format(Locale.getDefault(), 
                "\nTotal this month: $%.2f (%d transactions)\n", totalThisMonth, thisMonthExpenses.size()));
            
            if (budget != null) {
                double remaining = budget - totalThisMonth;
                context.append(String.format(Locale.getDefault(), 
                    "Budget remaining: $%.2f", remaining));
                if (remaining < 0) {
                    context.append(" (OVER BUDGET by $" + String.format(Locale.getDefault(), "%.2f", Math.abs(remaining)) + ")");
                }
                context.append("\n");
            }
        } else {
            context.append("No expenses recorded this month.\n");
        }

        // === SPENDING BY CATEGORY THIS MONTH ===
        context.append("\n=== SPENDING BY CATEGORY (This Month) ===\n");
        for (ExpenseCategory cat : ExpenseCategory.values()) {
            double catTotal = expenseDao.getTotalByCategory(cat, monthStart, monthEnd);
            if (catTotal > 0) {
                String catName = cat.name().charAt(0) + cat.name().substring(1).toLowerCase();
                context.append(String.format(Locale.getDefault(), "• %s: $%.2f\n", catName, catTotal));
            }
        }

        // === LAST MONTH COMPARISON ===
        double lastMonthTotal = expenseDao.getTotalForRange(lastMonthStart, lastMonthEnd);
        if (lastMonthTotal > 0) {
            context.append("\n=== LAST MONTH ===\n");
            context.append(String.format(Locale.getDefault(), "Total last month: $%.2f\n", lastMonthTotal));
            
            double thisMonthTotal = expenseDao.getTotalForRange(monthStart, monthEnd);
            double diff = thisMonthTotal - lastMonthTotal;
            if (diff > 0) {
                context.append(String.format(Locale.getDefault(), 
                    "You're spending $%.2f MORE than last month\n", diff));
            } else if (diff < 0) {
                context.append(String.format(Locale.getDefault(), 
                    "You're spending $%.2f LESS than last month\n", Math.abs(diff)));
            }
        }

        // === TODAY'S SPENDING ===
        cal = Calendar.getInstance();
        today = sdf.format(cal.getTime());
        double todayTotal = expenseDao.getTotalForDay(today);
        List<Expense> todayExpenses = expenseDao.getExpensesForDay(today);
        
        context.append("\n=== TODAY ===\n");
        if (todayExpenses != null && !todayExpenses.isEmpty()) {
            for (Expense e : todayExpenses) {
                context.append(String.format(Locale.getDefault(), 
                    "• %s - $%.2f (%s)\n", e.title, e.amount, e.category != null ? e.category.name() : "OTHER"));
            }
            context.append(String.format(Locale.getDefault(), "Total today: $%.2f\n", todayTotal));
        } else {
            context.append("No spending today (no-spend day! 🎉)\n");
        }

        return context.toString();
    }

    private void scrollToBottom() {
        if (adapter.getItemCount() > 0) {
            recyclerMessages.post(() -> 
                recyclerMessages.smoothScrollToPosition(adapter.getItemCount() - 1));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        bottomNavigation.setSelectedItemId(R.id.nav_chat);
    }
}
