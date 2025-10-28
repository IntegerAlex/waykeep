package de.dennisguse.opentracks.ui.customRecordingLayout;

import android.content.res.Resources;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.settings.PreferencesUtils;

public class RecordingLayoutIO {

    private static final String TAG = RecordingLayout.class.getSimpleName();

    private static final String YES_VALUE = "1";
    private static final String NOT_VALUE = "0";

    public static RecordingLayout fromCsv(@NonNull String csvLine, @NonNull Resources resources) {
        List<String> csvParts = CsvLayoutUtils.getCsvLineParts(csvLine);
        if (csvParts == null) {
            Log.e(TAG, "Invalid CSV layout. It shouldn't happen: " + csvLine);
            return new RecordingLayout(PreferencesUtils.getDefaultLayoutName());
        }

        RecordingLayout recordingLayout = new RecordingLayout(csvParts.get(0), Integer.parseInt(csvParts.get(1)));
        for (int i = 2; i < csvParts.size(); i++) {
            String[] fieldParts = CsvLayoutUtils.getCsvFieldParts(csvParts.get(i));
            if (fieldParts == null) {
                Log.e(TAG, "Invalid CSV layout. It shouldn't happen: " + csvLine);
                return recordingLayout;
            }
            recordingLayout.addField(fromCSV(fieldParts, resources));
        }

        // Add any new fields that have been added to the codebase since this layout was saved
        addAllInvisibleDataFields(recordingLayout, resources);

        return recordingLayout;
    }

    /**
     * Adds any missing fields to the layout as hidden fields.
     * This ensures that new fields added to the codebase appear in existing layouts.
     */
    private static void addAllInvisibleDataFields(@NonNull RecordingLayout recordingLayout, @NonNull Resources resources) {
        // Get all field keys currently in the layout
        Set<String> existingFieldKeys = recordingLayout.getFields().stream()
                .map(DataField::getKey)
                .collect(Collectors.toSet());

        // Get all available fields from the codebase
        List<DataField> allAvailableFields = PreferencesUtils.getAllAvailableFields();

        // Add any missing fields as hidden
        for (DataField dataField : allAvailableFields) {
            if (!existingFieldKeys.contains(dataField.getKey())) {
                recordingLayout.addField(dataField);
            }
        }
    }

    public static String toCSV(List<RecordingLayout> recordingLayouts) {
        return recordingLayouts.stream().map(RecordingLayout::toCsv).collect(Collectors.joining(CsvLayoutUtils.LINE_SEPARATOR));
    }

    private static DataField fromCSV(String[] fieldParts, @NonNull Resources resources) {
        return new DataField(
                fieldParts[0],
                YES_VALUE.equals(fieldParts[1]),
                YES_VALUE.equals(fieldParts[2]),
                fieldParts[0].equals(resources.getString(R.string.stats_custom_layout_coordinates_key)));
    }

    static String toCsv(DataField datafield) {
        String visible = datafield.isVisible() ? YES_VALUE : NOT_VALUE;
        String primary = datafield.isPrimary() ? YES_VALUE : NOT_VALUE;
        String wide = datafield.isWide() ? YES_VALUE : NOT_VALUE;
        return datafield.getKey() + CsvLayoutUtils.PROPERTY_SEPARATOR + visible + CsvLayoutUtils.PROPERTY_SEPARATOR + primary + CsvLayoutUtils.PROPERTY_SEPARATOR + wide;
    }
}
