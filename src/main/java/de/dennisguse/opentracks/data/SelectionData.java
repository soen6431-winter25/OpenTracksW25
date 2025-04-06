package de.dennisguse.opentracks.data;

import java.util.Arrays;
import java.util.Objects;

public record SelectionData(
        String selection,
        String[] selectionArgs
) {
    public SelectionData() {
        this(null, null);
    }

    @Override
    public String[] selectionArgs() {
        return selectionArgs == null ? null : selectionArgs.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SelectionData that = (SelectionData) o;
        return Objects.equals(selection, that.selection) && Arrays.equals(selectionArgs, that.selectionArgs);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(selection);
        result = 31 * result + Arrays.hashCode(selectionArgs);
        return result;
    }

    @Override
    public String toString() {
        return "SelectionData{" +
                "selection='" + selection + '\'' +
                ", selectionArgs=" + Arrays.toString(selectionArgs) +
                '}';
    }
}
