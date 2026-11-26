package com.aishhatorah.filemanagerpro.viewer;

import android.os.Handler;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.aishhatorah.filemanagerpro.ZoomableImageView;

import java.io.File;
import java.util.concurrent.ExecutorService;

/**
 * ממשק מתווך נקי בין BuiltInViewerActivity לבקרי הצפייה והניגון המודולריים.
 */
public interface ViewerHost {
    AppCompatActivity getActivity();

    ExecutorService getExecutor();

    Handler getUiHandler();

    File getTargetFile();

    void setTargetFile(File file);

    TextView getMarqueeTitleView();

    TextView getFolderTrackCounterView();

    void setHeaderViews(TextView marqueeTitle, TextView trackCounter, String initialName);

    void updateMarqueeHeaderName(String name);

    void setArchiveNavigateUpHandler(Runnable handler);

    void setArchiveMode(boolean active);

    void registerMediaChrome(
            boolean imageMode,
            boolean videoMode,
            boolean pdfMode,
            FrameLayout stageRoot,
            FrameLayout viewport,
            View topOverlay,
            View bottomPanel,
            View prevFloat,
            View nextFloat,
            View centerOverlay,
            View lockFloatingBtn,
            ZoomableImageView zoomView
    );

    boolean isMediaControlsHidden();

    void setMediaControlsHidden(boolean hidden);

    void toggleMediaControlsVisibility();

    void applyMediaImmersiveAndLayoutState();

    boolean isVideoLocked();

    void setVideoLocked(boolean locked);

    boolean isInPipMode();

    void setInPipMode(boolean inPip);

    int getLastVideoTopInsetPx();

    void setLastVideoTopInsetPx(int px);

    void proceedCloseOrNavigateBack();

    void shareCurrentFile();

    void confirmDeleteViewedFile();
}
