package de.hpi.role_matching.scoring

import de.hpi.socrata.{JsonReadable, JsonWritable}
import de.hpi.role_matching.scoring.MultipleEventWeightScoreOccurrenceStats.{NEUTRAL, STRONGNEGATIVE, STRONGPOSTIVE, WEAKNEGATIVE, WEAKPOSTIVE}

import java.time.LocalDate

case class MultipleEventWeightScoreOccurrenceStats(val dsName:String,
                                                   val trainTimeEnd:LocalDate,
                                               val summedScores:Option[collection.mutable.IndexedSeq[Float]]=Some(collection.mutable.ArrayBuffer[Float](0.0f,0.0f,0.0f,0.0f,0.0f)),
                                              var strongPositive:Int=0,
                                              var weakPositive:Int=0,
                                              var neutral:Int=0,
                                              var weakNegative:Int=0,
                                              var strongNegative:Int=0) extends JsonWritable[MultipleEventWeightScoreOccurrenceStats]{
  def totalCount = strongPositive+weakPositive+neutral+weakNegative+strongNegative


  def addScore(kind:String,count:Int,scoreSum:Float) = {
    kind match {
      case STRONGPOSTIVE  => strongPositive+=count; if(summedScores.isDefined) summedScores.get(0) = summedScores.get(0) + scoreSum
      case WEAKPOSTIVE  => weakPositive+=count; if(summedScores.isDefined) summedScores.get(1) = summedScores.get(1) + scoreSum
      case NEUTRAL  => neutral+=count; if(summedScores.isDefined) summedScores.get(2) = summedScores.get(2) + scoreSum
      case WEAKNEGATIVE  => weakNegative+=count; if(summedScores.isDefined) summedScores.get(3) = summedScores.get(3) + scoreSum
      case STRONGNEGATIVE  => strongNegative+=count; if(summedScores.isDefined) summedScores.get(4) = summedScores.get(4) + scoreSum
      case _  => assert(false)
    }
  }

  if(summedScores.isDefined)
    assert(summedScores.get.size==5) //positions of the score sums are the same as in the constructors

  def addAll(other: MultipleEventWeightScoreOccurrenceStats) = {
    strongPositive +=other.strongPositive
    weakPositive +=other.weakPositive
    neutral +=other.neutral
    weakNegative +=other.weakNegative
    strongNegative +=other.strongNegative
    if(summedScores.isDefined && other.summedScores.isDefined){
      for(i <- 0 until summedScores.get.size){
        summedScores.get(i)=summedScores.get(i)+other.summedScores.get(i)
      }
    }
  }
}
object MultipleEventWeightScoreOccurrenceStats extends JsonReadable[MultipleEventWeightScoreOccurrenceStats]{

  val STRONGPOSTIVE = "strongPositive"
  val WEAKPOSTIVE = "weakPositive"
  val NEUTRAL = "neutral"
  val WEAKNEGATIVE = "weakNegative"
  val STRONGNEGATIVE = "strongNegative"


}
