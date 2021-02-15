package de.hpi.dataset_versioning.db_synthesis.baseline.matching

import de.hpi.dataset_versioning.db_synthesis.preparation.{FieldLineageGraphEdge, FieldLineageMergeabilityGraph, ValueTransition}

import scala.collection.mutable

class FieldLineageGraph[A] {

  def toFieldLineageMergeabilityGraph(includeEvidenceSet:Boolean=false) = {
    FieldLineageMergeabilityGraph(edges.toIndexedSeq.map(e => {
      var evidenceSet:Option[collection.IndexedSeq[(ValueTransition,Int)]] = None
      if(includeEvidenceSet) {
        val tupA = e.tupleReferenceA.getDataTuple.head
        val tupB = e.tupleReferenceB.getDataTuple.head
        evidenceSet = Some(tupA.getOverlapEvidenceMultiSet(tupB).toIndexedSeq)
        if(evidenceSet.get.map(_._2).sum!=e.evidence){
          println()
        }
      }
      FieldLineageGraphEdge(e.tupleReferenceA.toIDBasedTupleReference, e.tupleReferenceB.toIDBasedTupleReference, e.evidence,evidenceSet)
    }))
  }

  val edges = mutable.HashSet[General_1_to_1_TupleMatching[A]]()

  def getTupleMatchOption(ref1:TupleReference[A], ref2:TupleReference[A]) = {
    val left = ref1.getDataTuple.head
    val right = ref2.getDataTuple.head // this is a map with all LHS being fields from tupleA and all rhs being fields from tuple B
    val evidence = left.getOverlapEvidenceCount(right)
    if (evidence == -1) {
      None
    } else {
      Some(General_1_to_1_TupleMatching(ref1,ref2, evidence))
    }
  }


}
