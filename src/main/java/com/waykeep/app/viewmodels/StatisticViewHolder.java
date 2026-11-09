package com.waykeep.app.viewmodels;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.viewbinding.ViewBinding;

import com.waykeep.app.services.RecordingData;
import com.waykeep.app.settings.UnitSystem;
import com.waykeep.app.ui.customRecordingLayout.DataField;

public abstract class StatisticViewHolder<T extends ViewBinding> {

    private Context context;

    private T binding;

    public void initialize(Context context, LayoutInflater inflater) {
        this.context = context;
        this.binding = createViewBinding(inflater);
    }

    protected abstract T createViewBinding(LayoutInflater inflater);

    public abstract void configureUI(DataField dataField);

    public abstract void onChanged(UnitSystem unitSystem, RecordingData data);

    public View getView() {
        return binding.getRoot();
    }

    /**
     * Lets a field disappear when the recording has no value for it (for example, gain/loss
     * without a barometer or GPS elevation fallback) instead of showing a repeated dash.
     */
    public void setVisible(boolean visible) {
        getView().setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    T getBinding() {
        return binding;
    }

    Context getContext() {
        return context;
    }

    /**
     * The numeral must stay readable at large system font scales: shrink it to fit rather than
     * ellipsizing the one number the screen exists for. The bounds keep the hero and register
     * ranks apart at the default scale.
     */
    protected static void autosizeValue(TextView value, boolean primary) {
        value.setAutoSizeTextTypeUniformWithConfiguration(
                primary ? 28 : 12, primary ? 56 : 24, 1, TypedValue.COMPLEX_UNIT_SP);
    }
}