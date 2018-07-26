package com.michael.amvplayer;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Intent;
import android.databinding.BaseObservable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.michael.amvplayer.dialog.UxDialogViewModel;
import com.michael.amvplayer.dialog.UxDlgState;
import com.michael.amvplayer.dialog.UxFileDialog;
import com.michael.utils.UtLogger;
import com.michael.video.AmvTranscoder;
import com.michael.video.AmvWorkingSurfaceView;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;

//import com.michael.amvplayer.databinding.TrimmingActivityBinding;

public class TrimmingActivity extends AppCompatActivity {

//    private TrimmingActivityBinding mBinding;

    public class BindingParams extends BaseObservable {
        private String _mediaInfoString;

//        @Bindable
        public String getMediaInfoString() {
            return _mediaInfoString;
        }

        public void setMediaInfoString(String v) {
            _mediaInfoString = v;
//            notifyPropertyChanged(BR.mediaInfoString);
        }
    }
    public BindingParams bindingParams = new BindingParams();
    public AmvTranscoder transcoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UtLogger.debug("LC-TrimmingActivity: onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.trimming_activity);
//        mBinding = DataBindingUtil.setContentView(this, R.layout.trimming_activity);
//        mBinding.setLifecycleOwner(this);
//        mBinding.setTrimmingActivity(this);
//        mBinding.setParams(bindingParams);

        findViewById(R.id.closeButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onCloseButton();
            }
        });
        findViewById(R.id.transcodeButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTranscodeButton();
            }
        });


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

        if(null!=mSource) {
            transcoder = new AmvTranscoder(mSource, (AmvWorkingSurfaceView)findViewById(R.id.surfaceView), getApplicationContext());
//            AmvMediaInfo mi = new AmvMediaInfo(mSource, getApplicationContext());
            TextView view = findViewById(R.id.infoText);
            view.setText(transcoder.getMediaInfo().getSummary());
        }

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

    public void onCloseButton() {
        finish();
    }

    public void onTranscodeButton() {
        //AmvTranscoder tc = new AmvTranscoder(mSource, this);
        transcoder.getCompletionListener().set(new AmvTranscoder.CompletionListener.IHandler() {
            @Override
            public void onCompleted(@NotNull AmvTranscoder sender, boolean result) {
                UtLogger.debug("Transcode done (%s)", result?"True":"False");
            }
        });
        transcoder.getProgressListener().set(new AmvTranscoder.ProgressListener.IHandler() {
            @Override
            public void onProgress(@NotNull AmvTranscoder sender, float progress) {
                UtLogger.debug("Transcode progress %d %%", (int)(progress*100));
            }
        });
        try {
            File path = File.createTempFile("TC_TEST", ".mp4", mSource.getParentFile());
            transcoder.transcode(path);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
