package de.hpi.tfm.data.wikipedia.infobox.evaluation

import de.hpi.tfm.data.wikipedia.infobox.fact_merging.VerticesOrdered
import de.hpi.tfm.evaluation.data.{IdentifiedTupleMerge, SLimGraph}
import de.hpi.tfm.fact_merging.optimization.SubGraph
import de.hpi.tfm.io.IOService

import java.io.{File, PrintWriter}
import scala.io.Source

object CliqueBasedEvaluationGreedyVSMDMCP extends App {
  val mergeDirGreedy = args(0)
  val mergeDir = new File(args(1))
  val mergeDirMappingDir = new File(args(2))
  val graphFile = args(3)
  val verticesOrderedFile = args(4)
  val resultFile = args(5)
  val slimGraph = SLimGraph.fromJsonFile(graphFile)
  val verticesOrdered = VerticesOrdered.fromJsonFile(verticesOrderedFile)
  val mergeFilesFromMDMCP = mergeDir.listFiles().map(f => (f.getName,f)).toMap
  val partitionVertexFiles = mergeDirMappingDir.listFiles().map(f => (f.getName,f)).toMap
  //assert(mergeFilesFromMDMCP.keySet==partitionVertexFiles.keySet)
  val pr = new PrintWriter(resultFile)
  val cliqueAnalyser = new CliqueAnalyser(pr,verticesOrdered)
  cliqueAnalyser.serializeSchema()
  val mdmcpMerges = mergeFilesFromMDMCP.foreach{case (fname,mf) => {
    val cliquesMDMCP = new MDMCPResult(new SubGraph(slimGraph.transformToOptimizationGraph),mf,partitionVertexFiles(fname)).cliques
    val componentName = fname.split("\\.")(0)
    val cliquesGreedy = IdentifiedTupleMerge.fromJsonObjectPerLineFile(mergeDirGreedy + s"/$componentName.json")
    cliqueAnalyser.addResultTuples(cliquesGreedy,componentName,"greedy")
    cliqueAnalyser.addResultTuples(cliquesMDMCP,componentName,"MDMCP")
  }}
  pr.close()
}
