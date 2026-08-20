package com.example.project3_aadhika8_sguragai;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ExpenseAdapter extends RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder> {

    private List<Expense> expenses = new ArrayList<>();
    private OnExpenseClickListener listener;

    public interface OnExpenseClickListener {
        void onExpenseClick(Expense expense);
    }

    public ExpenseAdapter(OnExpenseClickListener listener) {
        this.listener = listener;
    }

    public void setExpenses(List<Expense> expenses) {
        this.expenses = expenses != null ? expenses : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ExpenseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_expense, parent, false);
        return new ExpenseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ExpenseViewHolder holder, int position) {
        Expense expense = expenses.get(position);
        holder.bind(expense, listener);
    }

    @Override
    public int getItemCount() {
        return expenses.size();
    }

    static class ExpenseViewHolder extends RecyclerView.ViewHolder {
        private final View viewCategoryCircle;
        private final ImageView iconCategory;
        private final TextView textTitle;
        private final TextView textCategory;
        private final TextView textNote;
        private final TextView textAmount;

        public ExpenseViewHolder(@NonNull View itemView) {
            super(itemView);
            viewCategoryCircle = itemView.findViewById(R.id.viewCategoryCircle);
            iconCategory = itemView.findViewById(R.id.iconCategory);
            textTitle = itemView.findViewById(R.id.textExpenseTitle);
            textCategory = itemView.findViewById(R.id.textExpenseCategory);
            textNote = itemView.findViewById(R.id.textExpenseNote);
            textAmount = itemView.findViewById(R.id.textExpenseAmount);
        }

        public void bind(Expense expense, OnExpenseClickListener listener) {
            textTitle.setText(expense.title);
            textAmount.setText(String.format(Locale.getDefault(), "$%.2f", expense.amount));
            
            if (expense.category != null) {
                textCategory.setText(formatCategoryName(expense.category.name()));
                int color = getCategoryColor(expense.category);
                GradientDrawable background = (GradientDrawable) viewCategoryCircle.getBackground();
                background.setColor(color);
                iconCategory.setImageResource(getCategoryIcon(expense.category));
            }
            
            if (expense.note != null && !expense.note.isEmpty()) {
                textNote.setText(expense.note);
                textNote.setVisibility(View.VISIBLE);
            } else {
                textNote.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onExpenseClick(expense);
                }
            });
        }

        private String formatCategoryName(String name) {
            if (name == null || name.isEmpty()) return "";
            return name.charAt(0) + name.substring(1).toLowerCase(Locale.getDefault());
        }

        private int getCategoryColor(ExpenseCategory category) {
            switch (category) {
                case FOOD:
                    return itemView.getContext().getColor(R.color.category_food);
                case TRANSPORT:
                    return itemView.getContext().getColor(R.color.category_transport);
                case ENTERTAINMENT:
                    return itemView.getContext().getColor(R.color.category_entertainment);
                case GROCERIES:
                    return itemView.getContext().getColor(R.color.category_groceries);
                case BILLS:
                    return itemView.getContext().getColor(R.color.category_bills);
                case SHOPPING:
                    return itemView.getContext().getColor(R.color.category_shopping);
                default:
                    return itemView.getContext().getColor(R.color.category_other);
            }
        }

        private int getCategoryIcon(ExpenseCategory category) {
            // Using receipt icon for all categories for simplicity
            // Can be expanded with category-specific icons
            return R.drawable.ic_receipt;
        }
    }
}
