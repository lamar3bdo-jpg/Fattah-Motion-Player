package com.fattah.motionplayer

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlin.math.abs

class MainActivity : Activity() {
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private var equalizer: Equalizer? = null
    private var selectedUri: Uri? = null
    private var contrast = 1f
    private var leftMode = 0 // 0 = brightness; 1 = contrast
    private val cyan = Color.rgb(37, 184, 255)
    private val dark = Color.rgb(9, 16, 29)
    private val audio by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = dark
        window.navigationBarColor = dark
        player = ExoPlayer.Builder(this).build()
        playerView = PlayerView(this).apply { player = this@MainActivity.player; useController = true; setBackgroundColor(Color.BLACK) }
        buildHome()
    }

    private fun label(s: String, size: Float = 16f) = TextView(this).apply {
        text = s; textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL
        setPadding(18, 16, 18, 16)
    }
    private fun button(s: String, action: () -> Unit): Button = Button(this).apply {
        text = s; isAllCaps = false; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(21, 42, 65))
        setOnClickListener { action() }
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(dark) }
    private fun addButton(parent: LinearLayout, s: String, action: () -> Unit) {
        parent.addView(button(s, action), LinearLayout.LayoutParams(-1, -2).apply { setMargins(12, 5, 12, 5) })
    }
    private fun setPage(title: String): LinearLayout {
        root = column(); setContentView(root)
        root.addView(label(title, 24f))
        return root
    }
    private fun buildHome() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val v = setPage("F  |  Fattah Motion Player")
        v.addView(label("CINEMATIC VIDEO EXPERIENCE", 12f))
        addButton(v, "▶  فتح فيديو من الجهاز") { pickVideo() }
        addButton(v, "◷  متابعة آخر فيديو") { selectedUri?.let { play(it) } ?: toast("اختار فيديو الأول") }
        addButton(v, "♫  إعدادات الصوت والإيكولايزر") { showEqualizer() }
        addButton(v, "⚙  إعدادات التشغيل") { showSettings() }
        v.addView(label("النسخة التجريبية 0.1 — تشغيل محلي • إيماءات • PiP • سرعات", 13f))
        v.addView(label("AI Frame Interpolation غير مفعّل في هذه النسخة.", 13f))
    }
    private fun pickVideo() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "video/*" }
        startActivityForResult(intent, 100)
    }
    @Deprecated("Legacy result API for minimal Android project")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) data?.data?.let { uri ->
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            selectedUri = uri; play(uri)
        }
    }
    private fun play(uri: Uri) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        val v = setPage("Fattah Motion Player")
        v.addView(playerView, LinearLayout.LayoutParams(-1, 0, 1f))
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun action(text: String, f: () -> Unit) { bar.addView(button(text, f), LinearLayout.LayoutParams(0, -2, 1f)) }
        action("السرعة") { showSpeeds() }
        action("الصوت") { showEqualizer() }
        action("☀/◐") { leftMode = 1-leftMode; toast(if(leftMode==0) "يسار الشاشة: الإضاءة" else "يسار الشاشة: التباين") }
        action("PiP") { enterMiniPlayer() }
        action("رجوع") { player.pause(); buildHome() }
        v.addView(bar)
        player.setMediaItem(MediaItem.fromUri(uri)); player.prepare(); player.play()
        installGestures()
    }
    private fun installGestures() {
        var startX = 0f; var startY = 0f; var moved = false
        playerView.setOnTouchListener { view, event ->
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startX=event.x; startY=event.y; moved=false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx=event.x-startX; val dy=event.y-startY
                    if (abs(dy)>45 && abs(dy)>abs(dx)*1.2f) {
                        moved=true
                        val delta = -dy / view.height.coerceAtLeast(1)
                        if (startX > view.width/2) {
                            val max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val current=audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC,(current+(delta*max*1.5f).toInt()).coerceIn(0,max),0)
                        } else if(leftMode==0) {
                            val lp=window.attributes
                            val current=if(lp.screenBrightness<0) 0.5f else lp.screenBrightness
                            lp.screenBrightness=(current+delta).coerceIn(0.02f,1f); window.attributes=lp
                        } else {
                            contrast=(contrast+delta).coerceIn(0.5f,2f)
                            // Contrast needs a shader pipeline; UI-only indicator for now.
                            toast("التباين: ${(contrast*100).toInt()}% (المعالجة قيد التطوير)")
                        }
                        startY=event.y
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx=event.x-startX
                    if(!moved && abs(dx)>90) player.seekTo((player.currentPosition + (dx/view.width*60000).toLong()).coerceAtLeast(0))
                    else if(!moved) playerView.showController()
                    true
                }
                else -> true
            }
        }
    }
    private fun showSpeeds() {
        val speeds=floatArrayOf(0.05f,0.1f,0.2f,0.5f,1f,1.25f,1.5f,2f)
        val labels=speeds.map { "${it}x" }.toTypedArray()
        android.app.AlertDialog.Builder(this).setTitle("سرعة التشغيل").setItems(labels) { _,which ->
            try { player.playbackParameters=PlaybackParameters(speeds[which]); toast("السرعة ${labels[which]} — السلاسة الذكية غير مفعلة") }
            catch(e: Exception) { toast("السرعة غير مدعومة على الجهاز: ${e.message}") }
        }.show()
    }
    private fun showEqualizer() {
        val content=column()
        val heading=label("المعادل الصوتي — Equalizer",20f); content.addView(heading)
        val fx = try {
            equalizer ?: Equalizer(0, player.audioSessionId).apply { enabled=true; equalizer=this }
        } catch(e: Exception) { null }
        if(fx==null) content.addView(label("الإيكولايزر غير متاح لهذا المسار الصوتي."))
        else {
            val toggle=Switch(this).apply { text="تفعيل الإيكولايزر"; setTextColor(Color.WHITE); isChecked=fx.enabled; setOnCheckedChangeListener { _,checked -> fx.enabled=checked } }
            content.addView(toggle)
            val range=fx.bandLevelRange
            for(i in 0 until fx.numberOfBands.toInt()) {
                val band=i.toShort()
                content.addView(label("${fx.getCenterFreq(band)/1000} Hz"))
                val seek=SeekBar(this).apply { max=range[1]-range[0]; progress=fx.getBandLevel(band)-range[0] }
                seek.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s:SeekBar?,p:Int,fromUser:Boolean) { if(fromUser) try { fx.setBandLevel(band,(range[0]+p).toShort()) } catch(_:Exception){} }
                    override fun onStartTrackingTouch(s:SeekBar?) {}
                    override fun onStopTrackingTouch(s:SeekBar?) {}
                }); content.addView(seek)
            }
            addButton(content,"إعادة ضبط") { for(i in 0 until fx.numberOfBands.toInt()) fx.setBandLevel(i.toShort(),0) ; toast("تمت إعادة الضبط") }
        }
        val scroll=ScrollView(this).apply { addView(content) }
        android.app.AlertDialog.Builder(this).setView(scroll).setPositiveButton("إغلاق",null).show()
    }
    private fun showSettings() {
        android.app.AlertDialog.Builder(this).setTitle("إعدادات التشغيل")
            .setMessage("• اسحب يمين الشاشة عموديًا للصوت\n• اسحب يسار الشاشة للإضاءة\n• زر ☀/◐ يبدل إلى وضع التباين التجريبي\n• PiP للنافذة العائمة\n• AI Super Slow Motion يتطلب محرك توليد إطارات لم يُدمج بعد")
            .setPositiveButton("حسنًا",null).show()
    }
    private fun enterMiniPlayer() {
        if(Build.VERSION.SDK_INT>=26 && player.isPlaying) {
            try { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16,9)).build()) }
            catch(e:Exception) { toast("تعذر تشغيل النافذة العائمة") }
        } else toast("شغّل فيديو أولًا")
    }
    override fun onUserLeaveHint() { super.onUserLeaveHint(); if(player.isPlaying) enterMiniPlayer() }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode:Boolean, newConfig:android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode,newConfig)
        if(::root.isInitialized) root.getChildAt(0)?.visibility=if(isInPictureInPictureMode) View.GONE else View.VISIBLE
    }
    private fun toast(s:String) = Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
    override fun onDestroy() { equalizer?.release(); player.release(); super.onDestroy() }
}
