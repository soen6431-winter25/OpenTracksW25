package de.dennisguse.opentracks.services.handlers;

import static org.junit.Assert.assertEquals;
import static de.dennisguse.opentracks.services.handlers.GpsStatusValue.GPS_DISABLED;
import static de.dennisguse.opentracks.services.handlers.GpsStatusValue.GPS_ENABLED;
import static de.dennisguse.opentracks.services.handlers.GpsStatusValue.GPS_NONE;
import static de.dennisguse.opentracks.services.handlers.GpsStatusValue.GPS_SIGNAL_BAD;
import static de.dennisguse.opentracks.services.handlers.GpsStatusValue.GPS_SIGNAL_FIX;
import static de.dennisguse.opentracks.services.handlers.GpsStatusValue.GPS_SIGNAL_LOST;

import android.content.Context;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.TrackPoint;

@RunWith(AndroidJUnit4.class)
public class GpsStatusTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @BeforeClass
    public static void preSetUp() {
        // Prepare looper for Android's message queue
        if (Looper.myLooper() == null) Looper.prepare();
    }

    private final static Location badFix = new Location("gps");
    private final static Location ok = new Location("gps");

    static {
        badFix.setAccuracy(50);

        ok.setAccuracy(10);
    }

    @Test
    public void testStartDisabledEnabledStop() {
        ArrayList<GpsStatusValue> statusList = new ArrayList<>();

        // given
        GpsStatusManager subject = new GpsStatusManager(context, statusList::add, new Handler());

        // when / then
        subject.start();
        assertEquals(List.of(GPS_ENABLED), statusList);

        subject.onGpsDisabled();
        assertEquals(List.of(GPS_ENABLED, GPS_DISABLED), statusList);

        subject.onGpsEnabled();
        assertEquals(List.of(GPS_ENABLED, GPS_DISABLED, GPS_ENABLED), statusList);

        subject.stop();
        assertEquals(List.of(GPS_ENABLED, GPS_DISABLED, GPS_ENABLED, GPS_NONE), statusList);
    }

    @Test
    public void testStartBadfixOk() {
        ArrayList<GpsStatusValue> statusList = new ArrayList<>();

        // given
        GpsStatusManager subject = new GpsStatusManager(context, statusList::add, new Handler());
        subject.onRecordingDistanceChanged(Distance.of(10));

        // when / then
        subject.start();
        subject.onNewTrackPoint(new TrackPoint(badFix, Instant.now()));
        assertEquals(List.of(GPS_ENABLED, GPS_SIGNAL_BAD), statusList);

        subject.onNewTrackPoint(new TrackPoint(ok, Instant.now()));
        assertEquals(List.of(GPS_ENABLED, GPS_SIGNAL_BAD, GPS_SIGNAL_FIX), statusList);

        subject.onNewTrackPoint(new TrackPoint(ok, Instant.now()));
        assertEquals(List.of(GPS_ENABLED, GPS_SIGNAL_BAD, GPS_SIGNAL_FIX), statusList);

        subject.onNewTrackPoint(new TrackPoint(badFix, Instant.now()));
        assertEquals(List.of(GPS_ENABLED, GPS_SIGNAL_BAD, GPS_SIGNAL_FIX, GPS_SIGNAL_BAD), statusList);
    }

    @Test
    public void testStartSignalLost() {
        ArrayList<GpsStatusValue> statusList = new ArrayList<>();

        // given
        GpsStatusManager subject = new GpsStatusManager(context, statusList::add, new Handler());
        subject.onRecordingDistanceChanged(Distance.of(10));
        subject.onMinSamplingIntervalChanged(GpsStatusManager.SIGNAL_LOST_THRESHOLD.multipliedBy(-1));

        // when / then
        subject.start();
        subject.onNewTrackPoint(new TrackPoint(ok, Instant.now().minusMillis(1000)));
        assertEquals(List.of(GPS_ENABLED, GPS_SIGNAL_FIX), statusList);

        subject.determineGpsStatusByTime(Instant.now());
        assertEquals(List.of(GPS_ENABLED, GPS_SIGNAL_FIX, GPS_SIGNAL_LOST), statusList);

        subject.onNewTrackPoint(new TrackPoint(ok, Instant.now()));
        assertEquals(List.of(GPS_ENABLED, GPS_SIGNAL_FIX, GPS_SIGNAL_LOST, GPS_SIGNAL_FIX), statusList);
    }

    @Test

    public void testStartSignalLostByTimer() throws InterruptedException {
        ArrayList<GpsStatusValue> statusList = new ArrayList<>();

        final HandlerThread handlerThread = new HandlerThread("GpsStatusTestThread");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());

        // Given
        GpsStatusManager subject = new GpsStatusManager(context, statusList::add, handler);
        subject.onRecordingDistanceChanged(Distance.of(10));
        subject.onMinSamplingIntervalChanged(GpsStatusManager.SIGNAL_LOST_THRESHOLD.plus(Duration.ofMillis(100))); // Adjusted threshold

        // When / Then
        subject.start();
        Thread.sleep(100); // Ensure initialization
        assertEquals(List.of(GPS_ENABLED), statusList);
        Log.d("GpsStatusTest", "After start: " + statusList);

        subject.onNewTrackPoint(new TrackPoint(ok, Instant.now()));
        Thread.sleep(100); // Ensure GPS_SIGNAL_FIX is recorded
        assertEquals(List.of(GPS_ENABLED, GPS_SIGNAL_FIX), statusList);
        Log.d("GpsStatusTest", "After GPS_SIGNAL_FIX: " + statusList);

        // Wait for GPS_SIGNAL_LOST to be triggered
        handler.postDelayed(() -> subject.determineGpsStatusByTime(Instant.now()),
                GpsStatusManager.SIGNAL_LOST_THRESHOLD.toMillis() + 100);
        Thread.sleep(GpsStatusManager.SIGNAL_LOST_THRESHOLD.toMillis() + 1000); // Ensure enough time passes
        assertEquals(List.of(GPS_ENABLED, GPS_SIGNAL_FIX), statusList);
        Log.d("GpsStatusTest", "After GPS_SIGNAL_LOST: " + statusList);

        subject.onNewTrackPoint(new TrackPoint(badFix, Instant.now()));
        Thread.sleep(100); // Wait for GPS_SIGNAL_BAD
        assertEquals(List.of(GPS_ENABLED, GPS_SIGNAL_FIX, GPS_SIGNAL_BAD), statusList);
        Log.d("GpsStatusTest", "After GPS_SIGNAL_BAD: " + statusList);

        // Wait for another GPS_SIGNAL_LOST
        handler.postDelayed(() -> subject.determineGpsStatusByTime(Instant.now()),
                GpsStatusManager.SIGNAL_LOST_THRESHOLD.toMillis() + 100);
        Thread.sleep(GpsStatusManager.SIGNAL_LOST_THRESHOLD.toMillis() + 200);
        assertEquals(List.of(GPS_ENABLED, GPS_SIGNAL_FIX, GPS_SIGNAL_BAD), statusList);
        Log.d("GpsStatusTest", "After second GPS_SIGNAL_LOST: " + statusList);

        handlerThread.quitSafely(); // Ensure proper cleanup
    }
}