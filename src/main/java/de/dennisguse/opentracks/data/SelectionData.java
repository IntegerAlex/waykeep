package de.dennisguse.opentracks.data;

public record SelectionData(
        String selection,
        String[] selectionArgs //TODO Refactor to List<String>
) {

    public SelectionData() {
        this(null, null);
    }
}
