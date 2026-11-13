package com.aishhatorah.filemanagerpro;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.BaseDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSourceException;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;

import java.io.File;
import java.io.IOException;

/**
 * ספק נתונים עבור Media3 ExoPlayer המזהה קבצי .mts / .m2ts (AVCHD 192-byte TS)
 * ומספק ל-TsExtractor זרם 188-byte מיושר וחלק עם אורך ידוע ודילוג מהיר בכל נקודת זמן.
 */
@UnstableApi
public final class NormalizedTsExoDataSource extends BaseDataSource {

    public static final class Factory implements DataSource.Factory {
        private final DefaultDataSource.Factory fallbackFactory;

        public Factory(Context context) {
            this.fallbackFactory = new DefaultDataSource.Factory(context.getApplicationContext());
        }

        @Override
        public DataSource createDataSource() {
            return new NormalizedTsExoDataSource(fallbackFactory.createDataSource());
        }
    }

    private final DataSource fallbackDataSource;
    @Nullable
    private MpegTsNormalizer.NormalizedTsStreamReader tsReader;
    @Nullable
    private Uri currentUri;
    private long readPosition;
    private long bytesRemaining;
    private boolean opened;
    private boolean usingFallback;

    public NormalizedTsExoDataSource(DataSource fallbackDataSource) {
        super(false);
        this.fallbackDataSource = fallbackDataSource;
    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        Uri uri = dataSpec.uri;
        this.currentUri = uri;

        String scheme = uri.getScheme();
        if ((scheme == null || "file".equalsIgnoreCase(scheme)) && uri.getPath() != null) {
            File file = new File(uri.getPath());
            MpegTsNormalizer.TsFormatInfo info = MpegTsNormalizer.detectTsLayout(file);
            if (info.needsNormalization()) {
                transferInitializing(dataSpec);
                long virtualLength = info.getVirtualLength();
                if (dataSpec.position > virtualLength) {
                    throw new DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
                }
                this.tsReader = new MpegTsNormalizer.NormalizedTsStreamReader(file, info);
                this.readPosition = dataSpec.position;
                if (dataSpec.length != C.LENGTH_UNSET) {
                    this.bytesRemaining = Math.min(dataSpec.length, virtualLength - readPosition);
                } else {
                    this.bytesRemaining = virtualLength - readPosition;
                }
                this.usingFallback = false;
                this.opened = true;
                transferStarted(dataSpec);
                return this.bytesRemaining;
            }
        }

        this.usingFallback = true;
        long result = fallbackDataSource.open(dataSpec);
        this.opened = true;
        return result;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        if (usingFallback) {
            return fallbackDataSource.read(buffer, offset, length);
        }
        if (tsReader == null || bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT;
        }
        int toRead = (int) Math.min(length, bytesRemaining);
        int read = tsReader.readAt(readPosition, buffer, offset, toRead);
        if (read <= 0) {
            return C.RESULT_END_OF_INPUT;
        }
        readPosition += read;
        bytesRemaining -= read;
        bytesTransferred(read);
        return read;
    }

    @Override
    @Nullable
    public Uri getUri() {
        return usingFallback ? fallbackDataSource.getUri() : currentUri;
    }

    @Override
    public void close() throws IOException {
        currentUri = null;
        try {
            if (usingFallback) {
                fallbackDataSource.close();
            } else if (tsReader != null) {
                tsReader.close();
            }
        } finally {
            tsReader = null;
            usingFallback = false;
            if (opened) {
                opened = false;
                transferEnded();
            }
        }
    }
}
