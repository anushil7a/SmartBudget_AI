package com.example.project3_aadhika8_sguragai;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder> {

    private List<ChatMessage> messages = new ArrayList<>();
    private final int userBgColor;
    private final int aiBgColor;
    private final int userTextColor;
    private final int aiTextColor;

    public ChatMessageAdapter(int userBgColor, int aiBgColor, int userTextColor, int aiTextColor) {
        this.userBgColor = userBgColor;
        this.aiBgColor = aiBgColor;
        this.userTextColor = userTextColor;
        this.aiTextColor = aiTextColor;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages != null ? messages : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    class MessageViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout messageContainer;
        private final MaterialCardView cardMessage;
        private final TextView textMessage;
        private final TextView textTimestamp;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageContainer = itemView.findViewById(R.id.messageContainer);
            cardMessage = itemView.findViewById(R.id.cardMessage);
            textMessage = itemView.findViewById(R.id.textMessage);
            textTimestamp = itemView.findViewById(R.id.textTimestamp);
        }

        public void bind(ChatMessage message) {
            textMessage.setText(message.content);

            // Format timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
            textTimestamp.setText(sdf.format(new Date(message.timestamp)));

            // Style based on sender
            LinearLayout.LayoutParams containerParams = (LinearLayout.LayoutParams) cardMessage.getLayoutParams();
            
            if (message.isUser) {
                // User message - align right, primary color
                containerParams.gravity = Gravity.END;
                cardMessage.setCardBackgroundColor(userBgColor);
                textMessage.setTextColor(userTextColor);
                textTimestamp.setTextColor(userTextColor);
                textTimestamp.setAlpha(0.7f);
                
                // Different corner radius for user (rounded on left, less on right)
                cardMessage.setShapeAppearanceModel(
                    cardMessage.getShapeAppearanceModel().toBuilder()
                        .setTopLeftCornerSize(18f)
                        .setTopRightCornerSize(4f)
                        .setBottomLeftCornerSize(18f)
                        .setBottomRightCornerSize(18f)
                        .build()
                );
            } else {
                // AI message - align left, surface variant color
                containerParams.gravity = Gravity.START;
                cardMessage.setCardBackgroundColor(aiBgColor);
                textMessage.setTextColor(aiTextColor);
                textTimestamp.setTextColor(aiTextColor);
                textTimestamp.setAlpha(0.5f);
                
                // Different corner radius for AI (rounded on right, less on left)
                cardMessage.setShapeAppearanceModel(
                    cardMessage.getShapeAppearanceModel().toBuilder()
                        .setTopLeftCornerSize(4f)
                        .setTopRightCornerSize(18f)
                        .setBottomLeftCornerSize(18f)
                        .setBottomRightCornerSize(18f)
                        .build()
                );
            }
            
            cardMessage.setLayoutParams(containerParams);
        }
    }
}
