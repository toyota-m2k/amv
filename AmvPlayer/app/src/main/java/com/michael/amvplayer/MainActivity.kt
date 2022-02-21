package com.michael.amvplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.michael.video.*
import io.github.toyota32k.bindit.Binder
import io.github.toyota32k.bindit.Command
import io.github.toyota32k.dialog.broker.pickers.UtFilePickerStore
import io.github.toyota32k.dialog.task.UtMortalActivity
import io.github.toyota32k.utils.UtLogger
import kotlinx.coroutines.launch
import java.io.File


/**
 * PermissionsDispatcherが動作するために必要なアノテーション
 * ActivityまたはFragmentのクラス宣言に付けます。
 */
class MainActivity : UtMortalActivity() {

    private var mHandler = Handler(Looper.getMainLooper())

    private val mSourceUris = arrayOf(
            Uri.parse("https://video.twimg.com/ext_tw_video/1004721016608878592/pu/vid/640x360/WBhjCNo0V9kb2uR6.mp4"),
            Uri.parse("https://video.twimg.com/ext_tw_video/1003088826422603776/pu/vid/1280x720/u7R7uUhgWjPalQ0F.mp4"),
            Uri.parse("https://video.twimg.com/ext_tw_video/1020152626912956416/pu/vid/480x360/NjLXg8H4EHFeJ3fg.mp4"),
            Uri.parse("https://video.twimg.com/ext_tw_video/1020468235710283776/pu/vid/1280x720/hyxYIb70iGZCCo5F.mp4"),
            Uri.parse("https://video.twimg.com/ext_tw_video/1018568592378531840/pu/vid/512x640/hdHOlx2t9bBVi6GM.mp4"),
            Uri.parse("https://video.twimg.com/ext_tw_video/1018568592378531840/pu/vid/512x640/hdHOlx2t9bBVi6GM.mp4"),
            Uri.parse("https://video.twimg.com/ext_tw_video/1019794132678524928/pu/vid/480x480/1LF9gNupANoBL607.mp4"),
            Uri.parse("https://video.twimg.com/ext_tw_video/1019167439815208960/pu/vid/1268x720/Lvom3aNMLPqc3qxr.mp4"),
            Uri.parse("https://video.twimg.com/ext_tw_video/1018861232269324288/pu/vid/326x180/uCrA6o30QXO3vnMJ.mp4"),
            Uri.parse("https://video.twimg.com/ext_tw_video/1017036555649626112/pu/vid/720x720/2j7OkY23AF-nO7Ig.mp4"))

//    private var mHandler = Handler()


    //    public void onClickPlay(View view) {
    //        mBinding.videoPlayer.play();
    //    }
    //    public void onClickPause(View view) {
    //        mBinding.videoPlayer.pause();
    //    }

    private var mCurrentFile: File? = null

    val binder = Binder()
    val commandTrimming = Command()
    val commandSelectFrame = Command()

    override fun onCreate(savedInstanceState: Bundle?) {
        UtLogger.debug("LC-Activity: onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity);
        //        mBinding.videoPlayer.setLayoutHint(FitMode.Inside, 1000, 1000);
        //        mBinding.videoController.setVideoPlayer(mBinding.videoPlayer);

//        val viewModel = ViewModelProviders.of(this).get(UxDialogViewModel::class.java)
//
        binder.register(
            commandTrimming.connectAndBind(this, findViewById<Button>(R.id.trimming_button), this::onTrimming),
            commandSelectFrame.connectAndBind(this, findViewById<Button>(R.id.selectFrame), this::onSelectFrame),
        )

        // Cache Manager
        AmvSettings.initialize(this, cacheDir, 705, true, null)
//
//        //        AmvCacheManager.initialize(new File("/storage/emulated/0/Movies", ".video"), 5);
//
//        if (null == savedInstanceState) {
//            onShuffle(null)
//        }
//
//        // mBinding.videoPlayer.setSource(new File("/storage/emulated/0/Download/b.mp4"), false);
//
//        viewModel.state.observe(this, Observer { state ->
//            if (null != state && state.state === UxDlgState.OK) {
//                onDialogResult(state)
//            }
//        })
        checkPermission()
    }

    // ---

    override fun onRestart() {
        UtLogger.debug("LC-Activity: onRestart")
        super.onRestart()
    }

    override fun onStart() {
        UtLogger.debug("LC-Activity: onStart")
        super.onStart()
    }

//    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
//        UtLogger.debug("LC-Activity: onRestoreInstanceState ... legacy style")
//        val file = savedInstanceState.getSerializable(KEY_CURRENT_FILE) as? File
//        if(file!=null) {
//            mCurrentFile = file
//            val pos = savedInstanceState.getLong(KEY_SEEK_POSITION)
//            val play = savedInstanceState.getBoolean(KEY_IS_PLAYING)
//        }
//        super.onRestoreInstanceState(savedInstanceState)
//    }

//    override fun onRestoreInstanceState(savedInstanceState: Bundle, persistentState: PersistableBundle) {
//        UtLogger.debug("LC-Activity: onRestoreInstanceState with PersistableBundle")
//        super.onRestoreInstanceState(savedInstanceState, persistentState)
//    }


    override fun onResume() {
        UtLogger.debug("LC-Activity: onResume")
        super.onResume()
    }

    //----

    override fun onPause() {
        UtLogger.debug("LC-Activity: onPause")
        super.onPause()
    }


    override fun onStop() {
        UtLogger.debug("LC-Activity: onStop")
//        mBinding!!.playerUnitView.videoPlayer.pause()
        super.onStop()
    }


    override fun onDestroy() {
        UtLogger.debug("LC-Activity: onDestroy")
        super.onDestroy()
    }

//    override fun onSaveInstanceState(outState: Bundle) {
//        UtLogger.debug("LC-Activity: onSaveInstanceState ... legacy style")
//        outState.putSerializable(KEY_CURRENT_FILE, mCurrentFile)
//        super.onSaveInstanceState(outState)
//    }

//    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
//        UtLogger.debug("LC-Activity: onSaveInstanceState ... with PersistableBundle")
//        super.onSaveInstanceState(outState, outPersistentState)
//    }

    // ----

//    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        onRequestPermissionsResult(requestCode, grantResults)
//    }

    fun onClickCamera(view: View) {
        useCamera()
    }

    fun onClickFile(view: View) {
        selectFile()
        //selectFile();
    }

    fun onTranscode(view: View) {
        val intent = Intent(this, TranscodeActivity::class.java)
        if (null != mCurrentFile) {
            intent.putExtra("source", mCurrentFile)
            startActivity(intent)
        }
    }

    val filePickers: UtFilePickerStore = UtFilePickerStore(this)

    fun onTrimming(view: View?) {
        lifecycleScope.launch {
            val uri = filePickers.openFilePicker.selectFile(arrayOf("video/mp4", "video/*"))
            if(uri!=null) {
                val intent = Intent(this@MainActivity, TrimmingActivity::class.java)
                intent.putExtra("source", uri)
                startActivity(intent)
            }
        }
    }

    fun onShuffle(view: View?) {
        val rand = (Math.random() * mSourceUris.size.toDouble()).toInt()
        UtLogger.debug("Shuffle : %d", rand)

        val randomUri = mSourceUris[rand]
        val cache = AmvCacheManager.getCache(randomUri, null)
        cache.getFile(object : IAmvCache.IGotFileCallback {
            override fun onGotFile(cache: IAmvCache, file: File?) {
                if (null != file) {
                    UtLogger.debug("file retrieved")
                    mCurrentFile = file
                } else {
                    UtLogger.error("file not retrieved.\n" + cache.error.message)
                }
            }
        })
    }

    fun onSelectFrame(view: View?) {
        lifecycleScope.launch {
            val uri = filePickers.openFilePicker.selectFile(arrayOf("video/mp4", "video/*"))
            if(uri!=null) {
                val intent = Intent(this@MainActivity, SelectFrameActivity::class.java)
                intent.putExtra("source", uri)
                startActivity(intent)
            }
        }
    }

    fun onExpand(view: View) {
//        val player = mBinding!!.playerUnitView.videoPlayer
//        val hint = player.getLayoutHint()
//        player.setLayoutHint(hint.fitMode, hint.layoutWidth * 1.2f, hint.layoutHeight * 1.2f)

    }

    fun onReduce(view: View) {
//        val player = mBinding!!.playerUnitView.videoPlayer
//        val hint = player.getLayoutHint()
//        player.setLayoutHint(hint.fitMode, hint.layoutWidth / 1.2f, hint.layoutHeight / 1.2f)
    }

//    fun onDialogResult(state: UxDialogViewModel.State) {
//        when (state.tag) {
//            "FileDialog" -> {
//                val bundle = state.result
//                if (null != bundle) {
//                    val result = UxFileDialog.Status.unpack(bundle)
//                    Log.d("Amv", String.format("FileDialog ... select \"%s\"", result.fileName))
//                    val file = result.file
//                    if (null != file) {
//                        mCurrentFile = file
//                        mBinding!!.playerUnitView.setSource(AmvFileSource(file), false, 0, true)
//                    }
//                    //                    mBinding.videoPlayer.play();
//                }
//            }
//            else -> {
//            }
//        }
//    }

//    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun selectFile() {
//        UxFileDialog.selectFile(this, "FileDialog")
    }

    /**
     * パーミッションが必要な処理の実体
     * （パーミッションを要求するダイアログで「許可」を選択した時、あるいは既に許可されている場合の処理）
     *
     * このメソッド名 + "WithPermissionCheck" が、パーミッションチェック付き呼び出しのメソッド名になる。
     * 例）MainActivityPermissionsDispatcher.useCameraWithPermissionCheck(this);
     */
    fun useCamera() {
        try {
            // カメラを使う処理を記述する
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(intent, 1)
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * パーミッション要求が拒否された場合の処理
     */
//    fun onCameraDenied() {
//        Toast.makeText(this, "カメラ機能を利用できません。", Toast.LENGTH_LONG).show()
//    }
//
//    fun showRationaleForCamera(request: PermissionRequest) {
//        AlertDialog.Builder(this)
//                .setPositiveButton(android.R.string.ok) { dialog, which -> request.proceed() }
//                .setNegativeButton(android.R.string.no) { dialog, which -> request.cancel() }
//                .setCancelable(false)
//                .setMessage("カメラ機能を利用するためには、権限の許可が必要です。")
//                .show()
//    }
//
//    @OnNeverAskAgain(Manifest.permission.CAMERA)
//    fun ifNeverAskAgain() {
//        Toast.makeText(this, "設定画面からカメラ機能の利用を許可してください。", Toast.LENGTH_LONG).show()
//    }

    companion object {


        val workFolder: File
            get() {
                val file = File("/storage/emulated/0/Movies/amv")
                if (!file.exists()) {
                    file.mkdir()
                }
                return file
            }

        private const val KEY_CURRENT_FILE = "currentFile"
        private const val KEY_IS_PLAYING = "isPlaying"
        private const val KEY_SEEK_POSITION = "seekPosition"
    }

    private val MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 100

    private fun checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.CAMERA)
            }
            if(permissions.size>0) {
                Toast.makeText(this, "App required access to external storage and camera", Toast.LENGTH_SHORT).show()
                requestPermissions(permissions.toTypedArray(), MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
