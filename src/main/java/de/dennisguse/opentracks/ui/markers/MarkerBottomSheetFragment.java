package de.dennisguse.opentracks.ui.markers;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.models.Marker;
import de.dennisguse.opentracks.databinding.BottomsheetMarkerBinding;
import de.dennisguse.opentracks.util.IntentUtils;
import de.dennisguse.opentracks.util.StringUtils;

/**
 * Compact marker preview shown when tapping a map pin: photo (if any), name,
 * type, time, and a button opening the full detail screen.
 */
public class MarkerBottomSheetFragment extends BottomSheetDialogFragment {

    private static final String MARKER_ID_KEY = "markerId";

    public static MarkerBottomSheetFragment newInstance(Marker.Id markerId) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(MARKER_ID_KEY, markerId);
        MarkerBottomSheetFragment fragment = new MarkerBottomSheetFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

    private BottomsheetMarkerBinding viewBinding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        viewBinding = BottomsheetMarkerBinding.inflate(inflater, container, false);
        return viewBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Marker.Id markerId = getArguments().getParcelable(MARKER_ID_KEY);
        Marker marker = new ContentProviderUtils(requireContext()).getMarker(markerId);
        if (marker == null) {
            dismiss();
            return;
        }

        viewBinding.markerSheetName.setText(marker.name());
        viewBinding.markerSheetType.setText(marker.typeLocalized());
        if (marker.position().time() != null) {
            java.time.Instant time = marker.position().time();
            viewBinding.markerSheetTime.setText(StringUtils.formatDateTimeWithOffset(
                    time.atOffset(java.time.ZoneId.systemDefault().getRules().getOffset(time))));
            viewBinding.markerSheetTime.setVisibility(View.VISIBLE);
        } else {
            viewBinding.markerSheetTime.setVisibility(View.GONE);
        }
        if (marker.description() == null || marker.description().isEmpty()) {
            viewBinding.markerSheetDescription.setVisibility(View.GONE);
        } else {
            viewBinding.markerSheetDescription.setText(marker.description());
            viewBinding.markerSheetDescription.setVisibility(View.VISIBLE);
        }
        if (marker.photoUrl() != null) {
            viewBinding.markerSheetPhoto.setImageURI(marker.photoUrl());
            viewBinding.markerSheetPhoto.setVisibility(View.VISIBLE);
        } else {
            viewBinding.markerSheetPhoto.setVisibility(View.GONE);
        }
        viewBinding.markerSheetOpen.setOnClickListener(v -> {
            startActivity(IntentUtils.newIntent(requireContext(), MarkerDetailActivity.class)
                    .putExtra(MarkerDetailActivity.EXTRA_MARKER_ID, markerId));
            dismiss();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewBinding = null;
    }
}
