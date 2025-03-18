package de.dennisguse.opentracks.data;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public record Id(long id) implements Parcelable {
    @NonNull
    @Override
    public String toString() {
        throw new RuntimeException("Not supported");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeLong(id);
    }

    public static final Creator<Id> CREATOR = new Creator<>() {
        public Id createFromParcel(Parcel in) {
            return new Id(in.readLong());
        }

        public Id[] newArray(int size) {
            return new Id[size];
        }
    };
}
