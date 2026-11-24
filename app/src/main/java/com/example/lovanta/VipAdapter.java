package com.example.lovanta;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class VipAdapter extends RecyclerView.Adapter<VipAdapter.VipViewHolder> {

    private final List<VipClient> vipList;
    private final OnVipClickListener listener;

    public interface OnVipClickListener {
        void onVipClick(VipClient client);
    }

    public VipAdapter(List<VipClient> vipList, OnVipClickListener listener) {
        this.vipList = vipList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VipViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_vip_client, parent, false);
        return new VipViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VipViewHolder holder, int position) {
        VipClient client = vipList.get(position);
        holder.tvVipName.setText(client.getName());
        holder.tvVipPurchases.setText("Model · " + client.getModelName());
        holder.tvVipPurchases.setBackgroundResource(R.drawable.bg_vip_ppv_zero);
        holder.ivVipAvatar.setImageResource(client.getAvatarResId());
        holder.ivVipAvatar.setBackgroundResource(R.drawable.bg_vip_avatar_ring);

        holder.itemView.setOnClickListener(v -> listener.onVipClick(client));
    }

    @Override
    public int getItemCount() {
        return vipList.size();
    }

    static class VipViewHolder extends RecyclerView.ViewHolder {
        ImageView ivVipAvatar;
        TextView tvVipName, tvVipPurchases;

        public VipViewHolder(@NonNull View itemView) {
            super(itemView);
            ivVipAvatar = itemView.findViewById(R.id.ivVipAvatar);
            tvVipName = itemView.findViewById(R.id.tvVipName);
            tvVipPurchases = itemView.findViewById(R.id.tvVipPurchases);
        }
    }
}
