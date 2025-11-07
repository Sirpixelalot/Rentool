package com.renpytool;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Adapter for displaying files and folders in the file picker
 */
public class FilePickerAdapter extends RecyclerView.Adapter<FilePickerAdapter.FileViewHolder> {

    private final List<FileItem> items;
    private final OnItemClickListener listener;
    private final OnItemLongClickListener longClickListener;

    private boolean multiSelectMode = false;
    private final Set<File> selectedFiles = new HashSet<>();

    public interface OnItemClickListener {
        void onItemClick(FileItem item);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(FileItem item);
    }

    public FilePickerAdapter(List<FileItem> items, OnItemClickListener listener, OnItemLongClickListener longClickListener) {
        this.items = items;
        this.listener = listener;
        this.longClickListener = longClickListener;
    }

    public void setMultiSelectMode(boolean enabled) {
        this.multiSelectMode = enabled;
        if (!enabled) {
            selectedFiles.clear();
        }
        notifyDataSetChanged();
    }

    public boolean isMultiSelectMode() {
        return multiSelectMode;
    }

    public void toggleSelection(FileItem item) {
        if (selectedFiles.contains(item.getFile())) {
            selectedFiles.remove(item.getFile());
        } else {
            selectedFiles.add(item.getFile());
        }
        notifyDataSetChanged();
    }

    public Set<File> getSelectedFiles() {
        return new HashSet<>(selectedFiles);
    }

    public int getSelectedCount() {
        return selectedFiles.size();
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        FileItem item = items.get(position);
        boolean isSelected = selectedFiles.contains(item.getFile());
        holder.bind(item, listener, longClickListener, multiSelectMode, isSelected);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        private final CheckBox cbSelect;
        private final ImageView ivIcon;
        private final TextView tvName;
        private final TextView tvDetails;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            cbSelect = itemView.findViewById(R.id.cb_select);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            tvName = itemView.findViewById(R.id.tv_name);
            tvDetails = itemView.findViewById(R.id.tv_details);
        }

        public void bind(FileItem item, OnItemClickListener listener, OnItemLongClickListener longClickListener,
                         boolean multiSelectMode, boolean isSelected) {
            tvName.setText(item.getName());

            // Show/hide checkbox based on multi-select mode
            cbSelect.setVisibility(multiSelectMode ? View.VISIBLE : View.GONE);
            cbSelect.setChecked(isSelected);

            // Highlight selected items
            itemView.setBackgroundColor(isSelected ? 0x20000000 : 0x00000000);

            if (item.getName().equals("..")) {
                // Up navigation item
                ivIcon.setImageResource(android.R.drawable.ic_menu_revert);
                tvDetails.setText("Go up");
                cbSelect.setVisibility(View.GONE); // Never show checkbox for ".."
            } else if (item.isDirectory()) {
                // Directory
                ivIcon.setImageResource(R.drawable.ic_folder);
                tvDetails.setText("Folder");
            } else {
                // File - check file type for custom icons
                String fileName = item.getName().toLowerCase();
                if (fileName.endsWith(".rpy") || fileName.endsWith(".rpyb")) {
                    ivIcon.setImageResource(R.drawable.ic_script_file);
                } else if (fileName.endsWith(".rpa") || fileName.endsWith(".rpyc") || fileName.endsWith(".rpymc")) {
                    ivIcon.setImageResource(R.drawable.ic_rpa_file);
                } else if (fileName.endsWith(".apk")) {
                    ivIcon.setImageResource(R.drawable.ic_apk_file);
                } else if (fileName.endsWith(".png")) {
                    ivIcon.setImageResource(R.drawable.ic_png_file);
                } else if (fileName.endsWith(".zip")) {
                    ivIcon.setImageResource(R.drawable.ic_zip_file);
                } else {
                    ivIcon.setImageResource(android.R.drawable.ic_menu_upload);
                }
                tvDetails.setText(formatFileSize(item.getSize()) + " • " + formatDate(item.getLastModified()));
            }

            // Click listeners
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (longClickListener != null && !item.getName().equals("..")) {
                    longClickListener.onItemLongClick(item);
                    return true;
                }
                return false;
            });
        }

        private String formatFileSize(long size) {
            if (size < 1024) {
                return size + " B";
            } else if (size < 1024 * 1024) {
                return String.format(Locale.US, "%.1f KB", size / 1024.0);
            } else if (size < 1024 * 1024 * 1024) {
                return String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024));
            } else {
                return String.format(Locale.US, "%.1f GB", size / (1024.0 * 1024 * 1024));
            }
        }

        private String formatDate(long timestamp) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
            return sdf.format(new Date(timestamp));
        }
    }
}
