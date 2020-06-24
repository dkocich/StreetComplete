package de.westnordost.streetcomplete.quests.surface

import de.westnordost.osmapi.map.data.BoundingBox
import de.westnordost.osmapi.map.data.Element
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.meta.ALL_ROADS
import de.westnordost.streetcomplete.data.osm.changes.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.elementgeometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.OverpassMapDataAndGeometryApi
import de.westnordost.streetcomplete.data.osm.osmquest.OsmElementQuestType
import de.westnordost.streetcomplete.data.tagfilters.FiltersParser
import de.westnordost.streetcomplete.data.tagfilters.getQuestPrintStatement
import de.westnordost.streetcomplete.data.tagfilters.toGlobalOverpassBBox


class DetailRoadSurface(private val overpassMapDataApi: OverpassMapDataAndGeometryApi) : OsmElementQuestType<DetailSurfaceAnswer> {
    override val commitMessage = "More detailed road surfaces"
    override val wikiLink = "Key:surface"
    override val icon = R.drawable.ic_quest_street_surface_detail

    override fun getTitle(tags: Map<String, String>): Int {
        val hasName = tags.containsKey("name")
        val isSquare = tags["area"] == "yes"

        return if (hasName) {
            if (isSquare)
                R.string.quest_surface_detailed_square_name_title
            else
                R.string.quest_surface_detailed_name_title
        } else {
            if (isSquare)
                R.string.quest_surface_detailed_square_title
            else
                R.string.quest_surface_detailed_title
        }
    }

    override fun createForm() = DetailRoadSurfaceForm()

    override fun download(bbox: BoundingBox, handler: (element: Element, geometry: ElementGeometry?) -> Unit): Boolean {
        return overpassMapDataApi.query(getOverpassQuery(bbox), handler)
    }

    override fun isApplicableTo(element: Element) = REQUIRED_MATCH_TFE.matches(element)

    private fun getOverpassQuery(bbox: BoundingBox) =
        bbox.toGlobalOverpassBBox() + "\n" + """

          way[surface ~ "^($UNDETAILED_SURFACE_TAG_MATCH)$"][segregated != "yes"]["surface:note" !~ ".*"][highway ~ "^$HIGHWAY_TAG_MATCH$"] -> .surface_without_detail;
          way.surface_without_detail[access !~ "^(private|no)$"] -> .not_private;
          way.surface_without_detail[foot][foot !~ "^(private|no)$"] -> .foot_access;
          (.not_private; .foot_access;);
        """.trimIndent() + "\n" +
        getQuestPrintStatement()

    private val HIGHWAY_TAG_MATCH = ALL_ROADS.joinToString("|")
    private val UNDETAILED_SURFACE_TAG_MATCH = "paved|unpaved"
    private val REQUIRED_MATCH_TFE by lazy { FiltersParser().parse(
            "ways with surface ~ ${UNDETAILED_SURFACE_TAG_MATCH} and !surface:note and segregated!=yes and highway ~ $HIGHWAY_TAG_MATCH and (access !~ private|no or (foot and foot !~ private|no))"
    )}

    override val isSplitWayEnabled = true

    override fun applyAnswerTo(answer: DetailSurfaceAnswer, changes: StringMapChangesBuilder) {
        when(answer) {
            is SurfaceAnswer -> {
                changes.modify("surface", answer.value)
                changes.deleteIfExists("source:surface")
            }
            is DetailingImpossibleAnswer -> {
                changes.add("surface:note", answer.value)
            }
        }
    }
}
