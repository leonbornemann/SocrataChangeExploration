package de.hpi.socrata.tfmp_input.factLookup

import de.hpi.socrata.io.Socrata_Synthesis_IOService
import de.hpi.socrata.tfmp_input.association.AssociationIdentifier
import de.hpi.socrata.tfmp_input.table.nonSketch.{FactLineage, FactLineageWithHashMap}
import de.hpi.socrata.{JsonReadable, JsonWritable}

import java.io.File

case class FactLookupTable(id: AssociationIdentifier, factTableRows: IndexedSeq[FactTableRow], surrogateKeyToVL: IndexedSeq[(Int, FactLineageWithHashMap)]) extends JsonWritable[FactLookupTable] {

//  private val bySurrogateKey = factTableRows
//    .map(r => (r.surrogateKey,r))
//    .toMap
//  assert(bySurrogateKey.size == factTableRows.size)
  private val surrogateKEyToVLMap = surrogateKeyToVL.map(t => (t._1,FactLineage.fromSerializationHelper(t._2))).toMap

  def getCorrespondingValueLineage(surrogateKey: Int) = {
    surrogateKEyToVLMap(surrogateKey)
  }


  def writeToStandardFile() = {
    val file = FactLookupTable.getStandardFile(id)
    toJsonFile(file)
   }


}
object FactLookupTable extends JsonReadable[FactLookupTable] {

  def readFromStandardFile(id:AssociationIdentifier) = fromJsonFile(getStandardFile(id).getAbsolutePath)

  def getStandardFile(id: AssociationIdentifier) = {
    val dir = Socrata_Synthesis_IOService.OPTIMIZATION_INPUT_FACTLOOKUP_DIR(id.viewID,id.subdomain)
    val file = Socrata_Synthesis_IOService.createParentDirs(new File(dir + s"/${id.compositeID}.json"))
    file
  }

}
