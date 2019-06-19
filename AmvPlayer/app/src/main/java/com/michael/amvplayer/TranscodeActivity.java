package com.michael.amvplayer;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import android.content.Intent;
import androidx.databinding.BaseObservable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
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

public class TranscodeActivity extends AppCompatActivity {

    public class BindingParams extends BaseObservable {
        private String _mediaInfoString;

        public String getMediaInfoString() {
            return _mediaInfoString;
        }

        public void setMediaInfoString(String v) {
            _mediaInfoString = v;
        }
    }
    public BindingParams bindingParams = new BindingParams();
    public AmvTranscoder transcoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UtLogger.debug("LC-TranscodeActivity: onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.transcode_activity);

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
            transcoder = new AmvTranscoder(mSource, getApplicationContext());
//            AmvMediaInfo mi = new AmvMediaInfo(mSource, getApplicationContext());
            AmvWorkingSurfaceView surfaceView = findViewById(R.id.surfaceView);
            Size size = transcoder.getMediaInfo().getHd720Size();
            surfaceView.setVideoSize(size.getWidth(), size.getHeight());
            transcoder.setSurfaceView(surfaceView);
            ((TextView)findViewById(R.id.infoText)).setText(transcoder.getMediaInfo().getSummary());
        }

    }

    File mSource;

    public void onDialogResult(@NonNull UxDialogViewModel.State state) {
        switch(state.getTag()) {
            case "FileDialog":
                Bundle bundle = state.getResult();
                if(null!=bundle) {
                    UxFileDialog.Status result = UxFileDialog.Status.unpack(bundle);
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
        transcoder.setCompletionListener(new AmvTranscoder.ICompletionEventHandler() {
            @Override
            public void onCompleted(@NotNull AmvTranscoder sender, boolean result) {
                UtLogger.debug("Transcode done (%s)", result?"True":"False");
            }
        });
        transcoder.setProgressListener(new AmvTranscoder.IProgressEventHandler() {
            @Override
            public void onProgress(@NotNull AmvTranscoder sender, float progress) {
                UtLogger.debug("Transcode progress %d %%", (int)(progress*100));
            }
        });
        try {
            File path = File.createTempFile("TCOD_TEST", ".mp4", MainActivity.Companion.getWorkFolder());
            transcoder.transcode(path);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
