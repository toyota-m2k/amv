package com.michael.amvplayer;

import android.Manifest;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.DialogInterface;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.graphics.Camera;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.michael.amvplayer.databinding.MainActivityBinding;

import kotlin.Unit;
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

    public final String Message = "Hoge Fuga";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.main_activity);
        MainActivityBinding binding = DataBindingUtil.setContentView(this, R.layout.main_activity);
        binding.setLifecycleOwner(this);
        binding.setMainActivity(this);

        UiFileSelectionDialog.FileSelectorViewModel model = ViewModelProviders.of(this).get(UiFileSelectionDialog.FileSelectorViewModel.class);
        model.getState().observe(this, new Observer<UiFileSelectionDialog.DlgState>() {
            @Override
            public void onChanged(@Nullable UiFileSelectionDialog.DlgState state) {
                switch(state) {
                    case OK:
                        Log.d("Amv", "OK");
                        break;
                    case CANCELED:
                        Log.d("Amv", "Canceled");
                        break;
                    default:
                        Log.d("Amv", "(INITIALIZED)");
                        break;
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    public void onClickCamera(View view) {
        MainActivityPermissionsDispatcher.useCameraWithPermissionCheck(this);
    }

    public void onClickFile(View view) {
        UiFileSelectionDialog.Companion.newInstance().show(this.getSupportFragmentManager(), "hoge");
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
