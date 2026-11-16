package app.opora.ui

import app.opora.ui.screens.notificationAvailability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAvailabilityTest {
    @Test
    fun deniedPermissionMeansNoNotifications() {
        assertFalse(notificationAvailability(permissionGranted = false, appNotificationsEnabled = true))
    }

    @Test
    fun enabledAppWithoutPermissionMeansNoNotifications() {
        assertFalse(notificationAvailability(permissionGranted = false, appNotificationsEnabled = false))
    }

    @Test
    fun permissionAndAppBothOn() {
        assertTrue(notificationAvailability(permissionGranted = true, appNotificationsEnabled = true))
    }

    @Test
    fun appNotificationsDisabled() {
        assertFalse(notificationAvailability(permissionGranted = true, appNotificationsEnabled = false))
    }
}
