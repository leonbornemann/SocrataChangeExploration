package de.hpi.tfm.data.wikipedia.infobox.fact_merging

import com.typesafe.scalalogging.StrictLogging
import de.hpi.tfm.data.wikipedia.infobox.fact_merging.EdgeAnalysisMain.args
import de.hpi.tfm.evaluation.data.{GeneralEdge, IdentifiedFactLineage}
import de.hpi.tfm.fact_merging.metrics.TFIDFMapStorage

import java.io.File
import java.time.LocalDate

object TFIDFMapExtraction extends App with StrictLogging{
  logger.debug(s"called with ${args.toIndexedSeq}")
  val inputEdgeFile = args(0)
  val granularityInDays = args(1).toInt
  val resultFile = new File(args(2))
  val edgeIterator = GeneralEdge.iterableFromJsonObjectPerLineFile(inputEdgeFile)
  logger.debug("Finished setting up iterators")
  var count =0
  var nodes = scala.collection.mutable.HashSet[IdentifiedFactLineage]()
  edgeIterator.foreach(e => {
    nodes.add(e.v1)
    nodes.add(e.v2)
    count+=1
    if(count%1000000==0)
      logger.debug(s"Done with $count")
  })
  logger.debug("finished loading nodes")
  val hist = IdentifiedFactLineage.getTransitionHistogramForTFIDFFromVertices(nodes.toSeq,granularityInDays)
  TFIDFMapStorage(hist.toIndexedSeq).toJsonFile(resultFile)
}
