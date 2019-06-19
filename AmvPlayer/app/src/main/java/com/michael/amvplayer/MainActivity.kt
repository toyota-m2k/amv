package com.michael.amvplayer

import android.Manifest
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import android.content.DialogInterface
import android.content.Intent
import androidx.databinding.DataBindingUtil
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.PersistableBundle
import android.provider.MediaStore
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast

import com.michael.amvplayer.databinding.MainActivityBinding
import com.michael.amvplayer.dialog.UxDialogViewModel
import com.michael.amvplayer.dialog.UxDlgState
import com.michael.amvplayer.dialog.UxFileDialog
import com.michael.utils.UtLogger
import com.michael.video.AmvCacheManager
import com.michael.video.AmvFileSource
import com.michael.video.AmvSettings
import com.michael.video.IAmvCache
import com.michael.video.IAmvLayoutHint
import com.michael.video.IAmvVideoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import java.io.File

import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnNeverAskAgain
import permissions.dispatcher.OnPermissionDenied
import permissions.dispatcher.OnShowRationale
import permissions.dispatcher.PermissionRequest
import permissions.dispatcher.RuntimePermissions

/**
 * PermissionsDispatcherが動作するために必要なアノテーション
 * ActivityまたはFragmentのクラス宣言に付けます。
 */
@RuntimePermissions
class MainActivity : AppCompatActivity() {

    internal var mBinding: MainActivityBinding? = null

    internal var mSourceUris = arrayOf(Uri.parse("https://video.twimg.com/ext_tw_video/1004721016608878592/pu/vid/640x360/WBhjCNo0V9kb2uR6.mp4"), Uri.parse("https://video.twimg.com/ext_tw_video/1003088826422603776/pu/vid/1280x720/u7R7uUhgWjPalQ0F.mp4"), Uri.parse("https://video.twimg.com/ext_tw_video/1020152626912956416/pu/vid/480x360/NjLXg8H4EHFeJ3fg.mp4"), Uri.parse("https://video.twimg.com/ext_tw_video/1020468235710283776/pu/vid/1280x720/hyxYIb70iGZCCo5F.mp4"), Uri.parse("https://video.twimg.com/ext_tw_video/1018568592378531840/pu/vid/512x640/hdHOlx2t9bBVi6GM.mp4"),

            Uri.parse("https://video.twimg.com/ext_tw_video/1018568592378531840/pu/vid/512x640/hdHOlx2t9bBVi6GM.mp4"), Uri.parse("https://video.twimg.com/ext_tw_video/1019794132678524928/pu/vid/480x480/1LF9gNupANoBL607.mp4"), Uri.parse("https://video.twimg.com/ext_tw_video/1019167439815208960/pu/vid/1268x720/Lvom3aNMLPqc3qxr.mp4"), Uri.parse("https://video.twimg.com/ext_tw_video/1018861232269324288/pu/vid/326x180/uCrA6o30QXO3vnMJ.mp4"), Uri.parse("https://video.twimg.com/ext_tw_video/1017036555649626112/pu/vid/720x720/2j7OkY23AF-nO7Ig.mp4"))

    internal var mHandler = Handler()

    private val keyCurrentFile = "currentFile"

    //    public void onClickPlay(View view) {
    //        mBinding.videoPlayer.play();
    //    }
    //    public void onClickPause(View view) {
    //        mBinding.videoPlayer.pause();
    //    }

    private var mCurrentFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        UtLogger.debug("LC-Activity: onCreate")
        super.onCreate(savedInstanceState)
        //        setContentView(R.layout.main_activity);
        mBinding = DataBindingUtil.setContentView(this, R.layout.main_activity)
        mBinding!!.lifecycleOwner = this
        mBinding!!.mainActivity = this
        //        mBinding.videoPlayer.setLayoutHint(FitMode.Inside, 1000, 1000);
        //        mBinding.videoController.setVideoPlayer(mBinding.videoPlayer);

        val viewModel = ViewModelProviders.of(this).get(UxDialogViewModel::class.java)

        // Cache Manager
        AmvSettings.initialize(cacheDir, 705)

        //        AmvCacheManager.initialize(new File("/storage/emulated/0/Movies", ".video"), 5);

        if (null == savedInstanceState) {
            onShuffle(null)
        }

        // mBinding.videoPlayer.setSource(new File("/storage/emulated/0/Download/b.mp4"), false);

        viewModel.state.observe(this, Observer { state ->
            if (null != state && state.state === UxDlgState.OK) {
                onDialogResult(state)
            }
        })
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

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        UtLogger.debug("LC-Activity: onRestoreInstanceState ... legacy style")
        mCurrentFile = savedInstanceState.getSerializable(keyCurrentFile) as File
        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle, persistentState: PersistableBundle) {
        UtLogger.debug("LC-Activity: onRestoreInstanceState with PersistableBundle")
        super.onRestoreInstanceState(savedInstanceState, persistentState)
    }


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
        mBinding!!.playerUnitView.videoPlayer.pause()
        super.onStop()
    }


    override fun onDestroy() {
        UtLogger.debug("LC-Activity: onDestroy")
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        UtLogger.debug("LC-Activity: onSaveInstanceState ... legacy style")
        outState.putSerializable(keyCurrentFile, mCurrentFile)
        super.onSaveInstanceState(outState)
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        UtLogger.debug("LC-Activity: onSaveInstanceState ... with PersistableBundle")
        super.onSaveInstanceState(outState, outPersistentState)
    }

    // ----

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)
    }

    fun onClickCamera(view: View) {
        useCameraWithPermissionCheck()
    }

    fun onClickFile(view: View) {
        selectFileWithPermissionCheck()
        //selectFile();
    }

    fun onTranscode(view: View) {
        val intent = Intent(this, TranscodeActivity::class.java)
        if (null != mCurrentFile) {
            intent.putExtra("source", mCurrentFile)
            startActivity(intent)
        }
    }

    fun onTrimming(view: View) {
        val intent = Intent(this, TrimmingActivity::class.java)
        if (null != mCurrentFile) {
            intent.putExtra("source", mCurrentFile)
            startActivity(intent)
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
                    CoroutineScope(Dispatchers.Main).launch {
                        mBinding!!.playerUnitView.setSource(AmvFileSource(file), false, 0, true)
                    }
                } else {
                    UtLogger.error("file not retrieved.\n" + cache.error.message)
                }
            }
        })
    }

    fun onSelectFrame(view: View) {
        val intent = Intent(this, SelectFrameActivity::class.java)
        if (null != mCurrentFile) {
            intent.putExtra("source", mCurrentFile)
            startActivity(intent)
        }
    }

    fun onExpand(view: View) {
        val player = mBinding!!.playerUnitView.videoPlayer
        val hint = player.getLayoutHint()
        player.setLayoutHint(hint.fitMode, hint.layoutWidth * 1.2f, hint.layoutHeight * 1.2f)

    }

    fun onReduce(view: View) {
        val player = mBinding!!.playerUnitView.videoPlayer
        val hint = player.getLayoutHint()
        player.setLayoutHint(hint.fitMode, hint.layoutWidth / 1.2f, hint.layoutHeight / 1.2f)
    }

    fun onDialogResult(state: UxDialogViewModel.State) {
        when (state.tag) {
            "FileDialog" -> {
                val bundle = state.result
                if (null != bundle) {
                    val result = UxFileDialog.Status.unpack(bundle)
                    Log.d("Amv", String.format("FileDialog ... select \"%s\"", result.fileName))
                    val file = result.file
                    if (null != file) {
                        mCurrentFile = file
                        CoroutineScope(Dispatchers.Main).launch {
                            mBinding!!.playerUnitView.setSource(AmvFileSource(file), false, 0, true)
                        }
                    }
                    //                    mBinding.videoPlayer.play();
                }
            }
            else -> {
            }
        }
    }

    @NeedsPermission(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    fun selectFile() {
        UxFileDialog.selectFile(this, "FileDialog")
    }

    /**
     * パーミッションが必要な処理の実体
     * （パーミッションを要求するダイアログで「許可」を選択した時、あるいは既に許可されている場合の処理）
     *
     * このメソッド名 + "WithPermissionCheck" が、パーミッションチェック付き呼び出しのメソッド名になる。
     * 例）MainActivityPermissionsDispatcher.useCameraWithPermissionCheck(this);
     */
    @NeedsPermission(Manifest.permission.CAMERA)
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
    @OnPermissionDenied(Manifest.permission.CAMERA)
    fun onCameraDenied() {
        Toast.makeText(this, "カメラ機能を利用できません。", Toast.LENGTH_LONG).show()
    }

    @OnShowRationale(Manifest.permission.CAMERA)
    fun showRationaleForCamera(request: PermissionRequest) {
        AlertDialog.Builder(this)
                .setPositiveButton(android.R.string.ok) { dialog, which -> request.proceed() }
                .setNegativeButton(android.R.string.no) { dialog, which -> request.cancel() }
                .setCancelable(false)
                .setMessage("カメラ機能を利用するためには、権限の許可が必要です。")
                .show()
    }

    @OnNeverAskAgain(Manifest.permission.CAMERA)
    fun ifNeverAskAgain() {
        Toast.makeText(this, "設定画面からカメラ機能の利用を許可してください。", Toast.LENGTH_LONG).show()
    }

    companion object {


        val workFolder: File
            get() {
                val file = File("/storage/emulated/0/Movies/amv")
                if (!file.exists()) {
                    file.mkdir()
                }
                return file
            }
    }
}
