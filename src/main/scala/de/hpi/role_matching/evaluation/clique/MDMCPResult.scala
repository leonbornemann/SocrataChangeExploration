package de.hpi.role_matching.evaluation.clique

import de.hpi.role_matching.compatibility.graph.representation.SubGraph
import de.hpi.role_matching.clique_partitioning.RoleMerge

import java.io.File
import scala.io.Source

class MDMCPResult(c: SubGraph,
                  resultFile: File,
                  partitionVertexFile: File) {

  val indexToVertex = Source.fromFile(partitionVertexFile).getLines()
    .toIndexedSeq
    .zipWithIndex
    .map(t => (t._2,t._1.toInt))
    .toMap
  val resultFileString = Source.fromFile(resultFile).getLines().toIndexedSeq
  val linesOFResultFile = Source.fromFile(resultFile).getLines()
    .toIndexedSeq
  val startIndex = linesOFResultFile.indexWhere(_ == "Final Best Solution!!!!:")+1
  val cliqueIdToVertices = linesOFResultFile
    .slice(startIndex,linesOFResultFile.size)
    .zipWithIndex
    .map(t => (t._2,t._1))
    .groupMap(_._2)(t => {
      if(!indexToVertex.contains(t._1))
        println()
      indexToVertex(t._1)
    })

  def getScore(clique: IndexedSeq[Int]): Double = {
    var score = 0.0
    for (i <- 0 until clique.size){
      val v = clique(i)
      for (j <- (i+1) until clique.size){
        val w = clique(j)
        val weight = c.getEdgeWeight(v,w)
        score+=weight
      }
    }
    score
  }

  val cliques = cliqueIdToVertices
    .values
    .toIndexedSeq
    .map(cc => RoleMerge(cc.toSet,getScore(cc)))

}
