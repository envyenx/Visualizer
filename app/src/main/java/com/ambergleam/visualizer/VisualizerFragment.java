package com.ambergleam.visualizer;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.Visualizer;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.ambergleam.visualizer.utils.StringUtils;

import java.lang.reflect.Field;

public class VisualizerFragment extends Fragment {

    private static final String TAG = VisualizerFragment.class.getSimpleName();

    private static final String KEY_AUDIO_ID = TAG + ".audioid";
    private static final String KEY_POSITION = TAG + ".position";
    private static final String KEY_FILE_NAME = TAG + ".filename";

    private MediaPlayer mMediaPlayer;
    private Visualizer mVisualizer;

    private LinearLayout mScreen;
    private FrameLayout mFrame;
    private VisualizerView mVisualizerView;

    private TextView mTitleTextView;
    private TextView mCurrentTimeTextView;
    private TextView mDurationTimeTextView;

    private SeekBar mSeekBar;
    private Handler mSeekBarHandler = new Handler();

    private String mFileName;
    private int mAudioId;
    private int mPosition;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_visualizer, container, false);
        setHasOptionsMenu(true);

        getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);

        mScreen = (LinearLayout) view.findViewById(R.id.screen);
        mScreen.setVisibility(View.INVISIBLE);

        mFrame = (FrameLayout) view.findViewById(R.id.frame);
        mTitleTextView = (TextView) view.findViewById(R.id.title);
        mCurrentTimeTextView = (TextView) view.findViewById(R.id.time_current);
        mDurationTimeTextView = (TextView) view.findViewById(R.id.time_total);
        mSeekBar = (SeekBar) view.findViewById(R.id.seek_bar);

        if (savedInstanceState != null) {
            mFileName = savedInstanceState.getString(KEY_FILE_NAME, null);
            mAudioId = savedInstanceState.getInt(KEY_AUDIO_ID, 0);
            mPosition = savedInstanceState.getInt(KEY_POSITION, 0);
            if (mFileName != null && mAudioId != 0) {
                setup();
            }
        } else {
            mFileName = null;
            mAudioId = 0;
            mPosition = 0;
        }

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mVisualizer != null) {
            mVisualizer.release();
        }
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu, menu);
        MenuItem menuItemFeedback = menu.findItem(R.id.action_feedback);
        MenuItem menuItemRestart = menu.findItem(R.id.action_restart);
        if (mAudioId == 0) {
            menuItemFeedback.setVisible(false);
            menuItemRestart.setVisible(false);
        } else {
            menuItemFeedback.setVisible(true);
            menuItemRestart.setVisible(true);
            if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                menuItemFeedback.setIcon(android.R.drawable.ic_media_pause);
            } else {
                menuItemFeedback.setIcon(android.R.drawable.ic_media_play);
            }
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_feedback:
                if (mMediaPlayer != null) {
                    if (mMediaPlayer.isPlaying()) {
                        pause();
                    } else {
                        resume();
                    }
                }
                return true;
            case R.id.action_restart:
                if (mMediaPlayer != null) {
                    if (mMediaPlayer.isPlaying()) {
                        reset();
                        resume();
                    } else {
                        reset();
                    }
                }
                return true;
            case R.id.action_search:
                showDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putInt(KEY_AUDIO_ID, mAudioId);
        bundle.putInt(KEY_POSITION, mPosition);
        bundle.putString(KEY_FILE_NAME, mFileName);
    }

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            startTime();
        }
    };

    private void startTime() {
        updateTime();
        mPosition += 1000;
        mSeekBar.setProgress(mPosition);

        mSeekBarHandler.postDelayed(mRunnable, 1000);
    }

    private void stopTime() {
        mSeekBarHandler.removeCallbacks(mRunnable);
    }

    private void updateTime() {
        mCurrentTimeTextView.setText(StringUtils.getTimeFormatted(mPosition));
    }

    private void setAudio(String name) {
        try {
            Field field = R.raw.class.getField(name);
            int audioId = field.getInt(field);
            if (mAudioId != audioId) {
                if (mMediaPlayer != null) {
                    destroy();
                }
                mAudioId = audioId;
                mFileName = StringUtils.getFileNameFormatted(name);
                setup();
            }
        } catch (IllegalAccessException e) {
            Log.e(TAG, "IllegalAccessException: " + e.getStackTrace().toString());
        } catch (NoSuchFieldException e) {
            Log.e(TAG, "NoSuchFieldException: " + e.getStackTrace().toString());
        }
    }

    private void setup() {
        mVisualizerView = new VisualizerView(getActivity());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        mVisualizerView.setLayoutParams(params);
        mFrame.addView(mVisualizerView);

        mMediaPlayer = MediaPlayer.create(getActivity(), mAudioId);
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mPosition = 0;
                mSeekBar.setProgress(0);
                mMediaPlayer.pause();
                stopTime();
                updateTime();
                getActivity().invalidateOptionsMenu();
            }
        });

        mVisualizer = new Visualizer(mMediaPlayer.getAudioSessionId());
        mVisualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
        mVisualizer.setDataCaptureListener(mOnDataCaptureListener, Visualizer.getMaxCaptureRate() / 2, true, false);
        mVisualizer.setEnabled(true);

        updateUI();
        getActivity().invalidateOptionsMenu();
    }

    private void updateUI() {
        mScreen.setVisibility(View.VISIBLE);
        mTitleTextView.setText(mFileName);
        mDurationTimeTextView.setText(StringUtils.getTimeFormatted(mMediaPlayer.getDuration()));
        updateTime();
        mSeekBar.setMax(mMediaPlayer.getDuration());
        mSeekBar.setProgress(mPosition);
        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    if (mMediaPlayer.isPlaying()) {
                        pause();
                        mPosition = progress;
                        updateTime();
                        resume();
                    } else {
                        mPosition = progress;
                        updateTime();
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    private void pause() {
        mMediaPlayer.pause();
        stopTime();
        getActivity().invalidateOptionsMenu();
    }

    private void reset() {
        mPosition = 0;
        mSeekBar.setProgress(0);
        if (mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
        }
        stopTime();
        updateTime();
        getActivity().invalidateOptionsMenu();
    }

    private void resume() {
        mMediaPlayer.seekTo(mPosition);
        mMediaPlayer.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(MediaPlayer mp) {
                mMediaPlayer.start();
                startTime();
                getActivity().invalidateOptionsMenu();
            }
        });
    }

    private void destroy() {
        mPosition = 0;
        mSeekBar.setProgress(0);
        mMediaPlayer.stop();
        stopTime();
        updateTime();
        mVisualizer.setEnabled(false);
        mFrame.removeView(mVisualizerView);
    }

    private void showDialog() {
        final ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), android.R.layout.select_dialog_singlechoice);
        Field[] fields = R.raw.class.getFields();
        for (Field field : fields) {
            adapter.add(StringUtils.getFileNameFormatted(field.getName()));
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Select Audio:");
        builder.setIcon(android.R.drawable.ic_menu_search);
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int position) {
                        setAudio(StringUtils.getFileNameRaw(adapter.getItem(position)));
                    }
                }
        );
        builder.show();
    }

    private Visualizer.OnDataCaptureListener mOnDataCaptureListener = new Visualizer.OnDataCaptureListener() {

        public void onWaveFormDataCapture(Visualizer visualizer, byte[] bytes, int samplingRate) {
            mVisualizerView.updateVisualizer(bytes);
        }

        public void onFftDataCapture(Visualizer visualizer, byte[] bytes, int samplingRate) {
        }

    };

}
