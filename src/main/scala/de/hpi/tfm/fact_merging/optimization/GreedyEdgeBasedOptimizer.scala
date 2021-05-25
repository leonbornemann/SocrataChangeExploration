package de.hpi.tfm.fact_merging.optimization

import de.hpi.tfm.evaluation.data.{ComponentWiseOptimizer, IdentifiedTupleMerge}
import scalax.collection.Graph
import scalax.collection.edge.WUnDiEdge

import java.io.File

class GreedyEdgeBasedOptimizer(graph: Graph[String, WUnDiEdge],
                               resultFile:File,
                               MIN_EDGE_WEIGHT_THRESHOLD:Double) extends ComponentWiseOptimizer(graph,resultFile) {


  override def mergeComponent(subGraph: Graph[String, WUnDiEdge]): Set[IdentifiedTupleMerge] = {
    val componentOptimizer = new GreedyEdgeWeightOptimizerForComponent(subGraph,MIN_EDGE_WEIGHT_THRESHOLD)
    componentOptimizer.mergeGreedily()
  }
}