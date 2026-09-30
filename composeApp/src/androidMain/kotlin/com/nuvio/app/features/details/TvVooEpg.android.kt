package com.nuvio.app.features.details

import android.app.Application
import android.content.Context
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private data class TvEpgProgramme(val title:String,val description:String?,val start:Long,val end:Long)
private val TV_EPG_URLS=listOf(
 "https://iptv-org.github.io/epg/guides/it/guidatv.sky.it.epg.xml",
 "https://iptv-org.github.io/epg/guides/it/mediaset.it.epg.xml"
)

private fun configuredEpgUrls():List<String>{
 val automatic=TV_EPG_URLS
 val app=runCatching{Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as? Application}.getOrNull()
     ?:return automatic
 val raw=app.getSharedPreferences("nuvio_tvguide_epg",Context.MODE_PRIVATE).getString("sources","[]").orEmpty()
 val manual=runCatching{
  val a=JSONArray(raw)
  buildList{
   for(i in 0 until a.length()){
    val o=a.optJSONObject(i)?:continue
    if(o.optBoolean("enabled",true)){
     val url=o.optString("url")
     if(url.startsWith("http"))add(url)
    }
   }
  }
 }.getOrDefault(emptyList())
 return (automatic+manual).distinct()
}

internal actual suspend fun resolveTvVooEpgDescription(channelName:String):String?=withContext(Dispatchers.IO){
 val wanted=normalizeEpgName(channelName); val now=System.currentTimeMillis()
 for(source in configuredEpgUrls()){
  val programmes=runCatching{readEpg(source,wanted)}.getOrDefault(emptyList()).sortedBy{it.start}
  val index=programmes.indexOfFirst{it.start<=now&&it.end>now}
  if(index>=0){
   val current=programmes[index]; val next=programmes.getOrNull(index+1)
   val fmt=SimpleDateFormat("HH:mm",Locale.ITALIAN).apply{timeZone=TimeZone.getTimeZone("Europe/Rome")}
   return@withContext buildString{
    append("🔴 ").append(fmt.format(Date(current.start))).append("–").append(fmt.format(Date(current.end))).append(" · ").append(current.title)
    current.description?.takeIf{it.isNotBlank()}?.let{append("\n").append(it)}
    next?.let{
     append("\n➡️ ").append(fmt.format(Date(it.start))).append("–").append(fmt.format(Date(it.end))).append(" · ").append(it.title)
     it.description?.takeIf{d->d.isNotBlank()}?.let{d->append("\n").append(d)}
    }
   }
  }
 }
 null
}

private fun readEpg(source:String,wanted:String):List<TvEpgProgramme>{
 val connection=(URL(source).openConnection() as HttpURLConnection).apply{connectTimeout=8000;readTimeout=12000;setRequestProperty("User-Agent","NuvioMobile/EPG")}
 try{
  connection.inputStream.use{input->
   val parser=XmlPullParserFactory.newInstance().newPullParser().apply{setInput(input,"UTF-8")}
   val channelNames=mutableMapOf<String,String>(); val out=mutableListOf<TvEpgProgramme>()
   var event=parser.eventType; var channelId:String?=null; var programmeChannel:String?=null
   var start:String?=null; var end:String?=null; var title:String?=null; var desc:String?=null
   while(event!=XmlPullParser.END_DOCUMENT){
    when(event){
     XmlPullParser.START_TAG->when(parser.name){
      "channel"->{channelId=parser.getAttributeValue(null,"id")}
      "display-name"->if(channelId!=null) channelNames[channelId!!]=parser.nextText()
      "programme"->{programmeChannel=parser.getAttributeValue(null,"channel");start=parser.getAttributeValue(null,"start");end=parser.getAttributeValue(null,"stop");title=null;desc=null}
      "title"->if(programmeChannel!=null) title=parser.nextText()
      "desc"->if(programmeChannel!=null) desc=parser.nextText()
     }
     XmlPullParser.END_TAG->when(parser.name){
      "channel"->channelId=null
      "programme"->{
       val display=channelNames[programmeChannel]
       if(display!=null&&epgNamesMatch(wanted,normalizeEpgName(display))){
        val s=parseEpgTime(start);val e=parseEpgTime(end);val t=title
        if(s!=null&&e!=null&&!t.isNullOrBlank()) out+=TvEpgProgramme(t,desc,s,e)
       }
       programmeChannel=null
      }
     }
    }
    event=parser.next()
   }
   return out
  }
 }finally{connection.disconnect()}
}
private fun parseEpgTime(value:String?):Long?{
 if(value.isNullOrBlank())return null
 val compact=value.trim().replace(Regex("\\s+")," ")
 val patterns=listOf("yyyyMMddHHmmss Z","yyyyMMddHHmm Z","yyyyMMddHHmmss","yyyyMMddHHmm")
 for(pattern in patterns)runCatching{
  val f=SimpleDateFormat(pattern,Locale.US).apply{isLenient=false;if(!pattern.contains("Z"))timeZone=TimeZone.getTimeZone("Europe/Rome")}
  return f.parse(compact)?.time
 }
 return null
}
private fun normalizeEpgName(value:String):String=Normalizer.normalize(value,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT).replace("&"," e ").replace(Regex("\\b(hd|uhd|4k|fhd|italia|it)\\b")," ").replace(Regex("[^a-z0-9]+")," ").trim()
private fun epgNamesMatch(a:String,b:String):Boolean=a==b||a.contains(b)||b.contains(a)
