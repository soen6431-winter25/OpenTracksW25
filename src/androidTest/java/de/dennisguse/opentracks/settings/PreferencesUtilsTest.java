//package de.dennisguse.opentracks.settings;
//
//import static org.junit.Assert.assertEquals;
//import static org.junit.Assert.assertFalse;
//import static org.junit.Assert.assertNotNull;
//import static org.junit.Assert.assertTrue;
//
//import android.content.Context;
//import android.content.SharedPreferences;
//import android.content.res.Resources;
//
//import androidx.preference.PreferenceManager;
//import androidx.test.core.app.ApplicationProvider;
//import androidx.test.ext.junit.runners.AndroidJUnit4;
//
//import org.junit.Test;
//import org.junit.runner.RunWith;
//
//import java.util.ArrayList;
//import java.util.List;
//
//import de.dennisguse.opentracks.R;
//import de.dennisguse.opentracks.io.file.TrackFileFormat;
//import de.dennisguse.opentracks.ui.customRecordingLayout.DataField;
//import de.dennisguse.opentracks.ui.customRecordingLayout.RecordingLayout;
//import de.dennisguse.opentracks.ui.customRecordingLayout.RecordingLayoutIO;
//
//@RunWith(AndroidJUnit4.class)
//public class PreferencesUtilsTest {
//
//    private final Context context = ApplicationProvider.getApplicationContext();
//    private final Resources resources = ApplicationProvider.getApplicationContext().getResources();
//
//    @Test
//    public void ExportTrackFileFormat_ok() {
//        // given
//        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
//        SharedPreferences.Editor editor = sharedPreferences.edit();
//
//        editor.putString(context.getString(R.string.export_trackfileformat_key), TrackFileFormat.KML_WITH_TRACKDETAIL_AND_SENSORDATA.name());
//        editor.commit();
//
//        // when
//        TrackFileFormat trackFileFormat = PreferencesUtils.getExportTrackFileFormat();
//
//        // then
//        assertEquals(TrackFileFormat.KML_WITH_TRACKDETAIL_AND_SENSORDATA, trackFileFormat);
//    }
//
//    @Test
//    public void ExportTrackFileFormat_invalid() {
//        // given
//        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
//        SharedPreferences.Editor editor = sharedPreferences.edit();
//        editor.putString(context.getString(R.string.export_trackfileformat_key), "invalid");
//        editor.commit();
//
//        // when
//        TrackFileFormat trackFileFormat = PreferencesUtils.getExportTrackFileFormat();
//
//        // then
//        assertEquals(TrackFileFormat.KMZ_WITH_TRACKDETAIL_AND_SENSORDATA_AND_PICTURES, trackFileFormat);
//    }
//
//    @Test
//    public void ExportTrackFileFormat_noValue() {
//        // given
//        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
//        SharedPreferences.Editor editor = sharedPreferences.edit();
//        editor.clear();
//        editor.commit();
//
//        // when
//        TrackFileFormat trackFileFormat = PreferencesUtils.getExportTrackFileFormat();
//
//        // then
//        assertEquals(TrackFileFormat.KMZ_WITH_TRACKDETAIL_AND_SENSORDATA_AND_PICTURES, trackFileFormat);
//    }
//
//    @Test
//    public void testGetAllCustomLayouts_default() {
//        // given
//        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
//        SharedPreferences.Editor editor = sharedPreferences.edit();
//        editor.clear();
//        editor.commit();
//
//        // when
//        List<RecordingLayout> recordingLayouts = PreferencesUtils.getAllCustomLayouts();
//
//        // then
//        assertEquals(recordingLayouts.size(), 1);
//        assertTrue(recordingLayouts.get(0).getFields().size() > 0);
//        assertEquals(recordingLayouts.get(0).getName(), context.getString(R.string.stats_custom_layout_default_layout));
//        assertTrue(recordingLayouts.get(0).getFields().stream().anyMatch(DataField::isVisible));
//    }
//
//    @Test
//    public void testGetCustomLayout_default() {
//        // given
//        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
//        SharedPreferences.Editor editor = sharedPreferences.edit();
//        editor.clear();
//        editor.commit();
//
//        // when
//        RecordingLayout recordingLayout = PreferencesUtils.getCustomLayout();
//
//        // then
//        assertTrue(recordingLayout.getFields().size() > 0);
//        assertEquals(recordingLayout.getName(), context.getString(R.string.stats_custom_layout_default_layout));
//        assertTrue(recordingLayout.getFields().stream().anyMatch(DataField::isVisible));
//    }
//
///**
// * Helper method to verify custom layout configurations.
// * This method reduces redundancy by handling multiple test cases for custom layouts.
// *
// * @param profileName    The name of the profile being tested (e.g., "run", "walking").
// * @param customFieldKey The key representing the specific custom field to be tested.
// */
//
//private void verifyCustomLayout(String profileName, String customFieldKey) {
//    // Arrange: Set up SharedPreferences with a custom layout
//    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
//    SharedPreferences.Editor editor = sharedPreferences.edit();
//    editor.putString(
//            context.getString(R.string.stats_custom_layouts_key),
//            profileName + ";2;" // Profile name and column count
//                    + context.getString(R.string.stats_custom_layout_moving_time_key) + ",1,1;"  // Field 1: Visible, Primary
//                    + context.getString(R.string.stats_custom_layout_distance_key) + ",1,0;"    // Field 2: Visible, Not Primary
//                    + context.getString(customFieldKey) + ",0,1;"  // Field 3: Not Visible, Primary
//                    + context.getString(R.string.stats_custom_layout_speed_key) + ",0,0;");  // Field 4: Not Visible, Not Primary
//    editor.apply();
//
//    // Act: Retrieve the custom layout from PreferencesUtils
//    RecordingLayout recordingLayout = PreferencesUtils.getCustomLayout();
//
//    // Assert: Validate the retrieved layout properties
//    assertEquals(4, recordingLayout.getFields().size());  // Ensure 4 fields exist
//    assertEquals(profileName, recordingLayout.getName());  // Profile name should match
//    assertEquals(2, recordingLayout.getColumnsPerRow());  // Ensure 2 columns per row
//
//    // Validate Field 1: Moving Time
//    assertEquals(context.getString(R.string.stats_custom_layout_moving_time_key), recordingLayout.getFields().get(0).getKey());
//    assertTrue(recordingLayout.getFields().get(0).isVisible());
//    assertTrue(recordingLayout.getFields().get(0).isPrimary());
//
//    // Validate Field 2: Distance
//    assertEquals(context.getString(R.string.stats_custom_layout_distance_key), recordingLayout.getFields().get(1).getKey());
//    assertTrue(recordingLayout.getFields().get(1).isVisible());
//    assertFalse(recordingLayout.getFields().get(1).isPrimary());
//
//    // Validate Field 3: Custom Field (dynamic)
//    assertEquals(context.getString(customFieldKey), recordingLayout.getFields().get(2).getKey());
//    assertFalse(recordingLayout.getFields().get(2).isVisible());
//    assertTrue(recordingLayout.getFields().get(2).isPrimary());
//
//    // Validate Field 4: Speed
//    assertEquals(context.getString(R.string.stats_custom_layout_speed_key), recordingLayout.getFields().get(3).getKey());
//    assertFalse(recordingLayout.getFields().get(3).isVisible());
//    assertFalse(recordingLayout.getFields().get(3).isPrimary());
//}
//
///**
// * Test case to validate the retrieval of a custom layout for the "run" profile.
// * Ensures the correct attributes for the "average moving speed" field.
// */
//@Test
//public void testGetCustomLayout_1() {
//    verifyCustomLayout("run", R.string.stats_custom_layout_average_moving_speed_key);
//}
//
///**
// * Test case to validate the retrieval of a custom layout for the "walking" profile.
// * Ensures the correct attributes for the "coordinates" field.
// */
//@Test
//public void testGetCustomLayout_coordinatesIsWide() {
//    verifyCustomLayout("walking", R.string.stats_custom_layout_coordinates_key);
//}
//
//    @Test
//    public void testSetCustomLayout() {
//        // given
//        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
//        RecordingLayout recordingLayoutSrc = new RecordingLayout("road cycling");
//        recordingLayoutSrc.addField(new DataField(context.getString(R.string.stats_custom_layout_moving_time_key), true, true, false));
//        recordingLayoutSrc.addField(new DataField(context.getString(R.string.stats_custom_layout_distance_key), true, false, false));
//        recordingLayoutSrc.addField(new DataField(context.getString(R.string.stats_custom_layout_average_moving_speed_key), false, true, false));
//        recordingLayoutSrc.addField(new DataField(context.getString(R.string.stats_custom_layout_speed_key), false, false, false));
//
//        // when
//        SharedPreferences.Editor editor = sharedPreferences.edit();
//        editor.putString(context.getString(R.string.stats_custom_layouts_key), recordingLayoutSrc.toCsv());
//        editor.commit();
//
//        // then
//        String csv = sharedPreferences.getString(context.getString(R.string.stats_custom_layouts_key), null);
//        assertNotNull(csv);
//        assertEquals(csv,
//                "road cycling;" + PreferencesUtils.getLayoutColumnsByDefault() + ";"
//                + context.getString(R.string.stats_custom_layout_moving_time_key) + ",1,1,0;"
//                + context.getString(R.string.stats_custom_layout_distance_key) + ",1,0,0;"
//                + context.getString(R.string.stats_custom_layout_average_moving_speed_key) + ",0,1,0;"
//                + context.getString(R.string.stats_custom_layout_speed_key) + ",0,0,0;");
//
//        RecordingLayout recordingLayoutDst = PreferencesUtils.getCustomLayout();
//        assertEquals(recordingLayoutSrc.getName(), recordingLayoutDst.getName());
//        assertEquals(recordingLayoutSrc.getFields().size(), recordingLayoutDst.getFields().size());
//        for (int i = 0; i < recordingLayoutSrc.getFields().size(); i++) {
//            assertEquals(recordingLayoutSrc.getFields().get(i).getKey(), recordingLayoutDst.getFields().get(i).getKey());
//            assertEquals(recordingLayoutSrc.getFields().get(i).isVisible(), recordingLayoutDst.getFields().get(i).isVisible());
//            assertEquals(recordingLayoutSrc.getFields().get(i).isPrimary(), recordingLayoutDst.getFields().get(i).isPrimary());
//        }
//    }
//
//    @Test
//    public void testEditCustomLayouts() {
//        // update all custom layouts
//
//        // given a custom layout with two profiles
//        String cyclingProfile = "cycling;2;"
//                + context.getString(R.string.stats_custom_layout_moving_time_key) + ",1,1;"
//                + context.getString(R.string.stats_custom_layout_distance_key) + ",1,1;"
//                + context.getString(R.string.stats_custom_layout_average_moving_speed_key) + ",1,1;"
//                + context.getString(R.string.stats_custom_layout_speed_key) + ",1,1;";
//
//        String runningProfile = "running;2;"
//                + context.getString(R.string.stats_custom_layout_moving_time_key) + ",1,1;"
//                + context.getString(R.string.stats_custom_layout_distance_key) + ",1,1;"
//                + context.getString(R.string.stats_custom_layout_average_pace_key) + ",0,0;"
//                + context.getString(R.string.stats_custom_layout_pace_key) + ",0,0;";
//
//        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
//        SharedPreferences.Editor editor = sharedPreferences.edit();
//        editor.putString(context.getString(R.string.stats_custom_layouts_key), cyclingProfile + "\n" + runningProfile);
//        editor.apply();
//
//        List<RecordingLayout> layoutsBefore = PreferencesUtils.getAllCustomLayouts();
//
//        // when cyling profile is updated
//        String cyclingProfileUpdated = "cycling;2;"
//                + context.getString(R.string.stats_custom_layout_moving_time_key) + ",1,1;"
//                + context.getString(R.string.stats_custom_layout_distance_key) + ",0,0;"
//                + context.getString(R.string.stats_custom_layout_average_moving_speed_key) + ",0,0;"
//                + context.getString(R.string.stats_custom_layout_speed_key) + ",0,0;";
//
//        List<RecordingLayout> layoutsToBeUpdated = new ArrayList<>();
//        layoutsToBeUpdated.add(RecordingLayoutIO.fromCsv(cyclingProfileUpdated, resources));
//        layoutsToBeUpdated.add(RecordingLayoutIO.fromCsv(runningProfile, resources));
//
//        PreferencesUtils.updateCustomLayouts(layoutsToBeUpdated);
//
//        // then only updated profile is modified in the custom layouts
//        List<RecordingLayout> layoutsAfter = PreferencesUtils.getAllCustomLayouts();
//
//        assertEquals(layoutsBefore.size(), 2);
//        assertEquals(layoutsAfter.size(), 2);
//
//        assertEquals(layoutsBefore.get(0).getFields().stream().filter(DataField::isVisible).count(), 4);
//        assertEquals(layoutsAfter.get(0).getFields().stream().filter(DataField::isVisible).count(), 1);
//    }
//
//    @Test
//    public void testEditCustomLayout() {
//        // Update only one custom layout
//
//        // given a custom layout with two profiles
//        String cyclingProfile = "cycling;2;"
//                + context.getString(R.string.stats_custom_layout_moving_time_key) + ",1,1;"
//                + context.getString(R.string.stats_custom_layout_distance_key) + ",1,1;"
//                + context.getString(R.string.stats_custom_layout_average_moving_speed_key) + ",1,1;"
//                + context.getString(R.string.stats_custom_layout_speed_key) + ",1,1;";
//
//        String runningProfile = "running;2;"
//                + context.getString(R.string.stats_custom_layout_moving_time_key) + ",1,1;"
//                + context.getString(R.string.stats_custom_layout_distance_key) + ",1,1;"
//                + context.getString(R.string.stats_custom_layout_average_pace_key) + ",0,0;"
//                + context.getString(R.string.stats_custom_layout_pace_key) + ",0,0;";
//
//        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
//        SharedPreferences.Editor editor = sharedPreferences.edit();
//        editor.putString(context.getString(R.string.stats_custom_layouts_key), cyclingProfile + "\n" + runningProfile);
//        editor.apply();
//
//        List<RecordingLayout> layoutsBefore = PreferencesUtils.getAllCustomLayouts();
//
//        // when cyling profile is updated
//        String cyclingProfileUpdated = "cycling;2;"
//                + context.getString(R.string.stats_custom_layout_moving_time_key) + ",1,1;"
//                + context.getString(R.string.stats_custom_layout_distance_key) + ",0,0;"
//                + context.getString(R.string.stats_custom_layout_average_moving_speed_key) + ",0,0;"
//                + context.getString(R.string.stats_custom_layout_speed_key) + ",0,0;";
//        RecordingLayout recordingLayoutToBeUpdated = RecordingLayoutIO.fromCsv(cyclingProfileUpdated, resources);
//        PreferencesUtils.updateCustomLayout(recordingLayoutToBeUpdated);
//
//        // then only updated profile is modified in the custom layouts
//        List<RecordingLayout> layoutsAfter = PreferencesUtils.getAllCustomLayouts();
//
//        assertEquals(layoutsBefore.size(), 2);
//        assertEquals(layoutsAfter.size(), 2);
//
//        assertEquals(layoutsBefore.get(0).getFields().stream().filter(DataField::isVisible).count(), 4);
//        assertEquals(layoutsAfter.get(0).getFields().stream().filter(DataField::isVisible).count(), 1);
//    }
//
//    @Test
//    public void testGetCustomLayout_whenSelectedOneNotExists() {
//        // given a custom layout with two profiles and not existing custom layout selected
//        String cyclingProfile = "cycling;2;"
//                + context.getString(R.string.stats_custom_layout_moving_time_key) + ",1,1;"
//                + context.getString(R.string.stats_custom_layout_distance_key) + ",1,1;"
//                + context.getString(R.string.stats_custom_layout_average_moving_speed_key) + ",1,1;"
//                + context.getString(R.string.stats_custom_layout_speed_key) + ",1,1;";
//
//        String runningProfile = "running;2;"
//                + context.getString(R.string.stats_custom_layout_moving_time_key) + ",1,1;"
//                + context.getString(R.string.stats_custom_layout_distance_key) + ",1,1;"
//                + context.getString(R.string.stats_custom_layout_average_pace_key) + ",0,0;"
//                + context.getString(R.string.stats_custom_layout_pace_key) + ",0,0;";
//
//        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
//        SharedPreferences.Editor editor = sharedPreferences.edit();
//        editor.putString(context.getString(R.string.stats_custom_layouts_key), cyclingProfile + "\n" + runningProfile);
//        editor.putString(context.getString(R.string.stats_custom_layout_selected_layout_key), "Not Exists");
//        editor.apply();
//
//        // when it gets the custom layout
//        RecordingLayout recordingLayout = PreferencesUtils.getCustomLayout();
//
//        // then the first one was returned
//        assertEquals(recordingLayout.getName(), "cycling");
//    }
//}
