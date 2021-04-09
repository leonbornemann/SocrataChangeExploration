package de.hpi.tfm.data.wikipedia.infobox

import java.time.LocalDateTime


//{"valueValidTo":"Jan 24, 2017 3:08:48 AM","property":"lats","currentValue":"10"}
case class InfoboxChange(valueValidTo:Option[String]=None,property:String,currentValue:Option[String] = None,previousValue:Option[String] = None) {

}