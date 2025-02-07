package de.dennisguse.opentracks;


import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.longClick;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withParentIndex;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static de.dennisguse.opentracks.util.EspressoUtils.childAtPosition;
import static de.dennisguse.opentracks.util.EspressoUtils.waitFor;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class EspressoDeleteTrackTest {

    @Rule
    public ActivityScenarioRule<TrackListActivity> mActivityTestRule = new ActivityScenarioRule<>(TrackListActivity.class);

    @Rule
    public GrantPermissionRule mGrantPermissionRule = TestUtil.createGrantPermissionRule();

    @Test
    public void espressoDeleteTrackTest() {
        // TrackListActivity: start recording
        onView(withId(R.id.track_list_fab_action)).perform(click());

        // TrackRecordingActivity
        onView(withId(R.id.track_recording_fab_action))
                // wait; stay recording
                .perform(waitFor(5000))
                // stop;
                .perform(longClick());

        // TrackStoppedActivity: wait for the finish button to appear (increased wait)
        onView(withId(R.id.finish_button))
                .perform(waitFor(2000)) // increased wait to 2 seconds
                .perform(click());

        // Select track: wait for the track list to populate and select the first item
        onView(allOf(withParent(withId(R.id.track_list)), withParentIndex(0)))
                .perform(waitFor(1000)) // wait for track list to load
                .perform(longClick());

        // Open the overflow menu manually
        onView(allOf(
                withContentDescription("More options"),
                withParent(withParent(withId(androidx.appcompat.R.id.action_mode_bar))),
                isDisplayed()))
                .perform(waitFor(500))
                .perform(click());

        // Wait for the menu item "Delete" to appear then click it
        onView(withText("Delete"))
                .perform(waitFor(500))
                .perform(click());

        // Confirm deletion
        onView(withText("OK"))
                .perform(waitFor(500))
                .perform(click());

        // Verify that the tracklist is empty now
        onView(allOf(withText("Start recording your next adventure here"), isDisplayed()));
    }
}
