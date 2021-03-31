package de.hpi.tfm.compatibility

import com.typesafe.scalalogging.StrictLogging
import de.hpi.tfm.compatibility.graph.association.{AssociationGraphEdgeCandidate, AssociationMatch}
import de.hpi.tfm.compatibility.graph.fact.TupleReference
import de.hpi.tfm.compatibility.index._
import de.hpi.tfm.data.tfmp_input.association.AssociationIdentifier
import de.hpi.tfm.data.tfmp_input.table.TemporalDatabaseTableTrait
import de.hpi.tfm.data.tfmp_input.table.nonSketch.ValueTransition
import de.hpi.tfm.data.tfmp_input.table.sketch.SurrogateBasedSynthesizedTemporalDatabaseTableAssociationSketch
import de.hpi.tfm.fact_merging.config.GLOBAL_CONFIG
import de.hpi.tfm.io.DBSynthesis_IOService
import de.hpi.tfm.util.RuntimeMeasurementUtil.executionTimeInSeconds

import java.io.{FileWriter, PrintWriter}
import scala.collection.mutable

/***
 * Lazily in
 * @param unmatchedAssociations
 * @param heuristicMatchCalulator
 * @param recurseLogDepth
 * @param autoFlush
 * @param indexProcessingMode
 */
class AssociationEdgeCandidateFinder(unmatchedAssociations: collection.Set[SurrogateBasedSynthesizedTemporalDatabaseTableAssociationSketch],
                                     graphConfig: GraphConfig,
                                     subdomain:String,
                                     recurseLogDepth:Int = 0,
                                     autoFlush:Boolean = false) extends StrictLogging {
  logger.debug(s"Starting with graphConfig $graphConfig")
  logger.debug(s"Starting association clustering with ${unmatchedAssociations.size} associations --> ${gaussSum(unmatchedAssociations.size-1)} matches possible")
  val filterByTransitionOverlap = graphConfig.minEvidence>0
  var indexTimeInSeconds:Double = 0.0
  var matchTimeInSeconds:Double = 0.0
  var matchSkips = 0
  var matchesBasedOnWildcards = 0
  var pairwiseInnerLoopExecutions = 0
  var calculateAndMatchIfNotPresentCalls = 0

  var edgeCandidates = Some(new mutable.HashSet[Set[AssociationIdentifier]]())

  var tableGraphEdges = mutable.HashSet[AssociationGraphEdgeCandidate]()

  val adjacencyList = unmatchedAssociations
    .map(a => (a.asInstanceOf[TemporalDatabaseTableTrait[Int]],mutable.HashMap[TemporalDatabaseTableTrait[Int],AssociationMatch[Int]]()))
    .toMap
  val matchesWithZeroScore = mutable.HashSet[Set[TemporalDatabaseTableTrait[Int]]]()
  val nonWildcardValueTransitionSets: Map[TemporalDatabaseTableTrait[Int], Set[ValueTransition[Int]]] = if(filterByTransitionOverlap) unmatchedAssociations
    .map(a => (a.asInstanceOf[TemporalDatabaseTableTrait[Int]], a.nonWildcardValueTransitions))
    .toMap else Map()
  var nMatchesComputed = 0
  var processedNodes = 0
  var topLvlIndexSize = -1
  initMatchGraph()

  def matchWasAlreadyCalculated(firstMatchPartner: TemporalDatabaseTableTrait[Int], secondMatchPartner: TemporalDatabaseTableTrait[Int]) = {
    val existsWithScoreGreater0 = adjacencyList(firstMatchPartner).contains(secondMatchPartner)
    if(existsWithScoreGreater0) assert(adjacencyList(secondMatchPartner).contains(firstMatchPartner))
    existsWithScoreGreater0 || matchesWithZeroScore.contains(Set(firstMatchPartner,secondMatchPartner))
  }

  def executePairwiseMatching(groupsWithTupleIndices: collection.IndexedSeq[TemporalDatabaseTableTrait[Int]]) = {
    for (i <- 0 until groupsWithTupleIndices.size) {
      for (j <- (i + 1) until groupsWithTupleIndices.size) {
        pairwiseInnerLoopExecutions +=1
        val firstMatchPartner = groupsWithTupleIndices(i)
        val secondMatchPartner = groupsWithTupleIndices(j)
        if(firstMatchPartner.getUnionedOriginalTables.head != secondMatchPartner.getUnionedOriginalTables.head) {
          //can only happen due to a bug in change exporting currently
          calculateAndMatchIfNotPresent(firstMatchPartner, secondMatchPartner)
        }
      }
    }
  }

  def hasCommonTransition(firstMatchPartner: TemporalDatabaseTableTrait[Int], secondMatchPartner: TemporalDatabaseTableTrait[Int]): Boolean = {
    nonWildcardValueTransitionSets(firstMatchPartner).intersect(nonWildcardValueTransitionSets(secondMatchPartner)).size>=1//get changeset in first and second and compare!
  }

  private def calculateAndMatchIfNotPresent(firstMatchPartner: TemporalDatabaseTableTrait[Int],
                                            secondMatchPartner: TemporalDatabaseTableTrait[Int]) = {
    calculateAndMatchIfNotPresentCalls +=1
    val toAdd = Set(firstMatchPartner.getUnionedOriginalTables.head, secondMatchPartner.getUnionedOriginalTables.head)
    edgeCandidates.get.add(toAdd)
  }

  def gaussSum(n: Int) = n*(n+1) / 2

  def logRecursionWhitespacePrefix(depth:Int) = {
    var curRepeat = depth
    val sb = new StringBuilder()
    while(curRepeat>0) {
      sb.append("  ")
      curRepeat -=1
    }
    sb.toString()
  }

  def nonZeroScoreMatches = edgeCandidates.get.size
  def maybeLog(str: String, recurseDepth: Int) = {
    if(recurseDepth<=recurseLogDepth)
      logger.debug(str)
  }

  def calculatePotentialWildcardToOtherMatches(wildcards:IndexedSeq[TupleReference[Int]],
                                               nonWildcards:IndexedSeq[TupleReference[Int]]) = {
    val wildcardTables = wildcards.map(_.table).toSet.toIndexedSeq
    val tablesToMatch = nonWildcards.map(_.table).toSet.toIndexedSeq
    assert(wildcardTables.toSet.intersect(tablesToMatch.toSet).isEmpty)
    val (index,indexTime) = executionTimeInSeconds(new BipartiteTupleIndex(wildcards,nonWildcards))
    indexTimeInSeconds +=indexTime
    if(index.indexFailed){
      wildcardTables.foreach(wc => {
        //calculate matches to all other association tables:
        tablesToMatch
          .foreach(a => {
            calculateAndMatchIfNotPresent(wc,a)
          })
      })
    } else {
      val groupIterator = index.getBipartiteTupleGroupIterator()
      val wildcardsLeft = index.wildcardsLeft.map(_.table).toSet
      val wildcardsRight = index.wildcardsRight.map(_.table).toSet
      groupIterator.foreach(g => {
        val tablesLeft = g.tuplesLeft.map(_.table).toSet
        val tablesRight = g.tuplesRight.map(_.table).toSet
        if(tablesLeft.size>0){
          matchAllInBipartitePair(tablesLeft,wildcardsRight)
        }
        if(tablesRight.size>0){
          matchAllInBipartitePair(tablesRight,wildcardsLeft)
        }
        if(tablesLeft.size>0 && tablesRight.size>0){
          matchAllInBipartitePair(tablesLeft,tablesRight)
        }
      })
    }
  }

  private def matchAllInBipartitePair(tablesLeft: Set[TemporalDatabaseTableTrait[Int]],tablesRight:Set[TemporalDatabaseTableTrait[Int]]) = {
    tablesLeft.foreach(tLeft => {
      tablesRight.foreach(tRight => {
        calculateAndMatchIfNotPresent(tLeft, tRight)
      })
    })
  }

  def executeMatchesInIterator(oldIndex:IterableTupleIndex[Int],
                               recurseDepth:Int):Unit = {
    val isTopLvlCall = recurseDepth==0
    val it = oldIndex.tupleGroupIterator(true)
    it.foreach{case g => {
      val potentialTupleMatches = g.tuplesInNode
      val groupsWithTupleIndices = potentialTupleMatches.groupMap(t => t.table)(t => t.rowIndex).toIndexedSeq
      if(groupsWithTupleIndices.size>1) {
        maybeLog(s"${logRecursionWhitespacePrefix(recurseDepth)}Processing group ${g.valuesAtTimestamps} with ${groupsWithTupleIndices.size} tables [Recurse Depth:$recurseDepth]",recurseDepth)
        maybeLog(s"Index Time:${f"$indexTimeInSeconds%1.3f"}s, Match time:${f"$matchTimeInSeconds%1.3f"}s, 0-score matches: ${matchesWithZeroScore.size}, non-zero score matches: ${nonZeroScoreMatches}, match-Skips:$matchSkips",recurseDepth)
      }
      if(potentialTupleMatches.exists(_.getDataTuple.head.countChanges(GLOBAL_CONFIG.CHANGE_COUNT_METHOD)._1<=0)){
        logger.debug("Really weird, we found a tuple with zero changes, when we were not supposed to")
      }
      if(groupsWithTupleIndices.size>2){
        assert(g.chosenTimestamps.size==g.valuesAtTimestamps.size)
        val (newIndex,time) = executionTimeInSeconds(new TupleSetIndex[Int]((potentialTupleMatches).toIndexedSeq,
          g.chosenTimestamps.toIndexedSeq,
          g.valuesAtTimestamps,
          potentialTupleMatches.head.table.wildcardValues.toSet,
          true))
        indexTimeInSeconds +=time
        if(newIndex.indexBuildWasSuccessfull) {
          //maybeLog(s"${logRecursionWhitespacePrefix(recurseDepth)}Starting recursive call because size ${groupsWithTupleIndices.size} is too large [Recurse Depth:$recurseDepth]",recurseDepth)
          executeMatchesInIterator(newIndex,recurseDepth+1)
        } else {
          maybeLog(s"${logRecursionWhitespacePrefix(recurseDepth)}Executing pairwise matching with ${groupsWithTupleIndices.size} because we can't refine the index anymore [Recurse Depth:$recurseDepth]",recurseDepth)
          executePairwiseMatching(groupsWithTupleIndices.map(_._1))
        }
      } else {
        executePairwiseMatching(groupsWithTupleIndices.map(_._1))
      }
      if(isTopLvlCall)
        processedNodes +=1
      if(isTopLvlCall && processedNodes % 1000==0){
        logger.debug(s"FInished $processedNodes top lvl nodes out of $topLvlIndexSize (${100*processedNodes/topLvlIndexSize.toDouble}%)")
      }
    }}
    //aggregate to single WC-Bucket:
    val wildCardBucket = TupleGroup(oldIndex.wildcardBuckets.head.chosenTimestamps,
      oldIndex.wildcardBuckets.head.valuesAtTimestamps,
      IndexedSeq(),
      oldIndex.wildcardBuckets.flatMap(_.wildcardTuples))
    if(!wildCardBucket.wildcardTuples.isEmpty){
      val wildcardTableSet = wildCardBucket
        .wildcardTuples
        .map(_.table)
        .toSet
      val wildcardTables = wildcardTableSet.toIndexedSeq
      //TODO: build double-sided index to determine matches
      if(isTopLvlCall) {
        logger.debug(s"Finished Index-Based initial matching, resulting in ${nMatchesComputed} checked matches, of which ${adjacencyList.map(_._2.size).sum / 2} have a score > 0")
        logger.debug("Begin executing Wildcard matches FOR TOP-LVL")
        logger.debug(s"Found ${wildcardTables.size} wildcard tables")
      }
      val nonWildCards = oldIndex
        .tupleGroupIterator(true)
        .toIndexedSeq
        .flatMap(_.tuplesInNode.filter(tr => !wildcardTableSet.contains(tr.table)))
      calculatePotentialWildcardToOtherMatches(wildCardBucket.wildcardTuples.toIndexedSeq,nonWildCards)
      if(isTopLvlCall){
        logger.debug(s"Finished combining Wildcard-Bucket with all other Associations")
      }
      //WC-TO-WC matches:
      val (newIndex,time) = executionTimeInSeconds(new TupleSetIndex[Int]((wildCardBucket.wildcardTuples.toIndexedSeq),
        wildCardBucket.chosenTimestamps.toIndexedSeq,
        wildCardBucket.valuesAtTimestamps,
        wildCardBucket.wildcardTuples.head.table.wildcardValues.toSet,
        true))
      indexTimeInSeconds += time
      if(newIndex.indexBuildWasSuccessfull)
        executeMatchesInIterator(newIndex,recurseDepth+1)
      else
        executePairwiseMatching(wildcardTableSet.toIndexedSeq)
    }
  }

  def initMatchGraph() = {
    logger.debug("Starting Index-Based initial match computation")
    val indexBuilder = new MostDistinctTimestampIndexBuilder[Int](unmatchedAssociations.map(_.asInstanceOf[TemporalDatabaseTableTrait[Int]]))
    val (index,time) = executionTimeInSeconds(indexBuilder.buildTableIndexOnNonKeyColumns())
    indexTimeInSeconds +=time
    topLvlIndexSize = index.numLeafNodes
    logger.debug(s"starting to iterate through ${topLvlIndexSize} index leaf nodes")
    executeMatchesInIterator(index,0)
    logger.debug(s"Finished with $pairwiseInnerLoopExecutions num inner pairwise matching loop executions")
    logger.debug(s"Finished with $calculateAndMatchIfNotPresentCalls num calls to calculateAndMatchIfNotPresent")
    logger.debug("Beginning serialization of edge candidates")
    logger.debug(s"Serializing candidate edges for ${edgeCandidates.get.size} candidates")
    val pr = new PrintWriter(DBSynthesis_IOService.getAssociationGraphEdgeCandidateFile(subdomain,graphConfig))
    val weirdEdges = edgeCandidates.get.filter(_.size != 2)
    logger.debug(s"Found ${weirdEdges.size} weird edges:")
    weirdEdges.foreach(s => logger.debug(s.toString()))
    val byID = unmatchedAssociations.map(a => (a.getUnionedOriginalTables.head,a)).toMap
    val filteredByCommonTransition = if(filterByTransitionOverlap) edgeCandidates.get.filter(e => e.size==2 && {
      val ids = e.toSeq
      hasCommonTransition(byID(ids(0)),byID(ids(1)))
    }) else edgeCandidates.get
    logger.debug(s"Retained ${filteredByCommonTransition.size} out of ${edgeCandidates.get.size} edges after filtering by common transition existence")
    filteredByCommonTransition.foreach(s => {
      val res = s.toSeq
      pr.println(AssociationGraphEdgeCandidate(res(0),res(1),Integer.MIN_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE).toJson())
    })
    pr.close()
    logger.debug("Finished serialization of edge candidates")
  }

}
