package de.hpi.tfm.evaluation.data

import de.hpi.tfm.compatibility.GraphConfig
import de.hpi.tfm.data.socrata.{JsonReadable, JsonWritable}
import de.hpi.tfm.data.tfmp_input.table.nonSketch.FactLineage
import de.hpi.tfm.evaluation.wikipediaStyle.GeneralEdgeStatRow
import de.hpi.tfm.util.TableFormatter

case class GeneralEdge(v1:IdentifiedFactLineage, v2:IdentifiedFactLineage) extends JsonWritable[GeneralEdge] {

  def toGeneralEdgeStatRow(granularityInDays: Int, trainGraphConfig: GraphConfig) = {
    GeneralEdgeStatRow(granularityInDays,trainGraphConfig,v1.id,v2.id,v1.factLineage.toFactLineage,v2.factLineage.toFactLineage)
  }

  def printTabularEventLineageString = {
    val dates = v1.factLineage.toFactLineage.lineage.keySet//.filter(v => !FactLineage.isWildcard(v._2) && v._2!="").keySet
    val dates2 = v2.factLineage.toFactLineage.lineage.keySet//.filter(v => !FactLineage.isWildcard(v._2) && v._2!="").keySet
    val allDates = dates.union(dates2).toIndexedSeq.sorted
    val header = Seq("") ++ allDates
    val cells1 = Seq(v1.id) ++ allDates.map(t => v1.factLineage.toFactLineage.valueAt(t)).map(v => if(FactLineage.isWildcard(v)) "_" else v)
    val cells2 = Seq(v2.id) ++ allDates.map(t => v2.factLineage.toFactLineage.valueAt(t)).map(v => if(FactLineage.isWildcard(v)) "_" else v)
    TableFormatter.printTable(header,Seq(cells1,cells2))
  }

}

object GeneralEdge extends JsonReadable[GeneralEdge]
