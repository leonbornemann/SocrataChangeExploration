package de.hpi.role_matching.compatibility.graph.representation.slim

import com.typesafe.scalalogging.StrictLogging
import de.hpi.socrata.{JsonReadable, JsonWritable}
import de.hpi.role_matching.compatibility.graph.representation.simple.GeneralEdge
import de.hpi.role_matching.scoring.{EdgeScore, MultipleEventWeightScore}
import scalax.collection.Graph
import scalax.collection.edge.WUnDiEdge

import scala.collection.mutable

//adjacency list only contains every edge once (from the lower index to the higher index)! First element of adjacency list is the node index, second is the score
case class SLimGraph(verticesOrdered: IndexedSeq[String], adjacencyList: collection.Map[Int, collection.Map[Int, Float]]) extends JsonWritable[SLimGraph] with StrictLogging{

  val minValue = -1.0f
  val maxValue = 1.0f

  assert(adjacencyList.forall(_._2.forall(t => t._2 >= minValue && t._2<=maxValue)))

  //serializes this as an adjacencyMatrix file
//  def serializeToMDMCPInputFile(f:File) = {
//    val pr = new PrintWriter(f)
//    pr.println(s" ${verticesOrdered.size}")
//    verticesOrdered
//      .zipWithIndex
//      .foreach{case (_,i) => {
//        val neighbors = adjacencyList.getOrElse(i,Map[Int,Float]())
//        val weights = Seq(0) ++ ((i+1) until verticesOrdered.size).map{ j =>
//          val weight:Float = neighbors.getOrElse(j,Float.MinValue)
//          getScoreAsInt(weight)
//        }
//        pr.println(weights.mkString("  "))
//        if(i%1000==0){
//          logger.debug(s"Done with $i (${100*i/verticesOrdered.size.toDouble}%)")
//        }
//      }}
//    pr.close()
//  }

  def transformToOptimizationGraph = {
    val newVertices = scala.collection.mutable.HashSet[Int]() ++ (0 until verticesOrdered.size)
    val newEdges = adjacencyList.flatMap{case (v1,adjList) => {
      adjList.map{case (v2,weight) => WUnDiEdge(v1,v2)(weight)}
    }}
    val graph = Graph.from(newVertices,newEdges)
    graph
  }

}
object SLimGraph extends JsonReadable[SLimGraph] with StrictLogging {

  def getEdgeOption(e:GeneralEdge, scoringFunction: EdgeScore[Any], scoringFunctionThreshold: Double) = {
    val score = scoringFunction.compute(e.v1.factLineage.toFactLineage,e.v2.factLineage.toFactLineage)
    val weight = (score-scoringFunctionThreshold).toFloat
    if(weight==Float.NegativeInfinity) {
      None
    } else {
      //val scoreAsInt = getScoreAsInt(e,scoringFunction,scoringFunctionThreshold)
      assert(e.v1.id!=e.v2.id)
      if(e.v1.id<e.v2.id) {
        Some((e.v1.id,e.v2.id,weight))
      } else {
        Some((e.v2.id,e.v1.id,weight))
      }
    }
  }

  def fromVerticesAndEdges(vertices: collection.Set[String], adjacencyList: collection.Map[String, collection.Map[String, Float]]) = {
    val verticesOrdered = vertices.toIndexedSeq.sorted
    val nameToIndexMap = verticesOrdered
      .zipWithIndex
      .toMap
    val adjacencyListAsInt = adjacencyList.map{case (stringKey,stringKeyMap) =>{
      (nameToIndexMap(stringKey),stringKeyMap.map{case (k,v) => (nameToIndexMap(k),v)})
    }}
    SLimGraph(verticesOrdered,adjacencyListAsInt)
  }

  def fromGeneralEdgeIterator(edges: GeneralEdge.JsonObjectPerLineFileIterator, scoringFunction: MultipleEventWeightScore[Any], scoringFunctionThreshold: Double) = {
    val vertices = collection.mutable.HashSet[String]()
    val adjacencyList = collection.mutable.HashMap[String, mutable.HashMap[String, Float]]()
    var count = 0
    edges.foreach(e => {
      vertices.add(e.v1.id)
      vertices.add(e.v2.id)
      val score = scoringFunction.compute(e.v1.factLineage.toFactLineage,e.v2.factLineage.toFactLineage)
      val edge = getEdgeOption(e,scoringFunction,scoringFunctionThreshold)
      if(edge.isDefined){
        adjacencyList.getOrElseUpdate(edge.get._1,mutable.HashMap[String,Float]()).put(edge.get._2,edge.get._3)
      }
      count+=1
      if(count%100000==0)
        logger.debug(s"Done with $count edges")
    })
    fromVerticesAndEdges(vertices,adjacencyList)
  }

}
