package com.example.project3_aadhika8_sguragai.sense;

import com.example.project3_aadhika8_sguragai.data.*;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Service for communicating with OpenAI API for the financial chat assistant.
 */
public class OpenAIService {

    // The prose-answer search path was removed deliberately. It sent the user's entire
    // transaction history to OpenAI on every query and returned a paragraph that could not be
    // filtered, corrected, or drilled into. Find mode now compiles queries to local SQL, and
    // the only thing an LLM is asked for is a translation (see LlmFilterTranslator).
    // sendMessage below remains for Ask mode, which does send expense context — the Search
    // screen says so on screen.

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final String apiKey;
    private final Handler mainHandler;

    public interface ChatCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    public OpenAIService(String apiKey) {
        this.apiKey = apiKey;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Sends a message to the AI with expense context.
     * @param userMessage The user's question
     * @param expenseContext Full expense data for analysis
     * @param callback Callback for response
     */
    public void sendMessage(String userMessage, String expenseContext, ChatCallback callback) {
        if (apiKey == null || apiKey.isEmpty()) {
            mainHandler.post(() -> callback.onError("API key not configured. Please add OPENAI_API_KEY to local.properties"));
            return;
        }

        try {
            JSONObject requestJson = buildChatRequest(userMessage, expenseContext);
            RequestBody body = RequestBody.create(requestJson.toString(), JSON);

            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    mainHandler.post(() -> callback.onError("Network error: " + e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            String responseBody = response.body().string();
                            String aiResponse = parseResponse(responseBody);
                            mainHandler.post(() -> callback.onSuccess(aiResponse));
                        } catch (JSONException e) {
                            mainHandler.post(() -> callback.onError("Error parsing response"));
                        }
                    } else {
                        String errorMsg = response.body() != null ? response.body().string() : "Unknown error";
                        mainHandler.post(() -> callback.onError("API error: " + response.code() + " - " + errorMsg));
                    }
                }
            });

        } catch (JSONException e) {
            mainHandler.post(() -> callback.onError("Error creating request"));
        }
    }

    /**
     * Performs a natural language search using AI.
     */
    private JSONObject buildChatRequest(String userMessage, String expenseContext) throws JSONException {
        JSONObject request = new JSONObject();
        request.put("model", "gpt-4o-mini");
        request.put("max_tokens", 800);
        request.put("temperature", 0.7);

        JSONArray messages = new JSONArray();

        // System message with detailed instructions
        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", buildChatSystemPrompt(expenseContext));
        messages.put(systemMessage);

        // User message
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.put(userMsg);

        request.put("messages", messages);

        return request;
    }

    private String buildChatSystemPrompt(String expenseContext) {
        return "You are Budget Buddy, a smart personal finance assistant. You have FULL ACCESS to the user's expense data shown below.\n\n" +
               "YOUR CAPABILITIES:\n" +
               "1. Analyze individual transactions to find unnecessary/wasteful spending\n" +
               "2. Compare spending patterns across time periods\n" +
               "3. Identify the biggest expenses and spending categories\n" +
               "4. Provide specific, data-driven advice\n" +
               "5. Calculate totals, averages, and comparisons\n\n" +
               
               "IMPORTANT GUIDELINES:\n" +
               "- ALWAYS reference specific transactions by name and amount when answering\n" +
               "- When asked about 'unnecessary' spending, consider: entertainment, dining out, subscriptions, impulse purchases\n" +
               "- Be specific with numbers - don't just say 'you spent a lot', say 'you spent $X on Y'\n" +
               "- Keep responses concise but informative (2-4 short paragraphs)\n" +
               "- Use bullet points for lists\n" +
               "- Be friendly and encouraging, not judgmental\n" +
               "- If the data doesn't contain what the user asks about, say so clearly\n\n" +
               
               "USER'S EXPENSE DATA:\n" +
               "```\n" + expenseContext + "\n```\n\n" +
               
               "Remember: You can see ALL their transactions. Use this data to give personalized, specific answers.";
    }

    private String parseResponse(String responseBody) throws JSONException {
        JSONObject json = new JSONObject(responseBody);
        JSONArray choices = json.getJSONArray("choices");
        if (choices.length() > 0) {
            JSONObject choice = choices.getJSONObject(0);
            JSONObject message = choice.getJSONObject("message");
            return message.getString("content").trim();
        }
        throw new JSONException("No choices in response");
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isEmpty();
    }
}
