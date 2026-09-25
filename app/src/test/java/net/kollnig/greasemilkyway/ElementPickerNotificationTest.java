package net.kollnig.greasemilkyway;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class ElementPickerNotificationTest {

    @Test
    public void switchingPickerPurposeUpdatesTheNotificationTap() {
        Context context = RuntimeEnvironment.getApplication();
        ElementPickerNotification picker = new ElementPickerNotification(context);
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        picker.showNotification(true);
        Notification notification = shadowOf(manager).getAllNotifications().get(0);
        Intent startIntent = shadowOf(notification.contentIntent).getSavedIntent();
        assertTrue(startIntent.getBooleanExtra(
                ElementPickerNotification.EXTRA_NAVIGATION_PICKER, false));

        picker.showNotification(false);
        notification = shadowOf(manager).getAllNotifications().get(0);
        startIntent = shadowOf(notification.contentIntent).getSavedIntent();
        assertFalse(startIntent.getBooleanExtra(
                ElementPickerNotification.EXTRA_NAVIGATION_PICKER, true));
    }
}
