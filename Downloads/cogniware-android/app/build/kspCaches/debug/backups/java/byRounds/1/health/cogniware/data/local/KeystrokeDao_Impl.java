package health.cogniware.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import health.cogniware.data.models.ContextVector;
import health.cogniware.data.models.KeystrokeSession;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.StringBuilder;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class KeystrokeDao_Impl implements KeystrokeDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<KeystrokeSession> __insertionAdapterOfKeystrokeSession;

  private final Converters __converters = new Converters();

  private final SharedSQLiteStatement __preparedStmtOfPruneOld;

  public KeystrokeDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfKeystrokeSession = new EntityInsertionAdapter<KeystrokeSession>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `keystroke_sessions` (`sessionId`,`patientId`,`startMs`,`endMs`,`ikiMeanMs`,`ikiCvPercent`,`ikiP10Ms`,`ikiP90Ms`,`flightTimeMeanMs`,`flightTimeCvPercent`,`backspaceRatePercent`,`correctionBurstCount`,`wordBoundaryPauseMeanMs`,`withinWordPauseMeanMs`,`longPauseCount`,`estimatedWpm`,`totalKeystrokes`,`context`,`syncedToCloud`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final KeystrokeSession entity) {
        statement.bindString(1, entity.getSessionId());
        statement.bindString(2, entity.getPatientId());
        statement.bindLong(3, entity.getStartMs());
        statement.bindLong(4, entity.getEndMs());
        statement.bindDouble(5, entity.getIkiMeanMs());
        statement.bindDouble(6, entity.getIkiCvPercent());
        statement.bindDouble(7, entity.getIkiP10Ms());
        statement.bindDouble(8, entity.getIkiP90Ms());
        statement.bindDouble(9, entity.getFlightTimeMeanMs());
        statement.bindDouble(10, entity.getFlightTimeCvPercent());
        statement.bindDouble(11, entity.getBackspaceRatePercent());
        statement.bindLong(12, entity.getCorrectionBurstCount());
        statement.bindDouble(13, entity.getWordBoundaryPauseMeanMs());
        statement.bindDouble(14, entity.getWithinWordPauseMeanMs());
        statement.bindLong(15, entity.getLongPauseCount());
        statement.bindDouble(16, entity.getEstimatedWpm());
        statement.bindLong(17, entity.getTotalKeystrokes());
        final String _tmp = __converters.fromContextVector(entity.getContext());
        statement.bindString(18, _tmp);
        final int _tmp_1 = entity.getSyncedToCloud() ? 1 : 0;
        statement.bindLong(19, _tmp_1);
      }
    };
    this.__preparedStmtOfPruneOld = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM keystroke_sessions WHERE syncedToCloud = 1 AND startMs < ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final KeystrokeSession session,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfKeystrokeSession.insert(session);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object pruneOld(final long cutoffMs, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfPruneOld.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, cutoffMs);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfPruneOld.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getUnsynced(final Continuation<? super List<KeystrokeSession>> $completion) {
    final String _sql = "SELECT * FROM keystroke_sessions WHERE syncedToCloud = 0 ORDER BY startMs ASC LIMIT 200";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<KeystrokeSession>>() {
      @Override
      @NonNull
      public List<KeystrokeSession> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfStartMs = CursorUtil.getColumnIndexOrThrow(_cursor, "startMs");
          final int _cursorIndexOfEndMs = CursorUtil.getColumnIndexOrThrow(_cursor, "endMs");
          final int _cursorIndexOfIkiMeanMs = CursorUtil.getColumnIndexOrThrow(_cursor, "ikiMeanMs");
          final int _cursorIndexOfIkiCvPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "ikiCvPercent");
          final int _cursorIndexOfIkiP10Ms = CursorUtil.getColumnIndexOrThrow(_cursor, "ikiP10Ms");
          final int _cursorIndexOfIkiP90Ms = CursorUtil.getColumnIndexOrThrow(_cursor, "ikiP90Ms");
          final int _cursorIndexOfFlightTimeMeanMs = CursorUtil.getColumnIndexOrThrow(_cursor, "flightTimeMeanMs");
          final int _cursorIndexOfFlightTimeCvPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "flightTimeCvPercent");
          final int _cursorIndexOfBackspaceRatePercent = CursorUtil.getColumnIndexOrThrow(_cursor, "backspaceRatePercent");
          final int _cursorIndexOfCorrectionBurstCount = CursorUtil.getColumnIndexOrThrow(_cursor, "correctionBurstCount");
          final int _cursorIndexOfWordBoundaryPauseMeanMs = CursorUtil.getColumnIndexOrThrow(_cursor, "wordBoundaryPauseMeanMs");
          final int _cursorIndexOfWithinWordPauseMeanMs = CursorUtil.getColumnIndexOrThrow(_cursor, "withinWordPauseMeanMs");
          final int _cursorIndexOfLongPauseCount = CursorUtil.getColumnIndexOrThrow(_cursor, "longPauseCount");
          final int _cursorIndexOfEstimatedWpm = CursorUtil.getColumnIndexOrThrow(_cursor, "estimatedWpm");
          final int _cursorIndexOfTotalKeystrokes = CursorUtil.getColumnIndexOrThrow(_cursor, "totalKeystrokes");
          final int _cursorIndexOfContext = CursorUtil.getColumnIndexOrThrow(_cursor, "context");
          final int _cursorIndexOfSyncedToCloud = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedToCloud");
          final List<KeystrokeSession> _result = new ArrayList<KeystrokeSession>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final KeystrokeSession _item;
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final long _tmpStartMs;
            _tmpStartMs = _cursor.getLong(_cursorIndexOfStartMs);
            final long _tmpEndMs;
            _tmpEndMs = _cursor.getLong(_cursorIndexOfEndMs);
            final double _tmpIkiMeanMs;
            _tmpIkiMeanMs = _cursor.getDouble(_cursorIndexOfIkiMeanMs);
            final double _tmpIkiCvPercent;
            _tmpIkiCvPercent = _cursor.getDouble(_cursorIndexOfIkiCvPercent);
            final double _tmpIkiP10Ms;
            _tmpIkiP10Ms = _cursor.getDouble(_cursorIndexOfIkiP10Ms);
            final double _tmpIkiP90Ms;
            _tmpIkiP90Ms = _cursor.getDouble(_cursorIndexOfIkiP90Ms);
            final double _tmpFlightTimeMeanMs;
            _tmpFlightTimeMeanMs = _cursor.getDouble(_cursorIndexOfFlightTimeMeanMs);
            final double _tmpFlightTimeCvPercent;
            _tmpFlightTimeCvPercent = _cursor.getDouble(_cursorIndexOfFlightTimeCvPercent);
            final double _tmpBackspaceRatePercent;
            _tmpBackspaceRatePercent = _cursor.getDouble(_cursorIndexOfBackspaceRatePercent);
            final int _tmpCorrectionBurstCount;
            _tmpCorrectionBurstCount = _cursor.getInt(_cursorIndexOfCorrectionBurstCount);
            final double _tmpWordBoundaryPauseMeanMs;
            _tmpWordBoundaryPauseMeanMs = _cursor.getDouble(_cursorIndexOfWordBoundaryPauseMeanMs);
            final double _tmpWithinWordPauseMeanMs;
            _tmpWithinWordPauseMeanMs = _cursor.getDouble(_cursorIndexOfWithinWordPauseMeanMs);
            final int _tmpLongPauseCount;
            _tmpLongPauseCount = _cursor.getInt(_cursorIndexOfLongPauseCount);
            final double _tmpEstimatedWpm;
            _tmpEstimatedWpm = _cursor.getDouble(_cursorIndexOfEstimatedWpm);
            final int _tmpTotalKeystrokes;
            _tmpTotalKeystrokes = _cursor.getInt(_cursorIndexOfTotalKeystrokes);
            final ContextVector _tmpContext;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfContext);
            _tmpContext = __converters.toContextVector(_tmp);
            final boolean _tmpSyncedToCloud;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfSyncedToCloud);
            _tmpSyncedToCloud = _tmp_1 != 0;
            _item = new KeystrokeSession(_tmpSessionId,_tmpPatientId,_tmpStartMs,_tmpEndMs,_tmpIkiMeanMs,_tmpIkiCvPercent,_tmpIkiP10Ms,_tmpIkiP90Ms,_tmpFlightTimeMeanMs,_tmpFlightTimeCvPercent,_tmpBackspaceRatePercent,_tmpCorrectionBurstCount,_tmpWordBoundaryPauseMeanMs,_tmpWithinWordPauseMeanMs,_tmpLongPauseCount,_tmpEstimatedWpm,_tmpTotalKeystrokes,_tmpContext,_tmpSyncedToCloud);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object count(final String patientId, final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM keystroke_sessions WHERE patientId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, patientId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getRecent(final String patientId,
      final Continuation<? super List<KeystrokeSession>> $completion) {
    final String _sql = "SELECT * FROM keystroke_sessions WHERE patientId = ? ORDER BY startMs DESC LIMIT 8";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, patientId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<KeystrokeSession>>() {
      @Override
      @NonNull
      public List<KeystrokeSession> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfStartMs = CursorUtil.getColumnIndexOrThrow(_cursor, "startMs");
          final int _cursorIndexOfEndMs = CursorUtil.getColumnIndexOrThrow(_cursor, "endMs");
          final int _cursorIndexOfIkiMeanMs = CursorUtil.getColumnIndexOrThrow(_cursor, "ikiMeanMs");
          final int _cursorIndexOfIkiCvPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "ikiCvPercent");
          final int _cursorIndexOfIkiP10Ms = CursorUtil.getColumnIndexOrThrow(_cursor, "ikiP10Ms");
          final int _cursorIndexOfIkiP90Ms = CursorUtil.getColumnIndexOrThrow(_cursor, "ikiP90Ms");
          final int _cursorIndexOfFlightTimeMeanMs = CursorUtil.getColumnIndexOrThrow(_cursor, "flightTimeMeanMs");
          final int _cursorIndexOfFlightTimeCvPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "flightTimeCvPercent");
          final int _cursorIndexOfBackspaceRatePercent = CursorUtil.getColumnIndexOrThrow(_cursor, "backspaceRatePercent");
          final int _cursorIndexOfCorrectionBurstCount = CursorUtil.getColumnIndexOrThrow(_cursor, "correctionBurstCount");
          final int _cursorIndexOfWordBoundaryPauseMeanMs = CursorUtil.getColumnIndexOrThrow(_cursor, "wordBoundaryPauseMeanMs");
          final int _cursorIndexOfWithinWordPauseMeanMs = CursorUtil.getColumnIndexOrThrow(_cursor, "withinWordPauseMeanMs");
          final int _cursorIndexOfLongPauseCount = CursorUtil.getColumnIndexOrThrow(_cursor, "longPauseCount");
          final int _cursorIndexOfEstimatedWpm = CursorUtil.getColumnIndexOrThrow(_cursor, "estimatedWpm");
          final int _cursorIndexOfTotalKeystrokes = CursorUtil.getColumnIndexOrThrow(_cursor, "totalKeystrokes");
          final int _cursorIndexOfContext = CursorUtil.getColumnIndexOrThrow(_cursor, "context");
          final int _cursorIndexOfSyncedToCloud = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedToCloud");
          final List<KeystrokeSession> _result = new ArrayList<KeystrokeSession>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final KeystrokeSession _item;
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final long _tmpStartMs;
            _tmpStartMs = _cursor.getLong(_cursorIndexOfStartMs);
            final long _tmpEndMs;
            _tmpEndMs = _cursor.getLong(_cursorIndexOfEndMs);
            final double _tmpIkiMeanMs;
            _tmpIkiMeanMs = _cursor.getDouble(_cursorIndexOfIkiMeanMs);
            final double _tmpIkiCvPercent;
            _tmpIkiCvPercent = _cursor.getDouble(_cursorIndexOfIkiCvPercent);
            final double _tmpIkiP10Ms;
            _tmpIkiP10Ms = _cursor.getDouble(_cursorIndexOfIkiP10Ms);
            final double _tmpIkiP90Ms;
            _tmpIkiP90Ms = _cursor.getDouble(_cursorIndexOfIkiP90Ms);
            final double _tmpFlightTimeMeanMs;
            _tmpFlightTimeMeanMs = _cursor.getDouble(_cursorIndexOfFlightTimeMeanMs);
            final double _tmpFlightTimeCvPercent;
            _tmpFlightTimeCvPercent = _cursor.getDouble(_cursorIndexOfFlightTimeCvPercent);
            final double _tmpBackspaceRatePercent;
            _tmpBackspaceRatePercent = _cursor.getDouble(_cursorIndexOfBackspaceRatePercent);
            final int _tmpCorrectionBurstCount;
            _tmpCorrectionBurstCount = _cursor.getInt(_cursorIndexOfCorrectionBurstCount);
            final double _tmpWordBoundaryPauseMeanMs;
            _tmpWordBoundaryPauseMeanMs = _cursor.getDouble(_cursorIndexOfWordBoundaryPauseMeanMs);
            final double _tmpWithinWordPauseMeanMs;
            _tmpWithinWordPauseMeanMs = _cursor.getDouble(_cursorIndexOfWithinWordPauseMeanMs);
            final int _tmpLongPauseCount;
            _tmpLongPauseCount = _cursor.getInt(_cursorIndexOfLongPauseCount);
            final double _tmpEstimatedWpm;
            _tmpEstimatedWpm = _cursor.getDouble(_cursorIndexOfEstimatedWpm);
            final int _tmpTotalKeystrokes;
            _tmpTotalKeystrokes = _cursor.getInt(_cursorIndexOfTotalKeystrokes);
            final ContextVector _tmpContext;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfContext);
            _tmpContext = __converters.toContextVector(_tmp);
            final boolean _tmpSyncedToCloud;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfSyncedToCloud);
            _tmpSyncedToCloud = _tmp_1 != 0;
            _item = new KeystrokeSession(_tmpSessionId,_tmpPatientId,_tmpStartMs,_tmpEndMs,_tmpIkiMeanMs,_tmpIkiCvPercent,_tmpIkiP10Ms,_tmpIkiP90Ms,_tmpFlightTimeMeanMs,_tmpFlightTimeCvPercent,_tmpBackspaceRatePercent,_tmpCorrectionBurstCount,_tmpWordBoundaryPauseMeanMs,_tmpWithinWordPauseMeanMs,_tmpLongPauseCount,_tmpEstimatedWpm,_tmpTotalKeystrokes,_tmpContext,_tmpSyncedToCloud);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object markSynced(final List<String> ids, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("UPDATE keystroke_sessions SET syncedToCloud = 1 WHERE sessionId IN (");
        final int _inputSize = ids.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        for (String _item : ids) {
          _stmt.bindString(_argIndex, _item);
          _argIndex++;
        }
        __db.beginTransaction();
        try {
          _stmt.executeUpdateDelete();
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
