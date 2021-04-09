package de.hpi.tfm.fact_merging.metrics

import de.hpi.tfm.compatibility.graph.fact.TupleReference

trait EdgeScore {

  def name:String
  def compute[A](tr1: TupleReference[A], tr2: TupleReference[A]) :Double

}