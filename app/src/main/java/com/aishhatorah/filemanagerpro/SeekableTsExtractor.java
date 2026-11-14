package com.aishhatorah.filemanagerpro;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.SeekPoint;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * מחלץ MPEG-TS ייעודי לקבצי .mts / .m2ts / .ts:
 * 1) מבטיח שקובצי .mts / .m2ts ייפתחו תמיד ע"י TsExtractor ולא ייתפסו בטעות ע"י מפענחי שמע (AC3/ADTS/MP3).
 * 2) אם TsExtractor נתקל באי-רציפות PCR ומחזיר SeekMap לא ניתן לדילוג, מחליף אוטומטית למפת דילוג
 *    מיושרת לפאקטים של 188 בתים כדי שפס ההתקדמות והקפיצות יעבדו תמיד ללא תקיעות.
 */
@UnstableApi
public final class SeekableTsExtractor implements Extractor {

    public interface DurationProvider {
        long getFallbackDurationMs();
    }

    private static final int TS_SEARCH_BYTES = 4 * 1024 * 1024;
    private static final int TS_PACKET_BYTES = MpegTsNormalizer.TS_PACKET_SIZE;

    private final Extractor delegate;
    private final DurationProvider durationProvider;
    private long streamLengthBytes = C.LENGTH_UNSET;

    public SeekableTsExtractor(@NonNull Extractor delegate, @NonNull DurationProvider durationProvider) {
        this.delegate = delegate;
        this.durationProvider = durationProvider;
    }

    public static ExtractorsFactory createExtractorsFactory(
            boolean prioritizeTs,
            @NonNull DurationProvider durationProvider
    ) {
        DefaultExtractorsFactory baseFactory = new DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES)
                .setTsExtractorTimestampSearchBytes(TS_SEARCH_BYTES);

        if (!prioritizeTs) {
            return baseFactory;
        }

        return new ExtractorsFactory() {
            @NonNull
            @Override
            public Extractor[] createExtractors() {
                return wrapTsFirst(baseFactory.createExtractors(
                        Uri.parse("file:///normalized_stream.ts"),
                        Collections.emptyMap()
                ), durationProvider);
            }

            @NonNull
            @Override
            public Extractor[] createExtractors(@NonNull Uri uri, @NonNull Map<String, List<String>> responseHeaders) {
                return wrapTsFirst(baseFactory.createExtractors(
                        Uri.parse("file:///normalized_stream.ts"),
                        Collections.emptyMap()
                ), durationProvider);
            }
        };
    }

    private static Extractor[] wrapTsFirst(Extractor[] base, DurationProvider durationProvider) {
        if (base == null || base.length == 0) {
            return new Extractor[0];
        }
        Extractor[] wrapped = new Extractor[base.length];
        wrapped[0] = new SeekableTsExtractor(base[0], durationProvider);
        for (int i = 1; i < base.length; i++) {
            wrapped[i] = base[i];
        }
        return wrapped;
    }

    @Override
    public boolean sniff(@NonNull ExtractorInput input) throws IOException {
        streamLengthBytes = input.getLength();
        if (delegate.sniff(input)) {
            return true;
        }
        // גיבוי אם אחת הפאקטים הראשונים פגומה: בדיקת סנכרון 0x47 בתוך 16KB ראשונים
        input.resetPeekPosition();
        byte[] peek = new byte[16384];
        int peeked = 0;
        while (peeked < peek.length) {
            int read = input.peek(peek, peeked, peek.length - peeked);
            if (read == C.RESULT_END_OF_INPUT || read <= 0) {
                break;
            }
            peeked += read;
        }
        input.resetPeekPosition();
        for (int off = 0; off + TS_PACKET_BYTES * 3 < peeked; off++) {
            if (peek[off] != 0x47) continue;
            int matches = 1;
            for (int k = 1; k <= 4 && off + k * TS_PACKET_BYTES < peeked; k++) {
                if (peek[off + k * TS_PACKET_BYTES] == 0x47) {
                    matches++;
                }
            }
            if (matches >= 4) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void init(@NonNull ExtractorOutput output) {
        delegate.init(new ExtractorOutput() {
            @NonNull
            @Override
            public TrackOutput track(int id, int type) {
                return output.track(id, type);
            }

            @Override
            public void endTracks() {
                output.endTracks();
            }

            @Override
            public void seekMap(@NonNull SeekMap seekMap) {
                long rawDurUs = seekMap.getDurationUs();
                long fallbackMs = durationProvider.getFallbackDurationMs();
                long fallbackDurUs = fallbackMs > 0L ? fallbackMs * 1000L : C.TIME_UNSET;
                boolean delegateLooksValid = seekMap.isSeekable()
                        && rawDurUs > 0L
                        && rawDurUs != C.TIME_UNSET
                        && (fallbackDurUs == C.TIME_UNSET || rawDurUs * 2L >= fallbackDurUs);
                if (delegateLooksValid) {
                    output.seekMap(seekMap);
                    return;
                }
                long effectiveDurUs = (fallbackDurUs != C.TIME_UNSET)
                        ? fallbackDurUs
                        : ((rawDurUs > 0L && rawDurUs != C.TIME_UNSET) ? rawDurUs : C.TIME_UNSET);

                if (streamLengthBytes >= TS_PACKET_BYTES * 2L && effectiveDurUs > 0L && effectiveDurUs != C.TIME_UNSET) {
                    output.seekMap(new PacketAlignedSeekMap(streamLengthBytes, effectiveDurUs));
                } else {
                    output.seekMap(seekMap);
                }
            }
        });
    }

    @Override
    public int read(@NonNull ExtractorInput input, @NonNull PositionHolder seekPosition) throws IOException {
        if (streamLengthBytes == C.LENGTH_UNSET) {
            streamLengthBytes = input.getLength();
        }
        return delegate.read(input, seekPosition);
    }

    @Override
    public void seek(long position, long timeUs) {
        long targetPos = position > 0L
                ? (position / TS_PACKET_BYTES) * TS_PACKET_BYTES
                : position;
        delegate.seek(targetPos, timeUs);
    }

    @Override
    public void release() {
        delegate.release();
    }

    @NonNull
    @Override
    public Extractor getUnderlyingImplementation() {
        return delegate.getUnderlyingImplementation();
    }

    private static final class PacketAlignedSeekMap implements SeekMap {
        private final long totalBytes;
        private final long durationUs;
        private final long usablePackets;

        PacketAlignedSeekMap(long totalBytes, long durationUs) {
            this.totalBytes = totalBytes;
            this.durationUs = durationUs;
            this.usablePackets = Math.max(1L, totalBytes / TS_PACKET_BYTES);
        }

        @Override
        public boolean isSeekable() {
            return true;
        }

        @Override
        public long getDurationUs() {
            return durationUs;
        }

        @NonNull
        @Override
        public SeekPoints getSeekPoints(long timeUs) {
            if (durationUs <= 0L) {
                return new SeekPoints(new SeekPoint(0L, 0L));
            }
            long clampedUs = Math.max(0L, Math.min(durationUs, timeUs));
            long pktIndex = (clampedUs * (usablePackets - 1L)) / durationUs;
            pktIndex = Math.max(0L, Math.min(usablePackets - 1L, pktIndex));
            long bytePos = pktIndex * TS_PACKET_BYTES;
            long alignedUs = (pktIndex * durationUs) / Math.max(1L, usablePackets - 1L);
            return new SeekPoints(new SeekPoint(alignedUs, bytePos));
        }
    }
}
