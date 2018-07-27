package com.michael.amvplayer;

import android.Manifest;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.DialogInterface;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.PersistableBundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.michael.amvplayer.databinding.MainActivityBinding;
import com.michael.amvplayer.dialog.UxDialogViewModel;
import com.michael.amvplayer.dialog.UxDlgState;
import com.michael.amvplayer.dialog.UxFileDialog;
import com.michael.utils.UtLogger;
import com.michael.video.AmvCacheManager;
import com.michael.video.IAmvCache;

import org.jetbrains.annotations.NotNull;

import java.io.File;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnNeverAskAgain;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.RuntimePermissions;

/**
 * PermissionsDispatcherが動作するために必要なアノテーション
 * ActivityまたはFragmentのクラス宣言に付けます。
 */
@RuntimePermissions
public class MainActivity extends AppCompatActivity {

    MainActivityBinding mBinding = null;

    Uri[] mSourceUris = new Uri[] {
            Uri.parse("https://video.twimg.com/ext_tw_video/1003088826422603776/pu/vid/1280x720/u7R7uUhgWjPalQ0F.mp4?tag=3"),
            Uri.parse("https://video.twimg.com/ext_tw_video/1002595428049645570/pu/vid/720x1280/WNxL-rxdrGlM9wu_.mp4?tag=3"),
            Uri.parse("https://video.twimg.com/ext_tw_video/1021604619271557120/pu/vid/720x1280/2ffcbsSgNcZyXtMx.mp4?tag=3"),
            Uri.parse("https://video.twimg.com/ext_tw_video/1021376906610978816/pu/vid/720x1280/MiOHJiJJcMXBdUoq.mp4?tag=3") };

    Handler mHandler = new Handler();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UtLogger.debug("LC-Activity: onCreate");
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.main_activity);
        mBinding = DataBindingUtil.setContentView(this, R.layout.main_activity);
        mBinding.setLifecycleOwner(this);
        mBinding.setMainActivity(this);
//        mBinding.videoPlayer.setLayoutHint(FitMode.Inside, 1000, 1000);
//        mBinding.videoController.setVideoPlayer(mBinding.videoPlayer);

        UxDialogViewModel viewModel = ViewModelProviders.of(this).get(UxDialogViewModel.class);


        AmvCacheManager.INSTANCE.initialize(new File(getCacheDir(), ".video"));

        if(null==savedInstanceState) {
            Uri randomUri = mSourceUris[(int) (Math.random() * 4.0)];
            IAmvCache cache = AmvCacheManager.INSTANCE.getCache(randomUri, null);
            cache.getFile(new IAmvCache.IGotFileCallback() {
                @Override
                public void onGotFile(@NotNull IAmvCache cache, @org.jetbrains.annotations.Nullable final File file) {
                    if (null != file) {
                        UtLogger.debug("file retrieved");
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mCurrentFile = file;
                                mBinding.playerUnitView.setSource(file, false, 0);
                            }
                        });
                    } else {
                        UtLogger.error("file not retrieved.\n" + cache.getError().getMessage());
                    }
                }
            });
        }

        // mBinding.videoPlayer.setSource(new File("/storage/emulated/0/Download/b.mp4"), false);

        viewModel.getState().observe(this, new Observer<UxDialogViewModel.State>() {

            @Override
            public void onChanged(@Nullable UxDialogViewModel.State state) {
                if (null != state && state.getState() == UxDlgState.OK) {
                    onDialogResult(state);
                }
            }
        });
    }

    // ---

    @Override
    protected void onRestart() {
        UtLogger.debug("LC-Activity: onRestart");
        super.onRestart();
    }

    @Override
    protected void onStart() {
        UtLogger.debug("LC-Activity: onStart");
        super.onStart();
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        UtLogger.debug("LC-Activity: onRestoreInstanceState ... legacy style");
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState, PersistableBundle persistentState) {
        UtLogger.debug("LC-Activity: onRestoreInstanceState with PersistableBundle");
        super.onRestoreInstanceState(savedInstanceState, persistentState);
    }


    @Override
    protected void onResume() {
        UtLogger.debug("LC-Activity: onResume");
        super.onResume();
    }

    //----

    @Override
    protected void onPause() {
        UtLogger.debug("LC-Activity: onPause");
        super.onPause();
    }


    @Override
    protected void onStop() {
        UtLogger.debug("LC-Activity: onStop");
        super.onStop();
    }


    @Override
    protected void onDestroy() {
        UtLogger.debug("LC-Activity: onDestroy");
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        UtLogger.debug("LC-Activity: onSaveInstanceState ... legacy style");
        super.onSaveInstanceState(outState);
    }
    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        UtLogger.debug("LC-Activity: onSaveInstanceState ... with PersistableBundle");
        super.onSaveInstanceState(outState, outPersistentState);
    }

    // ----

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    public void onClickCamera(View view) {
        MainActivityPermissionsDispatcher.useCameraWithPermissionCheck(this);
    }

    public void onClickFile(View view) {
        MainActivityPermissionsDispatcher.selectFileWithPermissionCheck(this);
        //selectFile();
    }

    public void onTrimming(View view) {
        Intent intent = new Intent(this, TrimmingActivity.class);
        if(null!=mCurrentFile) {
            intent.putExtra("source", mCurrentFile);
        }
        startActivity(intent);
    }

//    public void onClickPlay(View view) {
//        mBinding.videoPlayer.play();
//    }
//    public void onClickPause(View view) {
//        mBinding.videoPlayer.pause();
//    }

    private File mCurrentFile = null;

    public void onDialogResult(@NonNull UxDialogViewModel.State state) {
        switch(state.getTag()) {
            case "FileDialog":
                Bundle bundle = state.getResult();
                if(null!=bundle) {
                    UxFileDialog.Status result = UxFileDialog.Status.unpack(bundle, null);
                    Log.d("Amv", String.format("FileDialog ... select \"%s\"", result.getFileName()));
                    File file = result.getFile();
                    if(null!=file) {
                        mCurrentFile = file;
                        mBinding.playerUnitView.setSource(file,false, 0);
                    }
//                    mBinding.videoPlayer.play();
                }
                break;
            default:
                break;
        }
    }

    @NeedsPermission({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    public void selectFile() {
        UxFileDialog.selectFile(this, "FileDialog");
    }

    /**
     * パーミッションが必要な処理の実体
     * （パーミッションを要求するダイアログで「許可」を選択した時、あるいは既に許可されている場合の処理）
     *
     * このメソッド名 + "WithPermissionCheck" が、パーミッションチェック付き呼び出しのメソッド名になる。
     * 例）MainActivityPermissionsDispatcher.useCameraWithPermissionCheck(this);
     */
    @NeedsPermission(Manifest.permission.CAMERA)
    public void useCamera() {
        try {
            // カメラを使う処理を記述する
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(intent, 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * パーミッション要求が拒否された場合の処理
     */
    @OnPermissionDenied(Manifest.permission.CAMERA)
    public void onCameraDenied() {
        Toast.makeText(this, "カメラ機能を利用できません。", Toast.LENGTH_LONG).show();
    }

    @OnShowRationale(Manifest.permission.CAMERA)
    public void showRationaleForCamera(final PermissionRequest request) {
        new AlertDialog.Builder(this)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(@NonNull DialogInterface dialog, int which) {
                        request.proceed();
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(@NonNull DialogInterface dialog, int which) {
                        request.cancel();
                    }
                })
                .setCancelable(false)
                .setMessage("カメラ機能を利用するためには、権限の許可が必要です。")
                .show();    }

    @OnNeverAskAgain(Manifest.permission.CAMERA)
    public void ifNeverAskAgain() {
        Toast.makeText(this, "設定画面からカメラ機能の利用を許可してください。", Toast.LENGTH_LONG).show();
    }
}
