package de.hpi.wikipedia.data.compatiblity_graph

import com.typesafe.scalalogging.StrictLogging
import de.hpi.socrata.tfmp_input.association.AssociationIdentifier
import de.hpi.role_matching.GLOBAL_CONFIG
import de.hpi.role_matching.compatibility.GraphConfig
import de.hpi.role_matching.compatibility.graph.creation.internal.ConcurrentMatchGraphCreator
import de.hpi.role_matching.compatibility.graph.creation.{FactMatchCreator, TupleReference}
import de.hpi.role_matching.compatibility.graph.representation.simple.GeneralEdge
import de.hpi.role_matching.evaluation.edge.EdgeAnalyser
import de.hpi.wikipedia.data.original.InfoboxRevisionHistory
import de.hpi.wikipedia.data.transformed.WikipediaInfoboxValueHistory

import java.io.File
import java.time.LocalDate
import java.util.regex.Pattern

object CompatibilityGraphByTemplateCreationMain extends App with StrictLogging {
  GLOBAL_CONFIG.STANDARD_TIME_FRAME_START = InfoboxRevisionHistory.EARLIEST_HISTORY_TIMESTAMP
  GLOBAL_CONFIG.STANDARD_TIME_FRAME_END = InfoboxRevisionHistory.LATEST_HISTORY_TIMESTAMP
  val templates = args(0).split(Pattern.quote(";")).toIndexedSeq
  val templateSetString = templates.mkString("&")
  val byTemplateDir = new File(args(1))
  val resultDirEdges = new File(args(2))
  val resultFileStats = new File(args(3))
  val endDateTrainPhase = LocalDate.parse(args(4))
  val timestampResolutionInDays = args(5).toInt
  val nthreads = args(6).toInt
  val thresholdForFork = args(7).toInt
  val maxPairwiseListSizeForSingleThread = args(8).toInt
  val runAnalysisAfter = if (args.size == 10) args(9).toBoolean else false
  FactMatchCreator.thresholdForFork = thresholdForFork
  FactMatchCreator.maxPairwiseListSizeForSingleThread = maxPairwiseListSizeForSingleThread
  GLOBAL_CONFIG.trainTimeEnd = endDateTrainPhase
  GLOBAL_CONFIG.granularityInDays = timestampResolutionInDays
  InfoboxRevisionHistory.setGranularityInDays(timestampResolutionInDays)
  val infoboxHistoryFiles = templates.map(t => new File(byTemplateDir.getAbsolutePath + s"/$t.json"))
  val lineagesComplete = infoboxHistoryFiles.flatMap(f => {
    logger.debug(s"Loading lineages in $f")
    WikipediaInfoboxValueHistory.fromJsonObjectPerLineFile(f.getAbsolutePath)
  })
  val lineagesTrain = lineagesComplete
    .map(h => h.projectToTimeRange(InfoboxRevisionHistory.EARLIEST_HISTORY_TIMESTAMP, endDateTrainPhase))
  val id = new AssociationIdentifier("wikipedia", templateSetString, 0, Some(0))
  val attrID = 0
  val table = WikipediaInfoboxValueHistory.toAssociationTable(lineagesTrain, id, attrID)
  val graphConfig = GraphConfig(0, InfoboxRevisionHistory.EARLIEST_HISTORY_TIMESTAMP, endDateTrainPhase)
  logger.debug("Starting compatibility graph creation")

  def toGeneralEdgeFunction(a: TupleReference[Any], b: TupleReference[Any]) = {
    WikipediaInfoboxValueHistoryMatch(lineagesComplete(a.rowIndex), lineagesComplete(b.rowIndex))
      .toGeneralEdge
  }

  new ConcurrentMatchGraphCreator(table.tupleReferences,
    graphConfig,
    true,
    GLOBAL_CONFIG.nonInformativeValues,
    nthreads,
    resultDirEdges,
    toGeneralEdgeFunction
  )
  private val edgeFiles: Array[File] = resultDirEdges.listFiles()
  logger.debug(s"Finished compatibility graph creation, found ${edgeFiles.size} edge files")
  //
  //  val lines = Source.fromFile(resultDirEdges.getAbsolutePath + "/partition_0.json")
  //    .getLines()
  //    .toIndexedSeq
  //    .zipWithIndex
  //    .foreach(t => {
  //      println(t._2)
  //      GeneralEdge.fromJsonString(t._1)
  //    })

  if (runAnalysisAfter) {
    val generalEdges: IndexedSeq[GeneralEdge] = edgeFiles.flatMap(f => {
      GeneralEdge.fromJsonObjectPerLineFile(f.getAbsolutePath)
    })
    new EdgeAnalyser(generalEdges, graphConfig, timestampResolutionInDays, GLOBAL_CONFIG.nonInformativeValues, None).toCsvFile(resultFileStats)
  }
}
