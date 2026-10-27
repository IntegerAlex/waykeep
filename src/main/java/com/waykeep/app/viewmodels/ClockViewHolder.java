package com.waykeep.app.viewmodels;

import android.view.LayoutInflater;

import com.waykeep.app.R;
import com.waykeep.app.databinding.StatsClockItemBinding;
import com.waykeep.app.services.RecordingData;
import com.waykeep.app.settings.UnitSystem;
import com.waykeep.app.ui.customRecordingLayout.DataField;

public class ClockViewHolder extends StatisticViewHolder<StatsClockItemBinding> {

    @Override
    protected StatsClockItemBinding createViewBinding(LayoutInflater inflater) {
        return StatsClockItemBinding.inflate(inflater);
    }

    @Override
    public void configureUI(DataField dataField) {
        //TODO Unify with GenericStatisticsViewHolder?
        getBinding().statsClock.setTextAppearance(dataField.isPrimary() ? R.style.TextAppearance_OpenTracks_HeroValue : R.style.TextAppearance_OpenTracks_RegisterValue);
        autosizeValue(getBinding().statsClock, dataField.isPrimary());
    }

    @Override
    public void onChanged(UnitSystem unitSystem, RecordingData data) {
    }
}
