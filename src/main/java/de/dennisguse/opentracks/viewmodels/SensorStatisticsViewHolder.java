package de.dennisguse.opentracks.viewmodels;

import android.util.Pair;
import android.view.LayoutInflater;

import java.time.Instant;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.models.HeartRateZones;
import de.dennisguse.opentracks.databinding.StatsSensorItemBinding;
import de.dennisguse.opentracks.sensors.sensorData.SensorDataSet;
import de.dennisguse.opentracks.services.RecordingData;
import de.dennisguse.opentracks.settings.PreferencesUtils;
import de.dennisguse.opentracks.settings.UnitSystem;
import de.dennisguse.opentracks.ui.customRecordingLayout.DataField;
import de.dennisguse.opentracks.util.StringUtils;

public abstract class SensorStatisticsViewHolder extends StatisticViewHolder<StatsSensorItemBinding> {

    @Override
    protected StatsSensorItemBinding createViewBinding(LayoutInflater inflater) {
        return StatsSensorItemBinding.inflate(inflater);
    }
    // @mohammadnaserameri
    // You are trying to call the method getBinding() in a static context,
    // but getBinding() is a non-static method. This means it belongs to an instance
    // of the class
    // and cannot be accessed directly from a static method or context.
    // Added the correct code below.

    // @Override
    // public void configureUI(DataField dataField) {
    // getBinding().statsValue.setTextAppearance(dataField.isPrimary() ?
    // R.style.TextAppearance_OpenTracks_PrimaryValue :
    // R.style.TextAppearance_OpenTracks_SecondaryValue);
    // getBinding().statsDescriptionMain.setTextAppearance(dataField.isPrimary() ?
    // R.style.TextAppearance_OpenTracks_PrimaryHeader :
    // R.style.TextAppearance_OpenTracks_SecondaryHeader);
    // }

    // protected static void updateUI(Pair<String, String> valueAndUnit, String
    // sensorName, int descriptionResId) {
    // getBinding().statsValue.setText(valueAndUnit.first);
    // getBinding().statsUnit.setText(valueAndUnit.second);
    // getBinding().statsDescriptionMain.setText(descriptionResId);
    // getBinding().statsDescriptionSecondary.setText(sensorName);
    // }

    @Override
    public void configureUI(DataField dataField) {
        this.getBinding().statsValue
                .setTextAppearance(dataField.isPrimary() ? R.style.TextAppearance_OpenTracks_PrimaryValue
                        : R.style.TextAppearance_OpenTracks_SecondaryValue);
        this.getBinding().statsDescriptionMain
                .setTextAppearance(dataField.isPrimary() ? R.style.TextAppearance_OpenTracks_PrimaryHeader
                        : R.style.TextAppearance_OpenTracks_SecondaryHeader);
    }

    protected void updateUI(Pair<String, String> valueAndUnit, String sensorName, int descriptionResId) {
        this.getBinding().statsValue.setText(valueAndUnit.first);
        this.getBinding().statsUnit.setText(valueAndUnit.second);
        this.getBinding().statsDescriptionMain.setText(descriptionResId);
        this.getBinding().statsDescriptionSecondary.setText(sensorName);
    }

    public static class SensorHeartRate extends SensorStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            SensorDataSet sensorDataSet = data.sensorDataSet();
            String sensorName = getContext().getString(R.string.value_unknown);

            Pair<String, String> valueAndUnit;
            if (sensorDataSet != null && sensorDataSet.getHeartRate() != null) {
                valueAndUnit = StringUtils.getHeartRateParts(getContext(), sensorDataSet.getHeartRate().first);
                sensorName = sensorDataSet.getHeartRate().second;
            } else {
                valueAndUnit = StringUtils.getHeartRateParts(getContext(), null);
            }

            // TODO Loads preference every time
            HeartRateZones zones = PreferencesUtils.getHeartRateZones();
            int textColor;
            if (sensorDataSet != null && sensorDataSet.getHeartRate() != null) {
                textColor = zones.getTextColorForZone(getContext(), sensorDataSet.getHeartRate().first);
            } else {
                textColor = zones.getTextColorForZone(getContext(), null);
            }

            getBinding().statsValue.setText(valueAndUnit.first);
            getBinding().statsUnit.setText(valueAndUnit.second);
            getBinding().statsDescriptionMain.setText(R.string.stats_sensors_heart_rate);

            getBinding().statsDescriptionSecondary.setText(sensorName);

            getBinding().statsValue.setTextColor(textColor);
        }
    }

    public static class SensorCadence extends SensorStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            SensorDataSet sensorDataSet = data.sensorDataSet();
            String sensorName = getContext().getString(R.string.value_unknown);

            Pair<String, String> valueAndUnit;
            if (sensorDataSet != null && sensorDataSet.getCadence() != null) {
                valueAndUnit = StringUtils.getCadenceParts(getContext(), sensorDataSet.getCadence().first);
                sensorName = sensorDataSet.getCadence().second;
            } else {
                valueAndUnit = StringUtils.getCadenceParts(getContext(), null);
            }

            updateUI(valueAndUnit, sensorName, R.string.stats_sensors_cadence);
        }
    }

    public static class SensorPower extends SensorStatisticsViewHolder {

        @Override
        public void onChanged(UnitSystem unitSystem, RecordingData data) {
            SensorDataSet sensorDataSet = data.sensorDataSet();
            String sensorName = getContext().getString(R.string.value_unknown);

            Pair<String, String> valueAndUnit;
            if (sensorDataSet != null && sensorDataSet.getCyclingPower() != null) {
                valueAndUnit = StringUtils.getPowerParts(getContext(),
                        sensorDataSet.getCyclingPower().getValue(Instant.now())); // TODO Use MonotonicClock
                sensorName = sensorDataSet.getCyclingPower().getSensorNameOrAddress();
            } else {
                valueAndUnit = StringUtils.getCadenceParts(getContext(), null);
            }

            updateUI(valueAndUnit, sensorName, R.string.stats_sensors_power);
        }
    }
}
