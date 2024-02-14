package statusbar.finder;

import android.content.Context;
import android.media.MediaMetadata;
import android.util.Pair;
import cn.zhaiyifan.lyric.LyricUtils;
import cn.zhaiyifan.lyric.model.Lyric;
import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.moji4j.MojiConverter;
import com.moji4j.MojiDetector;
import statusbar.finder.livedatas.GetResult;
import statusbar.finder.misc.Constants;
import statusbar.finder.misc.checkStringLang;
import statusbar.finder.provider.ILrcProvider;
import statusbar.finder.provider.KugouProvider;
import statusbar.finder.provider.MusixMatchProvider;
import statusbar.finder.provider.NeteaseProvider;
import statusbar.finder.provider.utils.LyricSearchUtil;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

public class LrcGetter {
    private static final ILrcProvider[] providers = {
            new MusixMatchProvider(),
            new NeteaseProvider(),
            new KugouProvider(),
            // new QQMusicProvider()
    };
    private static MessageDigest messageDigest;

    public static Lyric getLyric(Context context, MediaMetadata mediaMetadata, String sysLang, String packageName) {
        return getLyric(context, new ILrcProvider.MediaInfo(mediaMetadata), sysLang, packageName);
    }

    public static Lyric getLyric(Context context, ILrcProvider.MediaInfo mediaInfo, String sysLang, String packageName) {
        LyricsDatabase lyricsDatabase = new LyricsDatabase(context);
        // Log.d(TAG, "curMediaData" + new SimpleSongInfo(mediaMetadata));
        ILrcProvider.MediaInfo hiraganaMediaInfo;
        if (messageDigest == null) {
            try {
                messageDigest = MessageDigest.getInstance("SHA");
            } catch (NoSuchAlgorithmException e) {
                e.fillInStackTrace();
                lyricsDatabase.close();
                return null;
            }
        }

        ILrcProvider.LyricResult currentResult = lyricsDatabase.searchLyricFromDatabase(mediaInfo, packageName);
        if (currentResult != null) {
            GetResult.getInstance().notifyResult(new Pair<>(mediaInfo, currentResult));
            return LyricUtils.parseLyric(currentResult, mediaInfo);
        }
        currentResult = searchLyricsResultByInfo(mediaInfo);
        if  (currentResult == null) {
            MojiDetector detector = new MojiDetector();
            MojiConverter converter = new MojiConverter();
            if (!detector.hasKana(mediaInfo.getTitle()) && detector.hasLatin(mediaInfo.getTitle())) {
                try {
                    hiraganaMediaInfo = mediaInfo.clone();
                    hiraganaMediaInfo.setTitle(converter.convertRomajiToHiragana(mediaInfo.getTitle()));
                    if (detector.hasLatin(hiraganaMediaInfo.getTitle())) {
                        lyricsDatabase.close();
                        return null;
                    }
                    currentResult = searchLyricsResultByInfo(hiraganaMediaInfo);
                } catch (CloneNotSupportedException e) {
                    e.fillInStackTrace();
                }
                // Log.d(TAG, "newSearchInfo:" + new SimpleSongInfo(mediaMetadata))

                if (currentResult == null) {
                    mediaInfo.setTitle(converter.convertRomajiToKatakana(mediaInfo.getTitle()));
                    // Log.d(TAG, "newSearchInfo:" + new SimpleSongInfo(mediaMetadata));
                    currentResult = searchLyricsResultByInfo(mediaInfo);
                }

            }
            if (currentResult == null) {
                lyricsDatabase.insertLyricIntoDatabase(null, mediaInfo, packageName);
                lyricsDatabase.close();
                return null;
            }
        }
        String allLyrics;
        if (Constants.isTranslateCheck) {
            if (currentResult.mTranslatedLyric != null) {
                allLyrics = LyricUtils.getAllLyrics(false, currentResult.mTranslatedLyric);
            } else {
                allLyrics = LyricUtils.getAllLyrics(false, currentResult.mLyric);
            }
        } else {
            allLyrics = LyricUtils.getAllLyrics(false, currentResult.mLyric);
        }

        if (Objects.equals(sysLang, "zh-CN") && !checkStringLang.isJapanese(allLyrics)) {
            if (currentResult.mTranslatedLyric != null) {
                currentResult.mTranslatedLyric = ZhConverterUtil.toSimple(currentResult.mTranslatedLyric);
            } else {
                currentResult.mLyric = ZhConverterUtil.toSimple(currentResult.mLyric);
            }
        } else if (Objects.equals(sysLang, "zh-TW") && !checkStringLang.isJapanese(allLyrics)) {
            if (currentResult.mTranslatedLyric != null) {
                currentResult.mTranslatedLyric = ZhConverterUtil.toTraditional(currentResult.mTranslatedLyric);
            } else {
                currentResult.mLyric = ZhConverterUtil.toTraditional(currentResult.mLyric);
            }
        }

        if (lyricsDatabase.insertLyricIntoDatabase(currentResult, mediaInfo, packageName)) {
            GetResult.getInstance().notifyResult(new Pair<>(mediaInfo, currentResult));
            lyricsDatabase.close();
            return LyricUtils.parseLyric(currentResult, currentResult.resultInfo);
        }
        lyricsDatabase.close();
        return null;
    }

    private static ILrcProvider.LyricResult searchLyricsResultByInfo(ILrcProvider.MediaInfo mediaInfo) {
        ILrcProvider.LyricResult currentResult = null;
        for (ILrcProvider provider : providers) {
            try {
                ILrcProvider.LyricResult lyricResult = provider.getLyric(mediaInfo);
                if (lyricResult != null) {
                    if (LyricSearchUtil.isLyricContent(lyricResult.mLyric) && (currentResult == null || currentResult.mDistance > lyricResult.mDistance)) {
                        currentResult = lyricResult;
                    }
                }
            } catch (IOException e) {
                e.fillInStackTrace();
            }
        }
        return currentResult;
    }
}
