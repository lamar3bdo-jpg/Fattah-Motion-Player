package com.fattah.motionplayer

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ContentUris
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.WindowManager
import android.graphics.Bitmap
import android.os.CancellationSignal
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import android.media.AudioManager
import kotlin.math.roundToInt
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

private val Navy = Color(0xFF080F1E)
private val Panel = Color(0xFF15243A)
private val Accent = Color(0xFF4DC7EE)
private val Light = Color(0xFFE9F1FC)

data class Video(val id: Long, val name: String, val folder: String, val duration: Long, val uri: android.net.Uri)

class MainActivity : ComponentActivity() {
    private var playing by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8,15,30)
        window.decorView.systemUiVisibility = 0
        window.navigationBarColor = android.graphics.Color.rgb(8,15,30)
        setContent { App(this) { playing = it } }
    }
    fun enterPip() {
        if (Build.VERSION.SDK_INT >= 26 && playing) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        }
    }
    fun setRefresh60(enabled: Boolean) {
        val params = window.attributes
        if (enabled) {
            val mode = display?.supportedModes?.filter { it.refreshRate >= 59.5f }?.minByOrNull { kotlin.math.abs(it.refreshRate - 60f) }
            if (mode != null) params.preferredDisplayModeId = mode.modeId
        } else params.preferredDisplayModeId = 0
        window.attributes = params
    }
    fun loadVideos(): List<Video> {
        val out = mutableListOf<Video>()
        val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.BUCKET_DISPLAY_NAME, MediaStore.Video.Media.DURATION)
        contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val name = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val folder = c.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val duration = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while(c.moveToNext()) {
                val key = c.getLong(id)
                out.add(Video(key,c.getString(name) ?: "Video", c.getString(folder) ?: "Other",c.getLong(duration),ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,key)))
            }
        }
        return out
    }
}

@Composable
private fun App(activity: MainActivity, onPlaying: (Boolean) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("player", 0) }
    var permitted by remember { mutableStateOf(if(Build.VERSION.SDK_INT >= 33) activity.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)==PackageManager.PERMISSION_GRANTED else activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    var videos by remember { mutableStateOf(emptyList<Video>()) }
    var current by remember { mutableStateOf<Video?>(null) }
    var query by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(false) }
    var refresh60 by remember { mutableStateOf(prefs.getBoolean("refresh60", false)) }
    LaunchedEffect(permitted) { if(permitted) videos = activity.loadVideos() }
    BackHandler(current != null || settings || folder != null) {
        when { current != null -> current = null; settings -> settings = false; else -> folder = null }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary=Accent,background=Navy,surface=Panel,onSurface=Light)) {
        Column(Modifier.fillMaxSize().background(Navy).windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
            if(current != null) {
                val item = current!!
                PlayerScreen(activity,item,refresh60, onPlaying, onBack={ current=null },onNext={
                    val idx = videos.indexOfFirst { it.id == item.id }
                    if(idx in 0 until videos.lastIndex) current = videos[idx+1]
                })
            } else if(settings) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={settings=false}) { Icon(Icons.Default.ArrowBack,"Back") }
                    Text("إعدادات التشغيل",fontSize=22.sp,fontWeight=FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth().clickable {
                    refresh60 = !refresh60; prefs.edit().putBoolean("refresh60",refresh60).apply(); activity.setRefresh60(refresh60)
                }.padding(20.dp), verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("تفضيل عرض 60Hz",fontWeight=FontWeight.Bold)
                        Text("للفيديوهات الأصلية 60fps؛ لا يحوّل 30fps إلى 60fps",fontSize=12.sp,color=Color.LightGray)
                    }
                    Switch(checked=refresh60,onCheckedChange={ refresh60=it; prefs.edit().putBoolean("refresh60",it).apply();activity.setRefresh60(it) })
                }
                Text("Fattah Motion Player • 1.0",Modifier.padding(20.dp),color=Accent)
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("FATTAH",fontSize=29.sp,fontWeight=FontWeight.Black,color=Light)
                        Text("MOTION PLAYER",fontSize=12.sp,letterSpacing=3.sp,color=Accent)
                    }
                    IconButton(onClick={videos=activity.loadVideos()}) { Icon(Icons.Default.Refresh,"Refresh") }
                    IconButton(onClick={settings=true}) { Icon(Icons.Default.Settings,"Settings") }
                }
                if(!permitted) {
                    Column(Modifier.fillMaxSize().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                        Text("السماح بالوصول للفيديوهات",fontSize=20.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick={launcher.launch(if(Build.VERSION.SDK_INT>=33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE)}) { Text("السماح") }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("الفيديوهات","المجلدات").forEachIndexed { i,s -> FilterChip(selected=tab==i,onClick={tab=i;folder=null},label={Text(s)}) }
                    }
                    OutlinedTextField(value=query,onValueChange={query=it},label={Text("بحث عن فيديو")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth().padding(16.dp))
                    if(tab==1 && folder==null) {
                        val folders = videos.groupBy { it.folder }.toList().sortedBy { it.first.lowercase() }
                        LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            items(folders) { (name, list) ->
                                Row(Modifier.fillMaxWidth().background(Panel,RoundedCornerShape(14.dp)).clickable { folder=name }.padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
                                    Icon(Icons.Default.Folder,null,tint=Accent); Spacer(Modifier.width(12.dp)); Text(name,Modifier.weight(1f),maxLines=1); Text("${list.size}",color=Color.LightGray)
                                }
                            }
                        }
                    } else {
                        if(folder!=null) TextButton(onClick={folder=null},modifier=Modifier.padding(start=12.dp)) { Icon(Icons.Default.ArrowBack,null);Text(" $folder") }
                        val filtered=videos.filter { (folder==null || it.folder==folder) && it.name.contains(query,true) }
                        Text("${filtered.size} فيديو",Modifier.padding(horizontal=20.dp),color=Color.LightGray,fontSize=13.sp)
                        LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                            items(filtered,key={it.id}) { video ->
                                Row(Modifier.fillMaxWidth().background(Panel,RoundedCornerShape(14.dp)).clickable { current=video }.padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                                    VideoThumbnail(video)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(video.name,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Medium)
                                        Spacer(Modifier.height(5.dp))
                                        Text("${video.folder} • ${formatTime(video.duration)}",fontSize=12.sp,color=Color.LightGray,maxLines=1)
                                    }
                                    Icon(Icons.Default.ChevronRight,null,tint=Accent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val s=ms/1000
    return if(s>=3600) "%02d:%02d:%02d".format(s/3600,(s%3600)/60,s%60) else "%02d:%02d".format(s/60,s%60)
}

@Composable
private fun PlayerScreen(activity: MainActivity, video: Video, refresh60: Boolean, onPlaying: (Boolean)->Unit,onBack:()->Unit,onNext:()->Unit) {
    val context=LocalContext.current
    val prefs=remember { context.getSharedPreferences("player",0) }
    val player=remember(video.id) { ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(video.uri));prepare();seekTo(prefs.getLong("pos_${video.id}",0));playWhenReady=true
    } }
    var speedMenu by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var gestureHint by remember { mutableStateOf("") }
    val audio = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    var subtitlesVisible by remember { mutableStateOf(true) }
    DisposableEffect(player) {
        onPlaying(true)
        activity.setRefresh60(refresh60)
        val listener=object:Player.Listener { override fun onIsPlayingChanged(value:Boolean) { isPlaying=value } }
        player.addListener(listener)
        onDispose {
            prefs.edit().putLong("pos_${video.id}",player.currentPosition.coerceAtLeast(0)).apply()
            player.removeListener(listener);player.release();onPlaying(false)
            activity.window.attributes=activity.window.attributes.apply { screenBrightness=WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }
    LaunchedEffect(player) {
        while(true) { position=player.currentPosition.coerceAtLeast(0);duration=player.duration.coerceAtLeast(0);delay(500) }
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().background(Navy).padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack) { Icon(Icons.Default.ArrowBack,"Back",tint=Light) }
            Text(video.name,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,color=Light)
            IconButton(onClick={activity.enterPip()}) { Icon(Icons.Default.PictureInPictureAlt,"PiP",tint=Accent) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black),contentAlignment=Alignment.Center) {
            AndroidView(factory={ PlayerView(it).apply { this.player=player;useController=false;keepScreenOn=true } },modifier=Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().pointerInput(player) {
                var dx = 0f
                var dy = 0f
                detectDragGestures(onDragStart={ dx=0f;dy=0f },onDragEnd={gestureHint=""},onDrag={change,amount ->
                    change.consume(); dx+=amount.x;dy+=amount.y
                    if(kotlin.math.abs(dx)>60f && kotlin.math.abs(dx)>kotlin.math.abs(dy)) {
                        player.seekTo((player.currentPosition+(dx*120).toLong()).coerceAtLeast(0));gestureHint="تقديم / ترجيع";dx=0f
                    } else if(kotlin.math.abs(dy)>12f && kotlin.math.abs(dy)>kotlin.math.abs(dx)) {
                        if(change.position.x<size.width/2) {
                            brightness=(brightness-dy/size.height).coerceIn(0.05f,1f)
                            activity.window.attributes=activity.window.attributes.apply { screenBrightness=brightness }
                            gestureHint="السطوع ${(brightness*100).roundToInt()}%"
                        } else {
                            val max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val cur=audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val next=(cur-dy/size.height*max).roundToInt().coerceIn(0,max)
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC,next,0)
                            gestureHint="الصوت ${(next*100/max.coerceAtLeast(1))}%"
                        };dy=0f
                    }
                })
            }.clickable { showControls=!showControls })
            if(gestureHint.isNotBlank()) Surface(color=Color(0xBB10233B),shape=RoundedCornerShape(14.dp)) { Text(gestureHint,Modifier.padding(14.dp),color=Light) }
        }
        Column(Modifier.fillMaxWidth().background(Navy).padding(12.dp)) {
            Text("${formatTime(position)} / ${formatTime(duration)}",color=Light,fontSize=13.sp)
            Row(verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={player.seekTo((player.currentPosition-10000).coerceAtLeast(0))}) { Icon(Icons.Default.Replay10,"-10",tint=Light) }
                IconButton(onClick={if(player.isPlaying) player.pause() else player.play()}) { Icon(if(isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,"Play/Pause",tint=Accent) }
                IconButton(onClick={player.seekTo(player.currentPosition+10000)}) { Icon(Icons.Default.Forward10,"+10",tint=Light) }
                IconButton(onClick=onNext) { Icon(Icons.Default.SkipNext,"Next",tint=Light) }
                Spacer(Modifier.weight(1f))
                Box {
                    TextButton(onClick={speedMenu=true}) { Text("${speed}×") }
                    DropdownMenu(expanded=speedMenu,onDismissRequest={speedMenu=false}) {
                        listOf(0.25f,0.5f,0.75f,1f,1.25f,1.5f,1.75f,2f).forEach { v ->
                            DropdownMenuItem(text={Text("${v}×")},onClick={speed=v;player.playbackParameters=PlaybackParameters(v);speedMenu=false})
                        }
                    }
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={subtitlesVisible=!subtitlesVisible;player.trackSelectionParameters=player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT,!subtitlesVisible).build()}) { Text(if(subtitlesVisible) "CC On" else "CC Off",color=Accent) }
                Icon(Icons.Default.Brightness6,"Brightness",tint=Light)
                Slider(value=brightness,onValueChange={ brightness=it;activity.window.attributes=activity.window.attributes.apply { screenBrightness=it.coerceAtLeast(0.01f) } },modifier=Modifier.weight(1f))
                Text(if(refresh60) "60Hz" else "Auto",color=Accent,fontSize=12.sp)
            }
        }
    }
}

@Composable
private fun VideoThumbnail(video: Video) {
    val context=LocalContext.current
    var bitmap by remember(video.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(video.id) {
        bitmap= kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if(Build.VERSION.SDK_INT>=29) context.contentResolver.loadThumbnail(video.uri,android.util.Size(240,150),null)
                else null
            } catch(_:Exception) { null }
        }
    }
    Box(Modifier.size(92.dp,64.dp).background(Color(0xFF253C58),RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center) {
        if(bitmap!=null) Image(bitmap!!.asImageBitmap(),contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Icon(Icons.Default.PlayCircle,null,tint=Accent,modifier=Modifier.size(36.dp))
        Icon(Icons.Default.PlayArrow,null,tint=Color.White,modifier=Modifier.align(Alignment.Center).size(24.dp))
    }
}
