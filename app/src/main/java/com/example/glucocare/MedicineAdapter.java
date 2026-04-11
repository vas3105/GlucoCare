package com.example.glucocare;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * MedicineAdapter — binds Medicine objects to item_medicine.xml cards.
 *
 * Medicine uses public fields (not getters), so we access
 * medicine.name, medicine.time, medicine.status directly.
 */
public class MedicineAdapter extends RecyclerView.Adapter<MedicineAdapter.MedViewHolder> {

    // ── Callback ─────────────────────────────────────────────────────────────

    public interface OnItemClickListener {
        void onItemClick(Medicine medicine, int position);
    }

    private final Context          context;
    private final List<Medicine>   medicines;
    private OnItemClickListener    listener;

    public MedicineAdapter(Context context, List<Medicine> medicines) {
        this.context   = context;
        this.medicines = medicines;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    // ── RecyclerView.Adapter ──────────────────────────────────────────────────

    @NonNull
    @Override
    public MedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_medicine, parent, false);
        return new MedViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MedViewHolder holder, int position) {
        Medicine med = medicines.get(position);
        holder.bind(med);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(med, holder.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() { return medicines.size(); }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    class MedViewHolder extends RecyclerView.ViewHolder {

        private final ImageView ivMedIcon;
        private final TextView  tvMedName, tvMedDosage, tvMedTime, tvMedStatus;

        MedViewHolder(@NonNull View itemView) {
            super(itemView);
            ivMedIcon   = itemView.findViewById(R.id.ivMedIcon);
            tvMedName   = itemView.findViewById(R.id.tvMedName);
            tvMedDosage = itemView.findViewById(R.id.tvMedDosage);
            tvMedTime   = itemView.findViewById(R.id.tvMedTime);
            tvMedStatus = itemView.findViewById(R.id.tvMedStatus);
        }

        void bind(Medicine med) {
            // ── Direct field access (Medicine uses public fields, not getters) ──
            tvMedName.setText(med.name);
            tvMedDosage.setText(med.getDosageLabel());  // helper method — still valid
            tvMedTime.setText(med.time);
            ivMedIcon.setImageResource(med.getIconRes()); // helper method — still valid

            applyStatusBadge(med.getStatusEnum()); // helper method — still valid
        }

        private void applyStatusBadge(Medicine.Status status) {
            switch (status) {
                case TAKEN:
                    tvMedStatus.setText("TAKEN");
                    tvMedStatus.setTextColor(Color.WHITE);
                    tvMedStatus.getBackground().mutate()
                            .setTint(ContextCompat.getColor(context, R.color.status_taken));
                    break;

                case MISSED:
                    tvMedStatus.setText("MISSED");
                    tvMedStatus.setTextColor(Color.WHITE);
                    tvMedStatus.getBackground().mutate()
                            .setTint(ContextCompat.getColor(context, R.color.status_missed));
                    break;

                case UPCOMING:
                default:
                    tvMedStatus.setText("UPCOMING");
                    tvMedStatus.setTextColor(
                            ContextCompat.getColor(context, R.color.text_muted));
                    tvMedStatus.getBackground().mutate()
                            .setTint(ContextCompat.getColor(context, R.color.status_upcoming));
                    break;
            }
        }
    }
}