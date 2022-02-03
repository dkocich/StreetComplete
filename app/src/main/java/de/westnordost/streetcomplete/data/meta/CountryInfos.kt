package de.westnordost.streetcomplete.data.meta

import android.content.res.AssetManager
import com.charleskorn.kaml.Yaml
import de.westnordost.countryboundaries.CountryBoundaries
import java.io.File
import java.io.IOException
import java.io.SequenceInputStream
import java.util.concurrent.Future

class CountryInfos(
    private val assetManager: AssetManager,
    private val countryBoundaries: Future<CountryBoundaries>?,
) {
    private val countryInfoMap = HashMap<String, IncompleteCountryInfo?>()
    private val defaultCountryInfo: IncompleteCountryInfo by lazy {
        try {
            loadCountryInfo("default")
        } catch (e: Exception) {
            // this should be in any case a programming error
            throw RuntimeException(e)
        }
    }

    /** Get the info by location */
    fun get(longitude: Double, latitude: Double): CountryInfo {
        try {
            val countryCodesIso3166 = countryBoundaries?.get()?.getIds(longitude, latitude)
            return get(countryCodesIso3166 ?: emptyList())
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    /** Get the info by a list of country codes sorted by size. I.e. DE-NI,DE,EU gets the info
     * for Niedersachsen in Germany and uses defaults from Germany and from the European Union */
    fun get(countryCodesIso3166: List<String>): CountryInfo =
        CountryInfo(countryCodesIso3166.mapNotNull { get(it) } + defaultCountryInfo)

    private operator fun get(countryCodeIso3166: String): IncompleteCountryInfo? {
        if (!countryInfoMap.containsKey(countryCodeIso3166)) {
            val info = load(countryCodeIso3166)
            countryInfoMap[countryCodeIso3166] = info
        }
        return countryInfoMap[countryCodeIso3166]
    }

    private fun load(countryCodeIso3166: String): IncompleteCountryInfo? {
        try {
            val countryInfosFiles = assetManager.list(BASEPATH)
            if (countryInfosFiles?.contains("$countryCodeIso3166.yml") == true) {
                return loadCountryInfo(countryCodeIso3166)
            }
            return null
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @Throws(IOException::class)
    private fun loadCountryInfo(countryCodeIso3166: String): IncompleteCountryInfo {
        val filename = "$countryCodeIso3166.yml"
        assetManager.open(BASEPATH + File.separator + filename).use { inputStream ->
            val countryCode = countryCodeIso3166.split("-").first()
            val stream = SequenceInputStream("countryCode: $countryCode\n".byteInputStream(), inputStream)

            return Yaml.default.decodeFromStream(IncompleteCountryInfo.serializer(), stream)
        }
    }

    companion object {
        private const val BASEPATH = "country_metadata"
    }
}
