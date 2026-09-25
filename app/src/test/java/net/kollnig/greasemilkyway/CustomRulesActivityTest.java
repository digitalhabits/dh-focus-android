package net.kollnig.greasemilkyway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CustomRulesActivityTest {

    @Test
    public void pickerButtonsPrepareTheirOwnAction() {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("picker_prefs", Context.MODE_PRIVATE).edit()
                .putBoolean("picker_intro_shown", true).commit();
        CustomRulesActivity activity = Robolectric.buildActivity(CustomRulesActivity.class)
                .create().start().resume().visible().get();
        if (!context.getResources().getBoolean(R.bool.show_custom_rules_fab)) {
            assertEquals(View.GONE,
                    activity.findViewById(R.id.custom_rules_opening_button).getVisibility());
            assertEquals(View.GONE,
                    activity.findViewById(R.id.custom_rules_button).getVisibility());
            return;
        }
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        activity.findViewById(R.id.custom_rules_opening_button).performClick();
        assertTrue(isNavigationPicker(shadowOf(manager).getAllNotifications().get(0)));

        activity.findViewById(R.id.custom_rules_button).performClick();
        assertFalse(isNavigationPicker(shadowOf(manager).getAllNotifications().get(0)));
    }

    private boolean isNavigationPicker(Notification notification) {
        Intent startIntent = shadowOf(notification.contentIntent).getSavedIntent();
        return startIntent.getBooleanExtra(
                ElementPickerNotification.EXTRA_NAVIGATION_PICKER, false);
    }
}
