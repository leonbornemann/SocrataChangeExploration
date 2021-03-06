package de.hpi.role_matching.compatibility.graph.creation.bipartite

import com.typesafe.scalalogging.StrictLogging

/** *
 * Creates edges between two associations
 */
object DirectBipartiteFactMatchGraphCreationMain extends App with StrictLogging {
  assert(false) //TODO: correct the following to work with the parallelized variant!

//  IOService.socrataDir = args(0)
//  val subdomain = "org.cityofchicago"
//  val id1 = AssociationIdentifier.fromShortString(subdomain, "wrvz-psew.0_125")
//  val id2 = AssociationIdentifier.fromShortString(subdomain, "ijzp-q8t2.0_21")
//  val tableLeft = SurrogateBasedSynthesizedTemporalDatabaseTableAssociation.loadFromStandardOptimizationInputFile(id1)
//  val tableRight = SurrogateBasedSynthesizedTemporalDatabaseTableAssociation.loadFromStandardOptimizationInputFile(id2)
//  val leftTableHasChanges = GLOBAL_CONFIG.CHANGE_COUNT_METHOD.countChanges(tableLeft)._1 > 0
//  val rightTableHasChanges = GLOBAL_CONFIG.CHANGE_COUNT_METHOD.countChanges(tableRight)._1 > 0
//  val minEvidence = args(2).toInt
//  val timeRangeStart = LocalDate.parse(args(3))
//  val timeRangeEnd = LocalDate.parse(args(4))
//  val graphConfig = GraphConfig(minEvidence,timeRangeStart,timeRangeEnd)
//  if (leftTableHasChanges && rightTableHasChanges) {
//    val matchGraph = new BipartiteFactMatchCreator(tableLeft.tupleReferences, tableRight.tupleReferences,graphConfig)
//      .toFieldLineageMergeabilityGraph(true)
//    logger.debug(s"Found ${matchGraph.edges.size} edges of which ${matchGraph.edges.filter(_.evidence > 0).size} have more than 0 evidence ")
//    if (matchGraph.edges.size > 0)
//      matchGraph.writeToStandardFile()
//  }
}
