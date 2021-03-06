package de.hpi.role_matching.evaluation.edge

import com.typesafe.scalalogging.StrictLogging
import de.hpi.socrata.tfmp_input.table.nonSketch.ValueTransition
import de.hpi.role_matching.compatibility.GraphConfig
import de.hpi.role_matching.compatibility.graph.representation.simple.GeneralEdge
import de.hpi.role_matching.scoring.TFIDFMapStorage

import java.io.{File, PrintWriter}

class EdgeAnalyser(edges: collection.Seq[GeneralEdge],
                   trainGraphConfig:GraphConfig,
                   TIMESTAMP_RESOLUTION_IN_DAYS:Int,
                   nonInformativeValues:Set[Any],
                   TFIDFMapStorage: Option[TFIDFMapStorage]) extends StrictLogging{

  val transitionHistogramForTFIDF:Map[ValueTransition[Any],Int] = {
    if(TFIDFMapStorage.isDefined) TFIDFMapStorage.get.asMap else  GeneralEdge.getTransitionHistogramForTFIDF(edges,TIMESTAMP_RESOLUTION_IN_DAYS)
  }
  val lineageCount:Int = transitionHistogramForTFIDF.size

  def toCSVLine(e: GeneralEdge) = {
    val line = e.toGeneralEdgeStatRow(TIMESTAMP_RESOLUTION_IN_DAYS,trainGraphConfig,nonInformativeValues,transitionHistogramForTFIDF,lineageCount)
//    if(!line.remainsValid && line.isInteresting && line.trainMetrics(0) > 0.7 && line.trainMetrics(0) < 0.78 && !line.isNumeric) {
//      e.printTabularEventLineageString
//      println(line.getSchema)
//      println(line.toCSVLine)
//      println()
//    }
    line.toCSVLine
  }

  def toCsvFile(f:File): Unit = {
    val pr = new PrintWriter(f)
    logger.debug(s"Found ${edges.size} edges")
    pr.println(edges.head.toGeneralEdgeStatRow(TIMESTAMP_RESOLUTION_IN_DAYS,trainGraphConfig,nonInformativeValues,transitionHistogramForTFIDF,lineageCount).getSchema.mkString(","))
    var done = 0
    edges.foreach(e => {
      pr.println(toCSVLine(e))
      done+=1
      if(done%1000==0)
        logger.debug(s"Done with $done ( ${100*done/edges.size.toDouble}%)")
    })
    pr.close()
  }

}
