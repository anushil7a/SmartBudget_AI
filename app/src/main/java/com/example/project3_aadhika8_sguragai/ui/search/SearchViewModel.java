package com.example.project3_aadhika8_sguragai.ui.search;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.project3_aadhika8_sguragai.BuildConfig;
import com.example.project3_aadhika8_sguragai.data.Expense;
import com.example.project3_aadhika8_sguragai.data.ExpenseRepository;
import com.example.project3_aadhika8_sguragai.sense.OpenAIService;
import com.example.project3_aadhika8_sguragai.sense.search.LlmFilterTranslator;
import com.example.project3_aadhika8_sguragai.sense.search.QueryParser;
import com.example.project3_aadhika8_sguragai.sense.search.SearchFilter;
import com.example.project3_aadhika8_sguragai.sense.search.SearchFilterSqlBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Two ways of asking a question, with very different privacy properties.
 *
 * <p><b>Find</b> compiles the query locally and runs it against Room. Nothing leaves the
 * device. If the local parser cannot make sense of the phrase at all, {@link
 * LlmFilterTranslator} may be asked to translate it — and it is sent the phrase only, never
 * any transactions.
 *
 * <p><b>Ask</b> is the conversational path and <b>does</b> send expense context to OpenAI.
 * The screen says so plainly; that is the trade the user is making by switching modes.
 */
public class SearchViewModel extends AndroidViewModel {

    public enum Mode { FIND, ASK }

    private final ExpenseRepository repo;
    private final QueryParser parser = new QueryParser();
    private final LlmFilterTranslator translator;
    private final OpenAIService openAI;

    private final MutableLiveData<Mode> mode = new MutableLiveData<>(Mode.FIND);
    private final MutableLiveData<List<Expense>> results = new MutableLiveData<>();
    private final MutableLiveData<List<SearchFilter.Chip>> chips = new MutableLiveData<>();
    private final MutableLiveData<String> status = new MutableLiveData<>();
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);
    private final MutableLiveData<String> answer = new MutableLiveData<>();

    private SearchFilter current = new SearchFilter();

    public SearchViewModel(@NonNull Application application) {
        super(application);
        repo = ExpenseRepository.get(application);
        translator = new LlmFilterTranslator(application, BuildConfig.OPENAI_API_KEY);
        openAI = new OpenAIService(BuildConfig.OPENAI_API_KEY);
    }

    public LiveData<Mode> mode() {
        return mode;
    }

    public LiveData<List<Expense>> results() {
        return results;
    }

    public LiveData<List<SearchFilter.Chip>> chips() {
        return chips;
    }

    public LiveData<String> status() {
        return status;
    }

    public LiveData<Boolean> busy() {
        return busy;
    }

    public LiveData<String> answer() {
        return answer;
    }

    public void setMode(Mode m) {
        mode.setValue(m);
    }

    public boolean hasApiKey() {
        return openAI.hasApiKey();
    }

    // ------------------------------------------------------------- Find

    public void find(String query) {
        if (query == null || query.trim().isEmpty()) {
            current = new SearchFilter();
            chips.setValue(Collections.emptyList());
            results.setValue(Collections.emptyList());
            status.setValue(null);
            return;
        }

        SearchFilter local = parser.parse(query);

        if (local.isEmpty() && translator.canRun(local)) {
            // The local grammar did not recognise anything. Ask a model to translate the
            // phrase — the phrase alone, no transactions.
            busy.setValue(true);
            translator.translate(query, new LlmFilterTranslator.Result() {
                @Override
                public void onFilter(SearchFilter filter) {
                    busy.postValue(false);
                    apply(filter, true);
                }

                @Override
                public void onUnparseable() {
                    busy.postValue(false);
                    status.postValue("Could not understand that query");
                    results.postValue(Collections.emptyList());
                    chips.postValue(Collections.emptyList());
                }
            });
            return;
        }

        if (local.isEmpty()) {
            status.setValue("Could not understand that query");
            results.setValue(Collections.emptyList());
            chips.setValue(Collections.emptyList());
            return;
        }

        apply(local, false);
    }

    private void apply(SearchFilter filter, boolean translated) {
        current = filter;
        chips.postValue(filter.toChips());
        run(translated);
    }

    private void run(boolean translated) {
        repo.query(() -> repo.expenseDao().search(SearchFilterSqlBuilder.build(current)),
                rows -> {
                    List<Expense> list = rows == null ? Collections.emptyList() : rows;
                    results.setValue(list);
                    String prefix = translated ? "Interpreted · " : "";
                    status.setValue(prefix + list.size()
                            + (list.size() == 1 ? " result" : " results"));
                });
    }

    /** Removing a chip drops that one constraint and re-runs the query. */
    public void removeChip(SearchFilter.Chip chip) {
        current.remove(chip);
        chips.setValue(current.toChips());
        if (current.isEmpty()) {
            results.setValue(Collections.emptyList());
            status.setValue(null);
        } else {
            run(false);
        }
    }

    // -------------------------------------------------------------- Ask

    /**
     * Sends the question along with a summary of the user's expenses to OpenAI. Callers must
     * have made that clear on screen first.
     */
    public void ask(String question) {
        busy.setValue(true);
        repo.query(this::buildContext, context ->
                openAI.sendMessage(question, context, new OpenAIService.ChatCallback() {
                    @Override
                    public void onSuccess(String response) {
                        busy.setValue(false);
                        answer.setValue(response);
                    }

                    @Override
                    public void onError(String error) {
                        busy.setValue(false);
                        answer.setValue("Could not reach the assistant: " + error);
                    }
                }));
    }

    private String buildContext() {
        List<Expense> all = repo.expenseDao().getAllExpenses();
        StringBuilder sb = new StringBuilder();
        if (all != null) {
            for (Expense e : all) {
                sb.append(String.format(Locale.US, "%s: %s - $%.2f (%s)%n",
                        e.date,
                        e.merchant != null ? e.merchant : e.title,
                        e.amount,
                        e.category == null ? "OTHER" : e.category.name()));
            }
        }
        return sb.toString();
    }

    public List<String> exampleQueries() {
        List<String> out = new ArrayList<>();
        out.add("food last month");
        out.add("over $50 this year");
        out.add("top 5 most expensive");
        out.add("groceries last week");
        return out;
    }
}
