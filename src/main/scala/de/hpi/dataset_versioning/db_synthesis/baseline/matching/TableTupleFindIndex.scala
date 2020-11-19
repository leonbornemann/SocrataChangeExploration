package de.hpi.dataset_versioning.db_synthesis.baseline.matching

import java.time.LocalDate

import de.hpi.dataset_versioning.db_synthesis.baseline.database.TemporalDatabaseTableTrait
import de.hpi.dataset_versioning.db_synthesis.baseline.index.TupleGroup

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class TableTupleFindIndex[A](sketchA: TemporalDatabaseTableTrait[A], sketchB: TemporalDatabaseTableTrait[A]) extends IterableTupleIndex[A]{

  val relevantTimestampsA = getRelevantTimestamps(sketchA)
  val relevantTimestampsB = getRelevantTimestamps(sketchB)


  def getBestTimestamp(relevantTimestampsA: Set[LocalDate], sketch: TemporalDatabaseTableTrait[A]) = {
    relevantTimestampsA.map(ts => {
      val values = (0 until sketch.nrows).map(i => sketch.getDataTuple(i)(0).valueAt(ts)).toSet
      (ts,values.size)
    }).toIndexedSeq
      .sortBy(-_._2)
      .head._1
  }

  val indexableTimestamps = relevantTimestampsA.intersect(relevantTimestampsB)
  if(indexableTimestamps.isEmpty){

  }
  //find best timestamp:
  val bestTimestamp = getBestTimestamp(relevantTimestampsA.intersect(relevantTimestampsB),sketchA)
  val indexA = (0 until sketchA.nrows)
    .groupBy(i => sketchA.getDataTuple(i).head.valueAt(bestTimestamp))
  val wildcardKeys = sketchA.wildcardValues.toSet
  val indexB = (0 until sketchB.nrows)
    .groupBy(i => sketchB.getDataTuple(i).head.valueAt(bestTimestamp))
  val commonGroupsKeys = indexA.keySet.intersect(indexB.keySet).diff(wildcardKeys)

  override def tupleGroupIterator:Iterator[TupleGroup[A]] = new TupleGroupIterator()

  private def getRelevantTimestamps(sketch: TemporalDatabaseTableTrait[A]) = {
    (0 until sketch.nrows).toSet
      .map((i:Int) => {
        val a = sketch.getDataTuple(i)
        assert(a.size == 1)
        a(0).allTimestamps.toSet
      }).flatten
  }

  class TupleGroupIterator() extends Iterator[TupleGroup[A]] {
    val groupIterator = commonGroupsKeys.iterator

    override def hasNext: Boolean = groupIterator.hasNext

    override def next(): TupleGroup[A] = {
      val key = groupIterator.next()
      val fromTableA = indexA(key).map(rID => (sketchA,rID))
      val wildcardsA = wildcardKeys.toIndexedSeq
        .map(k => indexA.getOrElse(k,IndexedSeq()).map(rID => (sketchA,rID)))
        .flatten
      val fromTableB = indexB(key).map(rID => (sketchB,rID))
      val wildcardsB = wildcardKeys.toIndexedSeq
        .map(k => indexB.getOrElse(k,IndexedSeq()).map(rID => (sketchB,rID)))
        .flatten
      TupleGroup(ArrayBuffer(bestTimestamp),IndexedSeq(key),fromTableA++fromTableB,wildcardsA ++ wildcardsB)
    }
  }

}
