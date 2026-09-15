package com.example.project3_aadhika8_sguragai.ui.home;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.project3_aadhika8_sguragai.R;
import com.example.project3_aadhika8_sguragai.data.CaptureSource;
import com.example.project3_aadhika8_sguragai.data.Expense;
import com.example.project3_aadhika8_sguragai.data.ExpenseCategory;
import com.example.project3_aadhika8_sguragai.ui.CategoryPalette;

import java.util.Locale;
import java.util.Objects;

/**
 * The transaction feed.
 *
 * <p>A {@link ListAdapter} with a real {@link DiffUtil} callback, so a changed row animates
 * instead of the whole list flashing — which is what {@code notifyDataSetChanged} did before.
 */
public class ExpenseAdapter extends ListAdapter<Expense, ExpenseAdapter.VH> {

    public interface Listener {
        void onClick(Expense expense);

        void onRecategorise(Expense expense);
    }

    private final Listener listener;

    public ExpenseAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Expense> DIFF = new DiffUtil.ItemCallback<Expense>() {
        @Override
        public boolean areItemsTheSame(@NonNull Expense a, @NonNull Expense b) {
            return a.id == b.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Expense a, @NonNull Expense b) {
            return a.amount == b.amount
                    && a.category == b.category
                    && Objects.equals(a.title, b.title)
                    && Objects.equals(a.merchant, b.merchant)
                    && Objects.equals(a.date, b.date)
                    && a.source == b.source
                    && a.categorySource == b.categorySource
                    && Objects.equals(a.predictionConfidence, b.predictionConfidence);
        }
    };

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_expense, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(getItem(position), listener);
    }

    /** The category palette, as a marker colour. One mapping, shared with the charts. */
    static int colorFor(View view, ExpenseCategory category) {
        return CategoryPalette.color(view.getContext(), category);
    }

    static class VH extends RecyclerView.ViewHolder {

        private final View marker;
        private final TextView merchant;
        private final TextView meta;
        private final TextView amount;

        VH(@NonNull View itemView) {
            super(itemView);
            marker = itemView.findViewById(R.id.viewCategoryMarker);
            merchant = itemView.findViewById(R.id.textMerchant);
            meta = itemView.findViewById(R.id.textMeta);
            amount = itemView.findViewById(R.id.textAmount);
        }

        void bind(Expense e, Listener listener) {
            String name = e.merchant != null && !e.merchant.isEmpty() ? e.merchant : e.title;
            merchant.setText(name == null ? "—" : name);
            amount.setText(String.format(Locale.US, "$%.2f", e.amount));
            marker.setBackgroundColor(colorFor(itemView, e.category));
            meta.setText(buildMeta(e));

            itemView.setOnClickListener(v -> listener.onClick(e));
            itemView.setOnLongClickListener(v -> {
                listener.onRecategorise(e);
                return true;
            });
        }

        /**
         * "GROCERIES · SCANNED · 94%" — category, where it came from, and how sure the
         * classifier was. Confidence is omitted when a human chose the category, because
         * there is nothing to be unsure about.
         */
        private String buildMeta(Expense e) {
            StringBuilder sb = new StringBuilder();
            sb.append(e.category == null ? "UNCATEGORISED" : e.category.name());
            sb.append(" · ");
            sb.append(e.source == CaptureSource.SCANNED ? "SCANNED" : "TYPED");
            if (e.predictionConfidence != null) {
                sb.append(" · ")
                        .append(Math.round(e.predictionConfidence * 100))
                        .append('%');
            }
            return sb.toString();
        }
    }
}
