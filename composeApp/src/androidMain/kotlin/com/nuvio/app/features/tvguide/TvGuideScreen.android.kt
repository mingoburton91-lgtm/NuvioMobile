package com.nuvio.app.features.tvguide
import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import org.json.*
import org.xmlpull.v1.*
import java.net.*
import java.text.Normalizer
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val ADDON="https://tvvoo.hayd.uk/cfg-it-uk-fr-de-pt-es-al-tr-nl-ar-bk-ru-ro-pl-bg-hc1"
private val zone=ZoneId.of("Europe/Rome")
private val tf=DateTimeFormatter.ofPattern("HH:mm",Locale.ITALIAN).withZone(zone)
private val df=DateTimeFormatter.ofPattern("EEE d MMM",Locale.ITALIAN)
private val countries=listOf("it" to "Italia","uk" to "UK","fr" to "Francia","de" to "Germania","pt" to "Portogallo","es" to "Spagna","al" to "Albania","tr" to "Turchia","nl" to "Paesi Bassi","ar" to "Argentina","bk" to "Balcani","ru" to "Russia","ro" to "Romania","pl" to "Polonia","bg" to "Bulgaria")
private data class Programme(val title:String,val start:Instant,val end:Instant)
private data class Channel(val id:String,val name:String,val logo:String,val programmes:List<Programme>)
private data class EpgSource(val name:String,val url:String,val enabled:Boolean=true,val country:String="")
private data class EpgData(val names:Map<String,List<String>>,val programmes:Map<String,List<Programme>>)
private val defaults=mapOf(
"it" to listOf(EpgSource("Italia · Sky","https://iptv-org.github.io/epg/guides/it/guidatv.sky.it.epg.xml",country="it"),EpgSource("Italia · Mediaset","https://iptv-org.github.io/epg/guides/it/mediaset.it.epg.xml",country="it")),
"uk" to listOf(EpgSource("UK","https://iptv-org.github.io/epg/guides/uk/ontvtonight.com.epg.xml",country="uk")),
"fr" to listOf(EpgSource("Francia","https://iptv-org.github.io/epg/guides/fr/programme-tv.net.epg.xml",country="fr")),
"de" to listOf(EpgSource("Germania","https://iptv-org.github.io/epg/guides/de/hd-plus.de.epg.xml",country="de")),
"pt" to listOf(EpgSource("Portogallo","https://iptv-org.github.io/epg/guides/pt/meo.pt.epg.xml",country="pt")),
"es" to listOf(EpgSource("Spagna","https://iptv-org.github.io/epg/guides/es/programacion-tv.elpais.com.epg.xml",country="es")),
"al" to listOf(EpgSource("Albania","https://iptv-org.github.io/epg/guides/al/ipko.com.epg.xml",country="al")),
"tr" to listOf(EpgSource("Turchia · TV+","https://iptv-org.github.io/epg/guides/tr/tvplus.com.tr.epg.xml",country="tr"),EpgSource("Turchia · Digiturk","https://iptv-org.github.io/epg/guides/tr/digiturk.com.tr.epg.xml",country="tr")),
"nl" to listOf(EpgSource("Paesi Bassi","https://iptv-org.github.io/epg/guides/nl/delta.nl.epg.xml",country="nl")),
"ar" to listOf(EpgSource("Argentina","https://iptv-org.github.io/epg/guides/ar/mi.tv.epg.xml",country="ar")),
"ru" to listOf(EpgSource("Russia","https://iptv-org.github.io/epg/guides/ru/tv.yandex.ru.epg.xml",country="ru")),
"ro" to listOf(EpgSource("Romania","https://iptv-org.github.io/epg/guides/ro/programetv.ro.epg.xml",country="ro")),
"pl" to listOf(EpgSource("Polonia","https://iptv-org.github.io/epg/guides/pl/programtv.onet.pl.epg.xml",country="pl")),
"bg" to listOf(EpgSource("Bulgaria","https://iptv-org.github.io/epg/guides/bg/vivacom.bg.epg.xml",country="bg")),
"bk" to listOf(EpgSource("Balcani · Serbia","https://iptv-org.github.io/epg/guides/rs/mts.rs.epg.xml",country="bk"),EpgSource("Balcani · Bosnia","https://iptv-org.github.io/epg/guides/ba/mtel.ba.epg.xml",country="bk"))
)

@Composable internal actual fun TvGuideScreen(modifier:Modifier,onOpenStream:(String,String,String)->Unit){
 val context=LocalContext.current;val prefs=remember{context.getSharedPreferences("nuvio_tvguide_epg",0)}
 var country by rememberSaveable{mutableStateOf("it")};var dayOffset by rememberSaveable{mutableIntStateOf(0)}
 var channels by remember{mutableStateOf<List<Channel>>(emptyList())};var status by remember{mutableStateOf("Caricamento guida TV…")}
 var loading by remember{mutableStateOf(false)};var showEpg by rememberSaveable{mutableStateOf(false)}
 var sourceName by rememberSaveable{mutableStateOf("")};var sourceUrl by rememberSaveable{mutableStateOf("")}
 var sources by remember{mutableStateOf(loadSources(prefs.getString("sources","[]").orEmpty()))};var revision by remember{mutableIntStateOf(0)}
 var pending by remember{mutableStateOf<Channel?>(null)};val day=remember(dayOffset){LocalDate.now(zone).plusDays(dayOffset.toLong())}
 LaunchedEffect(country,day,revision,sources){loading=true;status="Caricamento canali e programmi…";runCatching{withContext(Dispatchers.IO){mergeEpg(loadChannels(country,day),defaults[country].orEmpty()+sources.filter{it.enabled&&(it.country.isBlank()||it.country==country)},day)}}.onSuccess{channels=it;status=if(it.isEmpty())"Nessun canale disponibile" else ""}.onFailure{status="Guida non disponibile: "+(it.localizedMessage?:"errore di rete")};loading=false}
 LaunchedEffect(pending){val ch=pending?:return@LaunchedEffect;status="Apro "+ch.name+"…";runCatching{withContext(Dispatchers.IO){resolveStream(ch.id)}}.onSuccess{onOpenStream(it,ch.name,ch.id);status=""}.onFailure{status="Impossibile aprire "+ch.name};pending=null}
 Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(horizontal=14.dp)){
  Row(Modifier.fillMaxWidth().padding(top=10.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically){Text("Guida TV",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));TextButton({showEpg=!showEpg}){Text("EPG")};TextButton({revision++}){Text("Aggiorna")}}
  if(showEpg)Card(Modifier.fillMaxWidth().padding(bottom=8.dp)){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Sorgenti XMLTV",fontWeight=FontWeight.SemiBold);Text("Automatiche: "+defaults[country].orEmpty().joinToString{it.name},style=MaterialTheme.typography.bodySmall);OutlinedTextField(sourceName,{sourceName=it},label={Text("Nome")},modifier=Modifier.fillMaxWidth());OutlinedTextField(sourceUrl,{sourceUrl=it},label={Text("URL XMLTV")},modifier=Modifier.fillMaxWidth());Button({if(sourceUrl.startsWith("http")){sources=sources+EpgSource(sourceName.ifBlank{"EPG "+(sources.size+1)},sourceUrl,country=country);saveSources(prefs,sources);sourceName="";sourceUrl="";revision++}},enabled=sourceUrl.startsWith("http")){Text("Aggiungi sorgente")};sources.forEachIndexed{i,s->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Checkbox(s.enabled,{e->sources=sources.toMutableList().also{it[i]=s.copy(enabled=e)};saveSources(prefs,sources);revision++});Column(Modifier.weight(1f)){Text(s.name,maxLines=1);Text(s.url,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)};TextButton({sources=sources.toMutableList().also{it.removeAt(i)};saveSources(prefs,sources);revision++}){Text("Elimina")}}}}}
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){countries.forEach{(c,n)->FilterChip(country==c,{country=c},{Text(n)})}}
  Row(Modifier.fillMaxWidth().padding(bottom=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){TextButton({dayOffset--}){Text("‹")};Column(horizontalAlignment=Alignment.CenterHorizontally){Text(if(dayOffset==0)"Oggi" else day.format(df),fontWeight=FontWeight.SemiBold);if(dayOffset!=0)TextButton({dayOffset=0}){Text("Torna a oggi")}};TextButton({dayOffset++}){Text("›")}}
  if(loading)LinearProgressIndicator(Modifier.fillMaxWidth());if(status.isNotBlank())Text(status,Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.bodySmall)
  LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=110.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){items(channels,key={it.id}){ch->ChannelRow(ch,day){pending=ch}}}
 }
}
@Composable private fun ChannelRow(ch:Channel,day:LocalDate,onPlay:()->Unit){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(10.dp)){Row(Modifier.fillMaxWidth().clickable(onClick=onPlay),verticalAlignment=Alignment.CenterVertically){if(ch.logo.isNotBlank())NetworkLogo(ch.logo);Column(Modifier.weight(1f).padding(start=10.dp)){Text(ch.name,fontWeight=FontWeight.SemiBold,maxLines=2);Text("Tocca per guardare",style=MaterialTheme.typography.bodySmall)};Text("▶",fontSize=20.sp)};Spacer(Modifier.height(8.dp));Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(7.dp)){val ps=visibleProgrammes(ch.programmes,day);if(ps.isEmpty())Text("Palinsesto non disponibile",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(10.dp)) else ps.forEach{p->val now=Instant.now();val live=day==LocalDate.now(zone)&&!p.start.isAfter(now)&&p.end.isAfter(now);Surface(Modifier.width(205.dp).clickable(onClick=onPlay),shape=RoundedCornerShape(10.dp),color=if(live)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant){Column(Modifier.padding(10.dp)){Text((if(live)"● IN ONDA  " else "")+tf.format(p.start)+"–"+tf.format(p.end),style=MaterialTheme.typography.labelSmall,color=if(live)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant);Text(p.title,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)}}}}}}}
@Composable private fun NetworkLogo(url:String){var b by remember(url){mutableStateOf<android.graphics.Bitmap?>(null)};LaunchedEffect(url){b=withContext(Dispatchers.IO){runCatching{URL(url).openStream().use(BitmapFactory::decodeStream)}.getOrNull()}};b?.let{Image(it.asImageBitmap(),null,Modifier.size(54.dp))}}
private fun loadChannels(country:String,day:LocalDate):List<Channel>{val out=ArrayList<Channel>();for(skip in 0..4900 step 100){val j=fetchJson(ADDON+"/catalog/tv/vavoo_tv_"+country+"/date="+day+"&skip="+skip+".json");val a=j.optJSONArray("metasDetailed")?:j.optJSONArray("metas")?:break;if(a.length()==0)break;for(i in 0 until a.length()){val x=a.optJSONObject(i)?:continue;val id=x.optString("id");if(id.isBlank())continue;val ps=ArrayList<Programme>();x.optJSONArray("videos")?.let{v->for(k in 0 until v.length()){val q=v.optJSONObject(k)?:continue;runCatching{ps+=Programme(q.optString("title","Programma"),Instant.parse(q.getString("startTime")),Instant.parse(q.getString("endTime")))}}};val logo=listOf("poster","logo","background","thumbnail").map{x.optString(it)}.firstOrNull{it.startsWith("http")}.orEmpty();out+=Channel(id,x.optString("name","Canale"),logo,ps.sortedBy{it.start})};if(a.length()<100)break};return out.distinctBy{it.id}}
private fun mergeEpg(base:List<Channel>,sources:List<EpgSource>,day:LocalDate):List<Channel>{val data=sources.mapNotNull{runCatching{parseXmlTv(it.url,day)}.getOrNull()};return base.map{ch->if(ch.programmes.isNotEmpty())ch else{val n=norm(ch.name);var hit:List<Programme>?=null;for(d in data){val id=d.names.entries.firstOrNull{norm(it.key)==norm(ch.id)||it.value.any{z->norm(z)==n}}?.key;if(id!=null&&!d.programmes[id].isNullOrEmpty()){hit=d.programmes[id];break}};ch.copy(programmes=hit?:emptyList())}}}
private fun parseXmlTv(address:String,day:LocalDate):EpgData{val c=URL(address).openConnection() as HttpURLConnection;c.connectTimeout=15000;c.readTimeout=30000;c.setRequestProperty("User-Agent","NuvioMobile/0.5.4");try{val p=XmlPullParserFactory.newInstance().newPullParser();p.setInput(c.inputStream,"UTF-8");val names=HashMap<String,List<String>>();val progs=HashMap<String,MutableList<Programme>>();var e=p.eventType;while(e!=XmlPullParser.END_DOCUMENT){if(e==XmlPullParser.START_TAG&&p.name=="channel"){val id=p.getAttributeValue(null,"id").orEmpty();val ns=ArrayList<String>();var z=p.next();while(!(z==XmlPullParser.END_TAG&&p.name=="channel")){if(z==XmlPullParser.START_TAG&&p.name=="display-name")ns+=p.nextText();z=p.next()};names[id]=ns}else if(e==XmlPullParser.START_TAG&&p.name=="programme"){val id=p.getAttributeValue(null,"channel").orEmpty();val st=parseTime(p.getAttributeValue(null,"start"));val en=parseTime(p.getAttributeValue(null,"stop"));var title="Programma";var z=p.next();while(!(z==XmlPullParser.END_TAG&&p.name=="programme")){if(z==XmlPullParser.START_TAG&&p.name=="title")title=p.nextText();z=p.next()};if(st!=null&&en!=null&&st.atZone(zone).toLocalDate()==day)progs.getOrPut(id){ArrayList()}+=Programme(title,st,en)};e=p.next()};return EpgData(names,progs.mapValues{it.value.sortedBy{q->q.start}})}finally{c.disconnect()}}
private fun parseTime(v:String?):Instant?{if(v.isNullOrBlank())return null;return runCatching{val m=Regex("^(\\d{8})(\\d{4})(\\d{2})?\\s*(Z|[+-]\\d{4}|[A-Za-z_]+(?:/[A-Za-z_]+)?)?.*").find(v.trim())?:return null;val raw=m.groupValues[1]+m.groupValues[2]+m.groupValues[3].ifBlank{"00"};val l=LocalDateTime.parse(raw,DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));val tz=m.groupValues.getOrNull(4).orEmpty();when{tz=="Z"->l.toInstant(ZoneOffset.UTC);Regex("[+-]\\d{4}").matches(tz)->l.toInstant(ZoneOffset.of(tz.substring(0,3)+":"+tz.substring(3)));tz.isNotBlank()->l.atZone(ZoneId.of(tz)).toInstant();else->l.atZone(zone).toInstant()}}.getOrNull()}
private fun resolveStream(id:String):String{val e=URLEncoder.encode(id,"UTF-8").replace("+","%20");val a=fetchJson(ADDON+"/stream/tv/"+e+".json").optJSONArray("streams")?:error("Nessuno stream");for(i in 0 until a.length()){val u=a.optJSONObject(i)?.optString("url").orEmpty();if(u.startsWith("http"))return u};error("Nessuno stream riproducibile")}
private fun fetchJson(address:String):JSONObject{val c=URL(address).openConnection() as HttpURLConnection;c.connectTimeout=15000;c.readTimeout=25000;c.setRequestProperty("User-Agent","NuvioMobile/0.5.4");try{if(c.responseCode !in 200..299)error("HTTP "+c.responseCode);return JSONObject(c.inputStream.bufferedReader().use{it.readText()})}finally{c.disconnect()}}
private fun norm(s:String):String{var x=Normalizer.normalize(s,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").uppercase(Locale.ROOT);x=x.replace(Regex("\\((BACKUP|HD|FHD|UHD|4K)\\)")," ").replace(Regex("\\b(BACKUP|FHD|UHD|4K|HD)\\b")," ");return x.replace(Regex("[^A-Z0-9]+")," ").trim()}
private fun loadSources(raw:String):List<EpgSource> = runCatching{val a=JSONArray(raw);(0 until a.length()).map{val o=a.getJSONObject(it);EpgSource(o.optString("name"),o.optString("url"),o.optBoolean("enabled",true),o.optString("country"))}}.getOrDefault(emptyList())
private fun saveSources(p:android.content.SharedPreferences,s:List<EpgSource>){val a=JSONArray();s.forEach{a.put(JSONObject().put("name",it.name).put("url",it.url).put("enabled",it.enabled).put("country",it.country))};p.edit().putString("sources",a.toString()).apply()}
private fun visibleProgrammes(ps:List<Programme>,day:LocalDate):List<Programme>{if(day!=LocalDate.now(zone))return ps;val now=Instant.now();val current=ps.indexOfFirst{!it.start.isAfter(now)&&it.end.isAfter(now)};if(current>=0)return ps.drop(current);val next=ps.indexOfFirst{it.start.isAfter(now)};return if(next>=0)ps.drop(next) else ps.takeLast(1)}
