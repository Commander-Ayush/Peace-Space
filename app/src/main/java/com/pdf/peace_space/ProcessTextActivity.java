package com.pdf.peace_space;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

public class ProcessTextActivity extends AppCompatActivity {

    private static final String TAG = "ProcessTextActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CharSequence text = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);

        Log.d(TAG, "Process text requested: " + text);

        if (text != null && text.length() > 0) {
            ExplanationBottomSheet bottomSheet = ExplanationBottomSheet.newInstance(text.toString().trim());
            bottomSheet.show(getSupportFragmentManager(), "explanation_bottom_sheet");

            getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                    new FragmentManager.FragmentLifecycleCallbacks() {
                        @Override
                        public void onFragmentDestroyed(@NonNull FragmentManager fm, @NonNull Fragment f) {
                            super.onFragmentDestroyed(fm, f);
                            if (f instanceof ExplanationBottomSheet) {
                                finish();
                            }
                        }
                    }, false);
        } else {
            finish();
        }
    }
}
