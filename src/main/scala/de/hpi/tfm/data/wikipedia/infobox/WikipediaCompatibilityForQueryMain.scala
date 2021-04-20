package de.hpi.tfm.data.wikipedia.infobox

import com.typesafe.scalalogging.StrictLogging
import de.hpi.tfm.compatibility.GraphConfig
import de.hpi.tfm.compatibility.graph.fact.internal.InternalFactMatchGraphCreator
import de.hpi.tfm.data.tfmp_input.association.AssociationIdentifier
import de.hpi.tfm.data.tfmp_input.table.nonSketch.FactLineage

import java.io.{File, PrintWriter}
import java.util.regex.Pattern

object WikipediaCompatibilityForQueryMain extends App with StrictLogging {

  val query = args(0).split(Pattern.quote("&")).toIndexedSeq
  val infoboxHistoryDir = new File(args(1))
  val queryResultDir = new File(args(2))
  val files = infoboxHistoryDir.listFiles()
  logger.debug(s"Found ${files.size} files")
  var processed = 0
  val fulfillsFilter = files.toIndexedSeq.flatMap(f => {
    val res = PaddedInfoboxHistory.fromJsonObjectPerLineFile(f.getAbsolutePath)
      .flatMap(ph => ph.lineages.map{case (p,h) => WikipediaInfoboxValueHistory(ph.pageID,ph.key,p,h)})
      .filter(wiwh => query.forall(s => wiwh.lineage.lineage.values.exists(_.toString.contains(s)))) //all query strings need to be matched in at least one value
    processed+=1
    if(processed%100==0)
      logger.debug(s"finished $processed")
    res
  })
  logger.debug(s"Found ${fulfillsFilter.size} lineages containing all terms in $query")
  val id = new AssociationIdentifier("wikipedia",s"contains all in $query",0,Some(0))
  val attrID = 0
  val table = WikipediaInfoboxValueHistory.toAssociationTable(fulfillsFilter,id,attrID)
  val graphConfig = GraphConfig(0,InfoboxRevisionHistory.EARLIEST_HISTORY_TIMESTAMP,InfoboxRevisionHistory.LATEST_HISTORY_TIMESTAMP)
  logger.debug("Starting compatibility graph creation")
  val edges = new InternalFactMatchGraphCreator( table.tupleReferences,graphConfig)
    .toFieldLineageMergeabilityGraph(false)
    .edges
    .map(e => WikipediaInfoboxValueHistoryMatch(fulfillsFilter(e.tupleReferenceA.rowIndex),fulfillsFilter(e.tupleReferenceB.rowIndex)))
  logger.debug("Finished compatibility graph creation")
  val writer = new PrintWriter(queryResultDir.getAbsolutePath + s"/${query.mkString("_AND_")}.json")
  edges.foreach(m => m.appendToWriter(writer,false,true))
  writer.close()

}
