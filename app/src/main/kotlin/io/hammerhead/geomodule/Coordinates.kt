/*
 * Compatibility parcel for the exported Karoo route creation activity.
 *
 * This mirrors only the stable Parcel wire format observed on Karoo 4.217.1.
 * It is intentionally isolated from the public karoo-ext API.
 */
package io.hammerhead.geomodule

import android.os.Parcel
import android.os.Parcelable

class Coordinates(
    private val lat: Double,
    private val lng: Double,
) : Parcelable {
    private constructor(parcel: Parcel) : this(
        lat = parcel.readDouble(),
        lng = parcel.readDouble(),
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeDouble(lat)
        dest.writeDouble(lng)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Coordinates> = object : Parcelable.Creator<Coordinates> {
            override fun createFromParcel(parcel: Parcel): Coordinates = Coordinates(parcel)
            override fun newArray(size: Int): Array<Coordinates?> = arrayOfNulls(size)
        }
    }
}
