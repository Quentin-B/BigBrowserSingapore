package com.example.zuhlkemap

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.gson.GsonBuilder
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.collections.ArrayList

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

	private lateinit var mMap: GoogleMap
	private lateinit var mCurrentCameraList: CameraList
	private lateinit var queue: RequestQueue
	private var dataURL: String = "https://api.data.gov.sg/v1/transport/traffic-images"
	private var SINGAPORE: LatLng = LatLng(1.3521, 103.8198)
	private var timerPeriod: Long = 20000


	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_maps)

		val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
		mapFragment.getMapAsync(this)
	}

	override fun onMapReady(googleMap: GoogleMap) {
		mMap = googleMap
		mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(SINGAPORE, 10f))
		mMap.setInfoWindowAdapter(CustomInfoWindowAdapter(this))

		queue = Volley.newRequestQueue(this)
		val jsonObjectRequest = fetchJSONData()
		queue.add(jsonObjectRequest)

		createTimer()
	}

	private fun createTimer() {
		val timer = Timer()

		timer.scheduleAtFixedRate(
			object : TimerTask() {
				override fun run() {
					val jsonObjectRequest = fetchJSONData()
					queue.add(jsonObjectRequest)
				}
			},timerPeriod, timerPeriod
		)
	}

	private fun fetchJSONData(): JsonObjectRequest {
		return JsonObjectRequest(Request.Method.GET, dataURL, null,
			Response.Listener { response ->
				val responseJSON: JSONArray = response["items"] as JSONArray
				val responseJSONArray: JSONObject = responseJSON.get(0) as JSONObject

				val gson = GsonBuilder().create()
				val cameraList = gson.fromJson(responseJSONArray.toString(),CameraList::class.java)

				parseCameraList(cameraList)
			},
			Response.ErrorListener { error ->
				Log.d("Request Failed", error.toString())
			}
		)
	}

	private fun parseCameraList(newCameraList: CameraList) {

		/** Initialize mCurrentCameraList **/
		if(!this::mCurrentCameraList.isInitialized) {

			mCurrentCameraList = newCameraList
			newCameraList.cameras.forEach {
				createMarker(it)
			}
			return
		}

		newCameraList.cameras.forEach {
			/** if the camera fetched is already in the list we look at its time stamp **/
			/** if the timestamp has been updated then we need to update the picture shown in the marker **/
			if(it in mCurrentCameraList.cameras) {
				val id = it.camera_id
				val cameraInList = mCurrentCameraList.cameras.find { it -> it.camera_id == id }

				if(it.timestamp != cameraInList?.timestamp) { /** New time stamp means no picture to show **/
					val currentMarker = cameraInList?.marker
					val isOldMarkerShown = currentMarker?.isInfoWindowShown

					// remove old marker and create a new one
					currentMarker?.remove()

					//replace in arrayList
					val index = mCurrentCameraList.cameras.indexOf(it)
					mCurrentCameraList.cameras[index] = it

					createMarker(it,isOldMarkerShown)
				}
			} else {
				/** if the camera is not part of the initial list **/
				/** it means it has been added to the grid after App initialisation **/
				createMarker(it)
				mCurrentCameraList.cameras.add(it)
			}

			/** Potential issue with the solution above **/
			/** Cameras are never removed from the list **/
			/** Possible solution: When the json is returned check if some IDs have disappeared **/
			/** And update the list accordingly **/
		}

		/** Clear map and re add markers **/
		/** Pros: **/
		/** Will make sure that any new camera will be added to the map **/
		/** Will make sure that any decommissioned camera will be removed from the map without restarting the app**/
		/** Cons: **/
		/** Force close on the marker currently open **/
		/** resulting in a bad UX **/
		//		mMap.clear()
		//		newCameraList.cameras.forEach {
		//			val markerOption = createMarker(it)
		//			mMap.addMarker(markerOption)
		//		}
	}

	private fun createMarker(cameraItem: Camera, showMarker: Boolean? = false) {
		val location = LatLng(cameraItem.location.latitude, cameraItem.location.longitude)
		val markerOption = MarkerOptions()
		markerOption.title(cameraItem.timestamp)
		markerOption.position(location)
		markerOption.snippet(cameraItem.image)

		val marker = mMap.addMarker(markerOption)
		marker.tag = cameraItem.camera_id
		cameraItem.marker = marker

		if(showMarker!!) {
			marker.showInfoWindow()
		}
	}

	/** Utility classes to serialize/deserialize JSON data **/
	private class CameraList constructor(val timestamp: String, val cameras: ArrayList<Camera>)
	private class Camera constructor(val timestamp: String, val  image: String, val location: LatLong, val camera_id: String, val image_metadata: ImageMetaData, var marker: Marker?) {
		override fun equals(other: Any?): Boolean {
			val toCheck: Camera = other as Camera
			return toCheck.camera_id == this.camera_id
		}
	}
	/**  we could improve serialisation by using the Location class instead of creating LatLong utility class **/
	/**  it would require to write a custom deserializer that implements JsonDeserializer  **/
	private class LatLong constructor(val latitude: Double, val longitude: Double)
	private class ImageMetaData constructor(val height: Int, val width: Int, val md5: String)

	/** Customize the content window **/
	class CustomInfoWindowAdapter(context: Context?) : AppCompatActivity(), GoogleMap.InfoWindowAdapter {

		private var inflater = LayoutInflater.from(context)

		override fun getInfoWindow(marker: Marker?): View? {
			return null
		}

		override fun getInfoContents(marker: Marker?): View? {

			val viewCustomInfoMarker = inflater.inflate(R.layout.custom_info_marker,null)

			val timeStampView = viewCustomInfoMarker.findViewById<TextView>(R.id.timeStamp)
			val latitudeTextView = viewCustomInfoMarker.findViewById<TextView>(R.id.latitudeText)
			val longitudeTextView = viewCustomInfoMarker.findViewById<TextView>(R.id.longitudeText)
			val cameraIDTextView = viewCustomInfoMarker.findViewById<TextView>(R.id.cameraID)
			val imageView = viewCustomInfoMarker.findViewById<ImageView>(R.id.image)

			val markerPos = marker?.position

			timeStampView?.text = "TimeStamp=" + marker?.title
			latitudeTextView?.text = "Lat=" + markerPos?.latitude.toString()
			longitudeTextView?.text = "Lng=" + markerPos?.longitude.toString()
			cameraIDTextView?.text = "CameraID= " + marker?.tag as String

			val url = marker?.snippet

			Picasso.get()
				.load(url)
				.placeholder(R.mipmap.ic_launcher_round)
				.into(imageView, MarkerCallback(marker));

			return viewCustomInfoMarker

		}

		/** Content of Info Window is pre-rendered **/
		/** We force repaint when new image is loaded **/
		class MarkerCallback internal constructor(marker: Marker?) : Callback {
			private var marker: Marker? = null

			override fun onSuccess() {
				if (marker != null && marker!!.isInfoWindowShown) {
					marker!!.hideInfoWindow()
					marker!!.showInfoWindow()
				}
			}

			override fun onError(e: java.lang.Exception?) {
				Log.d("error", "Error loading thumbnail!")
			}

			init {
				this.marker = marker
			}
		}
	}
}




