package de.hpi.tfm.data.wikipedia.infobox

import de.hpi.tfm.data.tfmp_input.table.nonSketch.{FactLineage, FactLineageWithHashMap}
import de.hpi.tfm.data.wikipedia.infobox.WikipediaInfoboxStatisticsLine.years

import java.time.LocalDate
import scala.collection.mutable

case class WikipediaInfoboxStatisticsLine(template: Option[String], pageID: BigInt, key: String, p: String, lineage: FactLineageWithHashMap) {

  def getRealChangeCountInRange(fl: mutable.TreeMap[LocalDate, Any]) = {
    val withoutWildcard =fl
      .filter(v => !FactLineage.isWildcard(v))
      .toIndexedSeq.zipWithIndex
    withoutWildcard.filter{case (v,i) => i!=0 && v!=withoutWildcard(i-1)}.size
  }

  def toCleanString(value: Any) = value.toString.replace(",",";")

  def getCSVLine = {
    val fl = FactLineage.fromSerializationHelper(lineage).lineage
    val nonWcValues = getNonWCValuesInRange(fl)
    val totalRealChanges = getRealChangeCountInRange(fl)
    val nonWcValuesPerYear = years.map(i => getNonWCValuesInRange(fl.range(LocalDate.ofYearDay(i, 1), LocalDate.ofYearDay(i + 1, 1))))
    val realChangesPerYear = years.map( i => getRealChangeCountInRange(fl.range(LocalDate.ofYearDay(i,1),LocalDate.ofYearDay(i+1,1))))
    (Seq(template.getOrElse(""),
      pageID,
      key,
      p,
      nonWcValues,
      totalRealChanges
    ) ++ nonWcValuesPerYear ++ realChangesPerYear).map(toCleanString(_)).mkString(",")
  }

  private def getNonWCValuesInRange(fl: mutable.TreeMap[LocalDate, Any]) = {
    fl.values.filter(v => !FactLineage.isWildcard(v)).size
  }
}
object WikipediaInfoboxStatisticsLine{

  def getSchema = Seq("infoboxTemplate",
    "pageID",
    "infoboxKey",
    "property",
    "#totalNonWildcardValues",
    "#totalRealChanges") ++ years.map( i => s"#nonWildcardValuesInYear_${i}") ++ years.map( i => s"#realChangesInYear_${i}")

  val years = InfoboxRevisionHistory.EARLIEST_HISTORY_TIMESTAMP.getYear until InfoboxRevisionHistory.LATEST_HISTORY_TIMESTAMP.getYear
}