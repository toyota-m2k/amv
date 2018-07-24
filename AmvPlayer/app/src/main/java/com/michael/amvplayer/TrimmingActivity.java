package com.michael.amvplayer;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.michael.amvplayer.databinding.TrimmingActivityBinding;
import com.michael.amvplayer.dialog.UxDialogViewModel;
import com.michael.amvplayer.dialog.UxDlgState;
import com.michael.amvplayer.dialog.UxFileDialog;
import com.michael.utils.UtLogger;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;

public class TrimmingActivity extends AppCompatActivity {

    private TrimmingActivityBinding mBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UtLogger.debug("LC-TrimmingActivity: onCreate");

        super.onCreate(savedInstanceState);
//        setContentView(R.layout.main_activity);
        mBinding = DataBindingUtil.setContentView(this, R.layout.trimming_activity);
        mBinding.setLifecycleOwner(this);
        mBinding.setTrimmingActivity(this);
//        mBinding.videoPlayer.setLayoutHint(FitMode.Inside, 1000, 1000);
//        mBinding.videoController.setVideoPlayer(mBinding.videoPlayer);

        UxDialogViewModel viewModel = ViewModelProviders.of(this).get(UxDialogViewModel.class);
        viewModel.getState().observe(this, new Observer<UxDialogViewModel.State>() {

            @Override
            public void onChanged(@Nullable UxDialogViewModel.State state) {
                if(null!=state && state.getState() == UxDlgState.OK) {
                    onDialogResult(state);
                }
            }
        });

        Intent intent = getIntent();
        Serializable s = intent.getSerializableExtra("source");
        mSource = (File)s;


    }

    File mSource;

    public void onDialogResult(@NonNull UxDialogViewModel.State state) {
        switch(state.getTag()) {
            case "FileDialog":
                Bundle bundle = state.getResult();
                if(null!=bundle) {
                    UxFileDialog.Status result = UxFileDialog.Status.unpack(bundle, null);
                    Log.d("Amv", String.format("FileDialog ... select \"%s\"", result.getFileName()));
                    File file = result.getFile();
                    if(null!=file) {
                        //
                    }
                }
                break;
            default:
                break;
        }
    }

    public void onCloseButton(View view) {
        finish();
    }
}
