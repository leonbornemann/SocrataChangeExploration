package de.hpi.tfm.compatibility.graph.fact.bipartite

import com.typesafe.scalalogging.StrictLogging
import de.hpi.tfm.data.tfmp_input.association.AssociationIdentifier
import de.hpi.tfm.data.tfmp_input.table.nonSketch.SurrogateBasedSynthesizedTemporalDatabaseTableAssociation
import de.hpi.tfm.fact_merging.config.GLOBAL_CONFIG
import de.hpi.tfm.io.IOService

/** *
 * Creates edges between two associations
 */
object DirectBipartiteFactMatchGraphCreationMain extends App with StrictLogging {
  IOService.socrataDir = args(0)
  val subdomain = "org.cityofchicago"
  val id1 = AssociationIdentifier.fromShortString(subdomain, "wrvz-psew.0_125")
  val id2 = AssociationIdentifier.fromShortString(subdomain, "ijzp-q8t2.0_21")
  val tableLeft = SurrogateBasedSynthesizedTemporalDatabaseTableAssociation.loadFromStandardOptimizationInputFile(id1)
  val tableRight = SurrogateBasedSynthesizedTemporalDatabaseTableAssociation.loadFromStandardOptimizationInputFile(id2)
  val leftTableHasChanges = GLOBAL_CONFIG.CHANGE_COUNT_METHOD.countChanges(tableLeft)._1 > 0
  val rightTableHasChanges = GLOBAL_CONFIG.CHANGE_COUNT_METHOD.countChanges(tableRight)._1 > 0
  if (leftTableHasChanges && rightTableHasChanges) {
    val matchGraph = new BipartiteFactMatchCreator(tableLeft.tupleReferences, tableRight.tupleReferences)
      .toFieldLineageMergeabilityGraph(true)
    logger.debug(s"Found ${matchGraph.edges.size} edges of which ${matchGraph.edges.filter(_.evidence > 0).size} have more than 0 evidence ")
    if (matchGraph.edges.size > 0)
      matchGraph.writeToStandardFile()
  }
  //pubx-yq2d.0_17,pubx-yq2d.0_2
}