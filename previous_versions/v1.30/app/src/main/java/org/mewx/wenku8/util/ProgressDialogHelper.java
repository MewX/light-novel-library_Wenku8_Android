package org.mewx.wenku8.util;

import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.mewx.wenku8.R;

import java.util.Locale;

/**
 * A helper to create Material 3 dialogs with an embedded progress indicator.
 * Replaces the old MaterialDialog.Builder.progress() pattern.
 */
public class ProgressDialogHelper {

    private final AlertDialog dialog;
    private final LinearProgressIndicator progressBar;
    private final TextView messageView;
    private final TextView percentView;
    private final TextView numberView;

    private ProgressDialogHelper(@NonNull AlertDialog dialog,
                                 @NonNull LinearProgressIndicator progressBar,
                                 @NonNull TextView messageView,
                                 @NonNull TextView percentView,
                                 @NonNull TextView numberView) {
        this.dialog = dialog;
        this.progressBar = progressBar;
        this.messageView = messageView;
        this.percentView = percentView;
        this.numberView = numberView;
    }

    /**
     * Create and show a progress dialog.
     *
     * @param context        the context
     * @param message        the message to display
     * @param indeterminate  true for indeterminate, false for determinate
     * @param cancelable     whether the dialog can be cancelled
     * @param cancelListener optional cancel listener
     * @return the ProgressDialogHelper instance
     */
    public static ProgressDialogHelper show(@NonNull Context context,
                                            @NonNull CharSequence message,
                                            boolean indeterminate,
                                            boolean cancelable,
                                            @Nullable DialogInterface.OnCancelListener cancelListener) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_progress, null);
        TextView messageView = view.findViewById(R.id.progress_message);
        LinearProgressIndicator progressBar = view.findViewById(R.id.progress_bar);
        TextView percentView = view.findViewById(R.id.progress_percent);
        TextView numberView = view.findViewById(R.id.progress_number);

        messageView.setText(message);

        if (indeterminate) {
            progressBar.setIndeterminate(true);
            percentView.setVisibility(View.GONE);
            numberView.setVisibility(View.GONE);
        } else {
            progressBar.setIndeterminate(false);
            progressBar.setMax(1);
            progressBar.setProgress(0);
            percentView.setVisibility(View.VISIBLE);
            numberView.setVisibility(View.VISIBLE);
            updateProgressText(percentView, numberView, 0, 1);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setView(view)
                .setCancelable(cancelable);

        if (cancelListener != null) {
            builder.setOnCancelListener(cancelListener);
        }

        AlertDialog dialog = builder.create();
        dialog.show();

        return new ProgressDialogHelper(dialog, progressBar, messageView, percentView, numberView);
    }

    private static void updateProgressText(TextView percentView, TextView numberView, int progress, int max) {
        if (max <= 0) {
            percentView.setText("0%");
            numberView.setText(String.format(Locale.getDefault(), "%d/%d", progress, max));
            return;
        }
        int percent = (int) ((float) progress / max * 100);
        percentView.setText(String.format(Locale.getDefault(), "%d%%", percent));
        numberView.setText(String.format(Locale.getDefault(), "%d/%d", progress, max));
    }

    /**
     * Convenience overload that accepts a string resource for the message.
     */
    public static ProgressDialogHelper show(@NonNull Context context,
                                            @StringRes int messageResId,
                                            boolean indeterminate,
                                            boolean cancelable,
                                            @Nullable DialogInterface.OnCancelListener cancelListener) {
        return show(context, context.getString(messageResId), indeterminate, cancelable, cancelListener);
    }

    public void setProgress(int progress) {
        progressBar.post(() -> {
            progressBar.setProgress(progress, true);
            if (!progressBar.isIndeterminate()) {
                updateProgressText(percentView, numberView, progress, progressBar.getMax());
            }
        });
    }

    public void setMaxProgress(int max) {
        progressBar.post(() -> {
            progressBar.setIndeterminate(false);
            progressBar.setMax(max);
            percentView.setVisibility(View.VISIBLE);
            numberView.setVisibility(View.VISIBLE);
            updateProgressText(percentView, numberView, progressBar.getProgress(), max);
        });
    }

    public void dismiss() {
        progressBar.post(() -> {
            try {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
            } catch (Exception e) {
                // Ignore exceptions from dismissed dialogs or detached windows.
            }
        });
    }

    public boolean isShowing() {
        return dialog.isShowing();
    }

    public void setTitle(@StringRes int titleId) {
        progressBar.post(() -> dialog.setTitle(titleId));
    }

    public void setTitle(CharSequence title) {
        progressBar.post(() -> dialog.setTitle(title));
    }

    public void setMessage(CharSequence message) {
        progressBar.post(() -> messageView.setText(message));
    }
}
