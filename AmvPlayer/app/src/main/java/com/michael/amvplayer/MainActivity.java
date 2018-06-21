package com.michael.amvplayer;

import android.Manifest;
import android.content.DialogInterface;
import android.databinding.DataBindingUtil;
import android.graphics.Camera;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.michael.amvplayer.databinding.MainActivityBinding;

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
        binding.setMainActivity(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        MainActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    public void onClickCamera(View view) {
        MainActivityPermissionsDispatcher.useCameraWithPermissionCheck(this);
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
        // カメラを使う処理を記述する
        try {
            // Camera camera = new Camera();

        } catch (Exception e) {
            e.printStackTrace();
        }
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
