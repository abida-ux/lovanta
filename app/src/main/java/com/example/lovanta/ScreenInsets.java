package com.example.lovanta;

import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.ComponentActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

final class ScreenInsets {

    private ScreenInsets() {}

    static void applyToMainRoot(ComponentActivity activity) {
        EdgeToEdge.enable(activity);
        View root = activity.findViewById(R.id.main);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return windowInsets;
        });
    }
}
