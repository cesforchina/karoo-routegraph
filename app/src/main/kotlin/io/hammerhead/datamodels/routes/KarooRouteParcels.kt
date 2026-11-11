/*
 * Compatibility parcels for the exported Karoo route creation activity.
 *
 * These classes intentionally use Karoo's private class names because Android stores a
 * Parcelable's class name in the Bundle. Keep this implementation version-gated in the importer.
 */
package io.hammerhead.datamodels.routes

import android.os.Parcel
import android.os.Parcelable
import io.hammerhead.geomodule.Coordinates
import java.util.Date

class Elevation(
    private val gain: Float = 0f,
    private val loss: Float = 0f,
    private val min: Float = 0f,
    private val max: Float = 0f,
    private val polyline: String = "",
    private val source: String? = null,
) : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeFloat(gain)
        dest.writeFloat(loss)
        dest.writeFloat(min)
        dest.writeFloat(max)
        dest.writeString(polyline)
        dest.writeString(source)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Elevation> = object : Parcelable.Creator<Elevation> {
            override fun createFromParcel(parcel: Parcel): Elevation = Elevation(
                gain = parcel.readFloat(),
                loss = parcel.readFloat(),
                min = parcel.readFloat(),
                max = parcel.readFloat(),
                polyline = parcel.readString().orEmpty(),
                source = parcel.readString(),
            )

            override fun newArray(size: Int): Array<Elevation?> = arrayOfNulls(size)
        }
    }
}

class Waypoint(
    private val lat: Double,
    private val lng: Double,
    private val polylineIndex: Int?,
) : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeDouble(lat)
        dest.writeDouble(lng)
        dest.writeInt(-1) // waypointType
        dest.writeInt(polylineIndex ?: -1)
        dest.writeInt(-1) // oneway
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Waypoint> = object : Parcelable.Creator<Waypoint> {
            override fun createFromParcel(parcel: Parcel): Waypoint {
                val lat = parcel.readDouble()
                val lng = parcel.readDouble()
                parcel.readInt()
                val index = parcel.readInt().takeUnless { it == -1 }
                parcel.readInt()
                return Waypoint(lat, lng, index)
            }

            override fun newArray(size: Int): Array<Waypoint?> = arrayOfNulls(size)
        }
    }
}

class Route(
    private val id: String,
    private val name: String,
    private val source: String,
    private val distance: Double,
    private val elevation: Elevation,
    private val startLocation: Coordinates,
    private val endLocation: Coordinates,
    private val waypoints: List<Waypoint>,
    private val startLocationName: String,
    private val endLocationName: String,
    private val createdAt: Date,
    private val summaryPolyline: String,
    private val routePolyline: String,
) : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(id)
        dest.writeString(name)
        dest.writeString(source)
        dest.writeDouble(distance)
        elevation.writeToParcel(dest, flags)
        dest.writeParcelable(startLocation, flags)
        dest.writeParcelable(endLocation, flags)
        dest.writeInt(waypoints.size)
        waypoints.forEach { it.writeToParcel(dest, flags) }
        dest.writeString(startLocationName)
        dest.writeString(endLocationName)
        dest.writeSerializable(createdAt)
        dest.writeString(summaryPolyline)
        dest.writeInt(0) // isPublic
        dest.writeString(null) // imageVersion
        dest.writeInt(1) // isAutoImported
        dest.writeSerializable(null) // updatedAt
        dest.writeString(routePolyline)
        dest.writeInt(0) // routingType
        dest.writeInt(0) // isStarred
        dest.writeInt(0) // pointsOfInterest
        dest.writeStringList(emptyList()) // collections
        dest.writeInt(0) // bounds
        dest.writeString(null) // sourceId
        dest.writeInt(0) // surfaceSummary
        dest.writeInt(0) // routingConfig
        dest.writeInt(0) // stemSheet
        dest.writeInt(1) // changeUpdatedAt
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Route> = object : Parcelable.Creator<Route> {
            override fun createFromParcel(parcel: Parcel): Route =
                error("Karoo compatibility Route is write-only")

            override fun newArray(size: Int): Array<Route?> = arrayOfNulls(size)
        }
    }
}
