package de.hpi.tfm.compatibility.graph.fact.bipartite

import com.typesafe.scalalogging.StrictLogging
import de.hpi.tfm.compatibility.GraphConfig
import de.hpi.tfm.compatibility.graph.fact.internal.InternalFactMatchGraphCreator
import de.hpi.tfm.compatibility.graph.fact.{ConcurrentMatchGraphCreator, FactMatchCreator, TupleReference}
import de.hpi.tfm.compatibility.index.BipartiteTupleIndex
import de.hpi.tfm.data.tfmp_input.table.TemporalFieldTrait
import de.hpi.tfm.data.tfmp_input.table.nonSketch.ValueTransition
import de.hpi.tfm.evaluation.data.GeneralEdge
import de.hpi.tfm.util.RuntimeMeasurementUtil.executionTimeInSeconds

import java.io.{File, PrintWriter}
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContextExecutor, Future}


class BipartiteFactMatchCreator[A](tuplesLeft: IndexedSeq[TupleReference[A]],
                                   tuplesRight: IndexedSeq[TupleReference[A]],
                                   val parentNodesTimestamps:IndexedSeq[LocalDate],
                                   val parentNodesKeys:IndexedSeq[A],
                                   graphConfig:GraphConfig,
                                   nonInformativeValues:Set[A] = Set[A](),
                                   futures:java.util.concurrent.ConcurrentHashMap[String,Future[FactMatchCreator[A]]],
                                   context:ExecutionContextExecutor,
                                   resultDir:File,
                                   fname:String,
                                   prOption:Option[PrintWriter],
                                   toGeneralEdgeFunction:((TupleReference[A],TupleReference[A]) => GeneralEdge),
                                   tupleToNonWcTransitions:Option[Map[TupleReference[A], Set[ValueTransition[A]]]],
                                   isAsynch:Boolean=true
                                  ) extends FactMatchCreator[A](toGeneralEdgeFunction,resultDir, fname,prOption, isAsynch) {

  override def execute() = {
    val index = new BipartiteTupleIndex[A](tuplesLeft,tuplesRight,parentNodesTimestamps,parentNodesKeys,true)
    buildGraph(tuplesLeft,tuplesRight,index,0)
  }

  def productTooBig(size: Int, size1: Int): Boolean = {
    size*size1>50
  }

  def buildGraph(originalInputLeft:IndexedSeq[TupleReference[A]],
                 originalInputRight:IndexedSeq[TupleReference[A]],
                 index: BipartiteTupleIndex[A],
                 recurseDepth:Int):Unit = {
    if(!index.indexFailed){
      val allTuplesLeft = scala.collection.mutable.ArrayBuffer[TupleReference[A]]()
      val allTuplesRight = scala.collection.mutable.ArrayBuffer[TupleReference[A]]()
      index.getBipartiteTupleGroupIterator().foreach{case g => {
        val tuplesLeft = g.tuplesLeft
        val tuplesRight = g.tuplesRight
        buildGraphRecursively(g.chosenTimestamps.toIndexedSeq,g.valuesAtTimestamps, tuplesLeft, tuplesRight,recurseDepth+1)
        //TODO: process Wildcards to others:
        allTuplesLeft ++= tuplesLeft
        allTuplesRight ++= tuplesRight
      }}
      buildGraphRecursively(index.parentTimestamps ++Seq(index.splitT),index.parentKeyValues ++Seq(index.wildcardValues.head),index.wildcardsLeft,index.wildcardsRight,recurseDepth+1)
      buildGraphRecursively(index.parentTimestamps ++Seq(index.splitT),index.parentKeyValues ++Seq(index.wildcardValues.head),index.wildcardsLeft,allTuplesRight.toIndexedSeq,recurseDepth+1)
      buildGraphRecursively(index.parentTimestamps ++Seq(index.splitT),index.parentKeyValues ++Seq(index.wildcardValues.head),allTuplesLeft.toIndexedSeq,index.wildcardsRight,recurseDepth+1)
    } else {
      doPairwiseMatching(originalInputLeft,originalInputRight)
    }
  }

  private def buildGraphRecursively(parentTimestamps:IndexedSeq[LocalDate],
                                    parentValues:IndexedSeq[A],
                                    tuplesLeft: IndexedSeq[TupleReference[A]],
                                    tuplesRight: IndexedSeq[TupleReference[A]],
                                    newRecurseDepth:Int) = {
    if (productTooBig(tuplesLeft.size, tuplesRight.size)) {
      //further index this: new Index
      if(tuplesLeft.size + tuplesRight.size > thresholdForFork){
        val newName = fname + s"_$parallelRecurseCounter"
        val f = BipartiteFactMatchCreator.createAsFuture(futures,
          tuplesLeft,
          tuplesRight,
          parentTimestamps,
          parentValues,
          graphConfig,
          nonInformativeValues,
          context,
          resultDir,
          newName,
          toGeneralEdgeFunction,
          tupleToNonWcTransitions)
        parallelRecurseCounter += 1
        mySubNodeFutures.put(newName,f)
      } else {
        new BipartiteFactMatchCreator[A](
          tuplesLeft,
          tuplesRight,
          parentTimestamps,
          parentValues,
          graphConfig,
          nonInformativeValues,
          futures,
          context,
          resultDir,
          fname + s"_rI_$internalRecurseCounter",
          Some(pr),
          toGeneralEdgeFunction,
          tupleToNonWcTransitions,
          false)
        internalRecurseCounter+=1
      }
    } else {
      doPairwiseMatching(tuplesLeft, tuplesRight)
    }
  }

  private def doPairwiseMatching(tuplesLeft: IndexedSeq[TupleReference[A]], tuplesRight:IndexedSeq[TupleReference[A]]) = {
    //we construct a graph as an adjacency list:
    //pairwise matching to find out the edge-weights:
    if(tuplesLeft.size>0 && tuplesRight.size>0) {
      for (i <- 0 until tuplesLeft.size) {
        for (j <- 0 until tuplesRight.size) {
          val ref1 = tuplesLeft(i)
          val ref2 = tuplesRight(j)
          if(!tupleToNonWcTransitions.isDefined || tupleToNonWcTransitions.get(ref1).exists(t => tupleToNonWcTransitions.get(ref2).contains(t))){
            serializeIfMatch(ref1,ref2,pr)
          }
        }
      }
    }
  }

  override def getGraphConfig: GraphConfig = graphConfig
}
object BipartiteFactMatchCreator extends StrictLogging {
  def createAsFuture[A](futures: ConcurrentHashMap[String,Future[FactMatchCreator[A]]],
                        tuplesLeft: IndexedSeq[TupleReference[A]],
                        tuplesRight: IndexedSeq[TupleReference[A]],
                        parentTimestamps: IndexedSeq[LocalDate],
                        parentValues: IndexedSeq[A],
                        graphConfig: GraphConfig,
                        nonInformativeValues: Set[A],
                        context: ExecutionContextExecutor,
                        resultDir: File,
                        fname: String,
                        toGeneralEdgeFunction: (TupleReference[A], TupleReference[A]) => GeneralEdge,
                        tupleToNonWcTransitions: Option[Map[TupleReference[A], Set[ValueTransition[A]]]]) = {
    val f = Future {
      new BipartiteFactMatchCreator[A](
        tuplesLeft,
        tuplesRight,
        parentTimestamps,
        parentValues,
        graphConfig,
        nonInformativeValues,
        futures,
        context,
        resultDir,
        fname,
        None,
        toGeneralEdgeFunction,
        tupleToNonWcTransitions)
    }(context)
    ConcurrentMatchGraphCreator.setupFuture(f,fname,futures,context)
    f
  }

}
