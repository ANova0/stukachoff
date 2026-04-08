package com.stukachoff.data.content

import android.content.Context
import com.stukachoff.domain.model.AppThreat
import com.stukachoff.domain.model.DetectionMethod
import com.stukachoff.domain.model.ThreatLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun loadKnownApps(): List<KnownAppEntry> {
        val json = context.assets.open("threats.json")
            .bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val arr = root.getJSONArray("known_apps")
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            val methodsArr = obj.getJSONArray("methods")
            val methods = (0 until methodsArr.length()).map { j ->
                when (methodsArr.getString(j)) {
                    "transport_vpn"  -> DetectionMethod.TRANSPORT_VPN
                    "interface_name" -> DetectionMethod.INTERFACE_NAME
                    "localhost_scan" -> DetectionMethod.LOCALHOST_SCAN
                    "http_probing"   -> DetectionMethod.HTTP_PROBING
                    "plmn"           -> DetectionMethod.PLMN_MCC
                    "geoip"          -> DetectionMethod.GEOLOCATION
                    "package_scan"   -> DetectionMethod.PACKAGE_SCAN
                    else             -> DetectionMethod.TRANSPORT_VPN
                }
            }
            KnownAppEntry(
                packageName  = obj.getString("package"),
                name         = obj.getString("name"),
                threatLevel  = when (obj.getString("threat_level")) {
                    "red"  -> ThreatLevel.RED
                    "grey" -> ThreatLevel.GREY
                    else   -> ThreatLevel.YELLOW
                },
                confirmed    = obj.optBoolean("confirmed", false),
                methods      = methods,
                harm         = obj.getString("harm"),
                source       = obj.optString("source").takeIf { it.isNotBlank() }
            )
        }
    }
}

data class KnownAppEntry(
    val packageName: String,
    val name: String,
    val threatLevel: ThreatLevel,
    val confirmed: Boolean,
    val methods: List<DetectionMethod>,
    val harm: String,
    val source: String?
)
