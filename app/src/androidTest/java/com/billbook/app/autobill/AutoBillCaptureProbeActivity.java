package com.billbook.app.autobill;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Synthetic receipt fixture, with no Kotlin runtime needed in the test APK process. */
public class AutoBillCaptureProbeActivity extends Activity {
    public static final String EXTRA_MODE = "receipt_mode";
    public static final String ACCEPTED = "accepted";
    public static final String PENDING = "pending";
    public static final String FINISH = "finish";
    public static final String EDITABLE_SECRET = "AUTOBILL_EDITABLE_SECRET";
    public static final String PASSWORD_SECRET = "AUTOBILL_PASSWORD_SECRET";

    public static String marker(String mode) {
        return "AUTOBILL_CAPTURE_PROBE_" + mode;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        showReceipt(modeFrom(getIntent()));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        showReceipt(modeFrom(intent));
    }

    private String modeFrom(Intent intent) {
        String mode = intent == null ? null : intent.getStringExtra(EXTRA_MODE);
        return mode == null ? ACCEPTED : mode;
    }

    private void showReceipt(String mode) {
        if (FINISH.equals(mode)) {
            finish();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 40, 24, 24);
        root.setBackgroundColor(Color.WHITE);
        root.addView(label(marker(mode), true));
        root.addView(label("转账", true));
        // These visible, nonfocusable text nodes exercise the service's new flag.
        root.addView(label(ACCEPTED.equals(mode) ? "对方已收款" : "待收款", false));
        root.addView(label("￥18.00", false));

        FilteredGroup editable = new FilteredGroup(this, true, false);
        editable.addView(label(EDITABLE_SECRET, true));
        editable.addView(label("￥888.00", true));
        root.addView(editable);

        FilteredGroup password = new FilteredGroup(this, false, true);
        password.addView(label(PASSWORD_SECRET, true));
        password.addView(label("￥999.00", true));
        root.addView(password);
        setContentView(root);
    }

    private TextView label(String value, boolean important) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(20f);
        label.setTextColor(Color.BLACK);
        label.setFocusable(false);
        label.setClickable(false);
        label.setImportantForAccessibility(important
                ? View.IMPORTANT_FOR_ACCESSIBILITY_YES : View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        label.setPadding(0, 8, 0, 8);
        return label;
    }

    /** Real framework nodes with descendants the production collector must skip. */
    private static class FilteredGroup extends LinearLayout {
        private final boolean editable;
        private final boolean password;

        FilteredGroup(Context context, boolean editable, boolean password) {
            super(context);
            this.editable = editable;
            this.password = password;
            setOrientation(LinearLayout.VERTICAL);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setEditable(editable);
            info.setPassword(password);
        }
    }
}
