package de.hpi.socrata.`export`

import com.typesafe.scalalogging.StrictLogging
import de.hpi.socrata.change.temporal_tables.TemporalTable
import de.hpi.socrata.change.temporal_tables.attribute.{AttributeLineage, SurrogateAttributeLineage}
import de.hpi.socrata.io.Socrata_IOService
import de.hpi.socrata.tfmp_input.GlobalSurrogateRegistry
import de.hpi.socrata.tfmp_input.association.{AssociationIdentifier, AssociationSchema}
import de.hpi.socrata.tfmp_input.factLookup.{FactLookupTable, FactTableRow}
import de.hpi.socrata.tfmp_input.table.nonSketch.{FactLineage, SurrogateBasedSynthesizedTemporalDatabaseTableAssociation, SurrogateBasedTemporalRow}
import de.hpi.role_matching.GLOBAL_CONFIG
import de.hpi.role_matching.compatibility.graph.representation.vertex.IdentifiedFactLineage

import java.io.{File, PrintWriter}
import java.time.LocalDate
import scala.collection.mutable

class SimplifiedInputExporter(subdomain: String, id: String) extends StrictLogging{

  var numAssociationsWithChangesAfterStandardTimeEnd = 0

  def exportAll(identifiedFactLineageFile:File,trainTimeEnd:LocalDate) = {
    logger.debug(s"processing $id")
    val tt = TemporalTable.load(id)
    val flResultFileWriter = new PrintWriter(identifiedFactLineageFile)
    tt.attributes.zipWithIndex.foreach{case (al,i) => {
      val attrID = tt.attributes(i).attrId
      val dttID = AssociationIdentifier(subdomain, id, 0, Some(i))
      val surrogateID = GlobalSurrogateRegistry.getNextFreeSurrogateID
      val surrogateKeyAttribute = SurrogateAttributeLineage(surrogateID, i)
      //create dictionary from entity ids to surrogate key in association:
      val vlToSurrogateKey = scala.collection.mutable.HashMap[FactLineage,Int]()
      val entityIDToSurrogateKey = scala.collection.mutable.HashMap[Long,Int]()
      var curSurrogateCounter = 0
      tt.rows.foreach(tr => {
        if(vlToSurrogateKey.contains(tr.fields(i))){
          val surrogate = vlToSurrogateKey(tr.fields(i))
          entityIDToSurrogateKey.put(tr.entityID,surrogate)
          //nothing to add to the association
        } else {
          vlToSurrogateKey.put(tr.fields(i),curSurrogateCounter)
          entityIDToSurrogateKey.put(tr.entityID,curSurrogateCounter)
          curSurrogateCounter +=1
        }
      })
      val associationFullTimeRange: SurrogateBasedSynthesizedTemporalDatabaseTableAssociation = buildAssociation(al, dttID, surrogateKeyAttribute, vlToSurrogateKey)
      val associationLimitedTimeRange = buildAssociation(al,dttID,surrogateKeyAttribute,vlToSurrogateKey,true)
      if(vlToSurrogateKey.keySet.map(_.lineage.keySet).flatten.exists(_.isAfter(Socrata_IOService.STANDARD_TIME_FRAME_END))){
        numAssociationsWithChangesAfterStandardTimeEnd +=1
      }
      writeAssociationSchemaFile(al, dttID, surrogateKeyAttribute)
      associationLimitedTimeRange.writeToStandardOptimizationInputFile
      associationLimitedTimeRange.toSketch.writeToStandardOptimizationInputFile()
      associationFullTimeRange.writeToFullTimeRangeFile()
      associationFullTimeRange.toSketch.writeToFullTimeRangeFile()
      writeFactTable(dttID, vlToSurrogateKey, entityIDToSurrogateKey)
      var serialized= 0
      associationFullTimeRange.tupleReferences
        .withFilter(_.getDataTuple.head.asInstanceOf[FactLineage].projectToTimeRange(Socrata_IOService.STANDARD_TIME_FRAME_START,trainTimeEnd).countChanges(GLOBAL_CONFIG.CHANGE_COUNT_METHOD)._1>0)
        .foreach(r => {
          val id = IdentifiedFactLineage.getIDString(subdomain,r.toIDBasedTupleReference)
          val identifiedLineage = r.getDataTuple.head.asInstanceOf[FactLineage].toIdentifiedFactLineage(id)
          identifiedLineage.appendToWriter(flResultFileWriter,false,true)
          serialized+=1
      })
      logger.debug(s"Found ${associationFullTimeRange.rows.size} lineages without filter")
      logger.debug(s"Serialized $serialized lineages to $identifiedFactLineageFile")
    }}
    flResultFileWriter.close()
    val allTImstamps = tt.rows.flatMap(r =>
      r.fields.flatMap(_.lineage.keySet).toSet).toSet
    val ttContainsEvaluationChanges = allTImstamps.exists(_.isAfter(Socrata_IOService.STANDARD_TIME_FRAME_END))
    if(ttContainsEvaluationChanges && numAssociationsWithChangesAfterStandardTimeEnd ==0){
      println(s"changes after standard time are not kept in associations in $id")
      assert(false)
    }
  }

  private def writeAssociationSchemaFile(al: AttributeLineage, dttID: AssociationIdentifier, surrogateKeyAttribute: SurrogateAttributeLineage) = {
    val schema = new AssociationSchema(dttID, surrogateKeyAttribute, al)
    schema.writeToStandardFile()
  }

  private def buildAssociation(al: AttributeLineage,
                               dttID: AssociationIdentifier,
                               surrogateKeyAttribute: SurrogateAttributeLineage,
                               vlToSurrogateKey: mutable.HashMap[FactLineage, Int],
                               shortenToStandardTimeRange:Boolean=false) = {
    val newRows = collection.mutable.ArrayBuffer() ++ vlToSurrogateKey
      .toIndexedSeq
      .sortBy(_._2)
      .map { case (vl, surrogateKey) => {
        val finalVL = if(shortenToStandardTimeRange)
            FactLineage(vl.lineage.filter(!_._1.isAfter(GLOBAL_CONFIG.STANDARD_TIME_FRAME_END)))
          else
            vl
        new SurrogateBasedTemporalRow(IndexedSeq(surrogateKey), finalVL, IndexedSeq())
      }}
      .filter(!_.valueLineage.lineage.isEmpty)
    val association = new SurrogateBasedSynthesizedTemporalDatabaseTableAssociation(dttID.compositeID,
      mutable.HashSet(dttID),
      IndexedSeq(surrogateKeyAttribute),
      al,
      IndexedSeq(),
      newRows
    )
    association
  }

  private def writeFactTable(dttID: AssociationIdentifier, vlToSurrogateKey: mutable.HashMap[FactLineage, Int], entityIDToSurrogateKey: mutable.HashMap[Long, Int]) = {
    val surrogateKeyToVL = vlToSurrogateKey
      .map(t => (t._2, t._1))
      .toIndexedSeq
    val factTableRows = entityIDToSurrogateKey
      .map { case (e, sk) => {
        FactTableRow(e, sk)
      }
      }.toIndexedSeq
    val factLookupTable = new FactLookupTable(dttID, factTableRows, surrogateKeyToVL.map(t => (t._1,t._2.toSerializationHelper)))
    factLookupTable.writeToStandardFile()
  }
}
