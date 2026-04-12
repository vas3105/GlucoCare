package com.example.glucocare;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * MedicineAdapter — binds Medicine objects to item_medicine.xml.
 *
 * Two callbacks:
 *   onItemClick   → called when the card body is tapped (mark as taken)
 *   onDeleteClick → called when the trash icon is tapped (delete medicine)
 */
public class MedicineAdapter extends RecyclerView.Adapter<MedicineAdapter.MedViewHolder> {

    // ── Callbacks ─────────────────────────────────────────────────────────────

    public interface OnItemClickListener {
        void onItemClick(Medicine medicine, int position);
    }

    public interface OnDeleteClickListener {
        void onDeleteClick(Medicine medicine, int position);
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final Context        context;
    private final List<Medicine> medicines;

    private OnItemClickListener   itemClickListener;
    private OnDeleteClickListener deleteClickListener;

    public MedicineAdapter(Context context, List<Medicine> medicines) {
        this.context   = context;
        this.medicines = medicines;
    }

    public void setOnItemClickListener(OnItemClickListener l)   { this.itemClickListener   = l; }
    public void setOnDeleteClickListener(OnDeleteClickListener l) { this.deleteClickListener = l; }

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

        // Card body tap → mark as taken
        holder.itemView.setOnClickListener(v -> {
            if (itemClickListener != null)
                itemClickListener.onItemClick(med, holder.getAdapterPosition());
        });

        // Trash icon tap → delete (intercept touch so card click doesn't fire)
        holder.btnDelete.setOnClickListener(v -> {
            if (deleteClickListener != null)
                deleteClickListener.onDeleteClick(med, holder.getAdapterPosition());
        });
    }

    @Override
    public int getItemCount() { return medicines.size(); }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    class MedViewHolder extends RecyclerView.ViewHolder {

        final ImageView   ivMedIcon;
        final TextView    tvMedName, tvMedDosage, tvMedTime, tvMedStatus;
        final ImageButton btnDelete;

        MedViewHolder(@NonNull View itemView) {
            super(itemView);
            ivMedIcon   = itemView.findViewById(R.id.ivMedIcon);
            tvMedName   = itemView.findViewById(R.id.tvMedName);
            tvMedDosage = itemView.findViewById(R.id.tvMedDosage);
            tvMedTime   = itemView.findViewById(R.id.tvMedTime);
            tvMedStatus = itemView.findViewById(R.id.tvMedStatus);
            btnDelete   = itemView.findViewById(R.id.btnDeleteMed);
        }

        void bind(Medicine med) {
            tvMedName.setText(med.name);
            tvMedDosage.setText(med.getDosageLabel());
            tvMedTime.setText(med.time);
            ivMedIcon.setImageResource(med.getIconRes());
            applyStatusBadge(med.getStatusEnum());
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