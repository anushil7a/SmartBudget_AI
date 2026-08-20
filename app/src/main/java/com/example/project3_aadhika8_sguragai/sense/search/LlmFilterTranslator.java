package com.example.project3_aadhika8_sguragai.sense.search;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;

import com.example.project3_aadhika8_sguragai.data.ExpenseCategory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Last-resort translator: when the local parser understood nothing, ask a model to turn the
 * query into a {@link SearchFilter}.
 *
 * <p>It sends the query string and the category enum names. <strong>It never sends
 * transactions.</strong> That is the whole reason this class translates rather than answers —
 * the old search path shipped the user's entire spending history to OpenAI to get a sentence
 * back. Here the model sees a phrase like "coffee runs before payday" and nothing else, and
 * the filter it returns is executed locally.
 *
 * <p>Everything in the response is validated field by field. An unparseable reply is
 * discarded and the UI says the query was not understood; there is no path by which model
 * output reaches SQL unchecked.
 */
public class LlmFilterTranslator {

    private static final String TAG = "LlmFilterTranslator";
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String MODEL = "gpt-4o-mini";

    public interface Result {
        void onFilter(SearchFilter filter);

        void onUnparseable();
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private final String apiKey;
    private final Context context;

    public LlmFilterTranslator(Context context, String apiKey) {
        this.context = context.getApplicationContext();
        this.apiKey = apiKey;
    }

    /**
     * Fires only when the local parser came up empty, a key is configured, and there is a
     * network. Any of those failing means {@code onUnparseable} — never a silent hang.
     */
    public boolean canRun(SearchFilter localResult) {
        return localResult != null
                && localResult.isEmpty()
                && apiKey != null
                && !apiKey.trim().isEmpty()
                && isOnline();
    }

    public void translate(String query, Result callback) {
        if (query == null || query.trim().isEmpty()) {
            callback.onUnparseable();
            return;
        }

        JSONObject body;
        try {
            body = buildRequest(query);
        } catch (Exception e) {
            Log.e(TAG, "could not build request", e);
            callback.onUnparseable();
            return;
        }

        Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "translation call failed", e);
                callback.onUnparseable();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful() || r.body() == null) {
                        callback.onUnparseable();
                        return;
                    }
                    SearchFilter parsed = validate(extractContent(r.body().string()));
                    if (parsed == null || parsed.isEmpty()) {
                        callback.onUnparseable();
                    } else {
                        callback.onFilter(parsed);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "translation response rejected", e);
                    callback.onUnparseable();
                }
            }
        });
    }

    private JSONObject buildRequest(String query) throws Exception {
        StringBuilder cats = new StringBuilder();
        for (ExpenseCategory c : ExpenseCategory.values()) {
            if (cats.length() > 0) {
                cats.append(", ");
            }
            cats.append(c.name());
        }

        String system = "You translate a personal-finance search phrase into a JSON filter. "
                + "Reply with JSON only, no prose. Schema: {\"categories\":[string],"
                + "\"startDate\":\"yyyy-MM-dd\"|null,\"endDate\":\"yyyy-MM-dd\"|null,"
                + "\"minAmount\":number|null,\"maxAmount\":number|null,"
                + "\"merchantTerms\":[string],\"sort\":\"DATE_DESC\"|\"AMOUNT_DESC\"|\"AMOUNT_ASC\","
                + "\"limit\":number|null}. "
                + "Valid categories: " + cats + ". Today is "
                + java.time.LocalDate.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".";

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", system));
        // Only the phrase. No expenses, no totals, no merchant history.
        messages.put(new JSONObject().put("role", "user").put("content", query));

        return new JSONObject()
                .put("model", MODEL)
                .put("messages", messages)
                .put("temperature", 0)
                .put("response_format", new JSONObject().put("type", "json_object"));
    }

    private String extractContent(String responseBody) throws Exception {
        return new JSONObject(responseBody)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
    }

    /**
     * Field-by-field validation. Anything unrecognised is dropped rather than trusted — the
     * result of this method is about to become SQL.
     */
    SearchFilter validate(String json) {
        SearchFilter f = new SearchFilter();
        JSONObject o;
        try {
            o = new JSONObject(json);
        } catch (Exception e) {
            return null;
        }

        JSONArray cats = o.optJSONArray("categories");
        if (cats != null) {
            for (int i = 0; i < cats.length(); i++) {
                String name = cats.optString(i, "").trim().toUpperCase(Locale.US);
                for (ExpenseCategory c : ExpenseCategory.values()) {
                    if (c.name().equals(name)) {
                        f.categories.add(c);
                    }
                }
            }
        }

        f.startDate = validDate(o.optString("startDate", null));
        f.endDate = validDate(o.optString("endDate", null));

        f.minAmount = validAmount(o.opt("minAmount"));
        f.maxAmount = validAmount(o.opt("maxAmount"));

        JSONArray terms = o.optJSONArray("merchantTerms");
        if (terms != null) {
            for (int i = 0; i < terms.length(); i++) {
                String term = terms.optString(i, "").trim();
                // Bound the length; these become LIKE arguments.
                if (!term.isEmpty() && term.length() <= 64) {
                    f.merchantTerms.add(term.toLowerCase(Locale.US));
                }
            }
        }

        String sort = o.optString("sort", "").trim().toUpperCase(Locale.US);
        for (SearchFilter.Sort s : SearchFilter.Sort.values()) {
            if (s.name().equals(sort)) {
                f.sort = s;
            }
        }

        int limit = o.optInt("limit", -1);
        if (limit > 0 && limit <= 500) {
            f.limit = limit;
        }

        return f;
    }

    private String validDate(String raw) {
        if (raw == null || raw.equals("null")) {
            return null;
        }
        String s = raw.trim();
        if (!s.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return null;
        }
        try {
            java.time.LocalDate.parse(s);
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    private Double validAmount(Object raw) {
        if (!(raw instanceof Number)) {
            return null;
        }
        double v = ((Number) raw).doubleValue();
        if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) {
            return null;
        }
        return v;
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return false;
            }
            NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return caps != null
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return false;
        }
    }
}
