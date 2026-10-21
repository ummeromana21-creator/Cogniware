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
import health.cogniware.data.models.Laterality;
import health.cogniware.data.models.TremorBand;
import health.cogniware.data.models.TremorReading;
import java.lang.Class;
import java.lang.Exception;
import java.lang.IllegalArgumentException;
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
public final class TremorDao_Impl implements TremorDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TremorReading> __insertionAdapterOfTremorReading;

  private final Converters __converters = new Converters();

  private final SharedSQLiteStatement __preparedStmtOfPruneOld;

  public TremorDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTremorReading = new EntityInsertionAdapter<TremorReading>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `tremor_readings` (`readingId`,`patientId`,`timestampMs`,`durationMs`,`peakFrequencyHz`,`powerAtPeakDb`,`power4to7Hz`,`power4to12Hz`,`power2to4Hz`,`totalPowerDb`,`rmsAmplitude`,`laterality`,`tremorBandHint`,`context`,`syncedToCloud`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TremorReading entity) {
        statement.bindString(1, entity.getReadingId());
        statement.bindString(2, entity.getPatientId());
        statement.bindLong(3, entity.getTimestampMs());
        statement.bindLong(4, entity.getDurationMs());
        statement.bindDouble(5, entity.getPeakFrequencyHz());
        statement.bindDouble(6, entity.getPowerAtPeakDb());
        statement.bindDouble(7, entity.getPower4to7Hz());
        statement.bindDouble(8, entity.getPower4to12Hz());
        statement.bindDouble(9, entity.getPower2to4Hz());
        statement.bindDouble(10, entity.getTotalPowerDb());
        statement.bindDouble(11, entity.getRmsAmplitude());
        statement.bindString(12, __Laterality_enumToString(entity.getLaterality()));
        statement.bindString(13, __TremorBand_enumToString(entity.getTremorBandHint()));
        final String _tmp = __converters.fromContextVector(entity.getContext());
        statement.bindString(14, _tmp);
        final int _tmp_1 = entity.getSyncedToCloud() ? 1 : 0;
        statement.bindLong(15, _tmp_1);
      }
    };
    this.__preparedStmtOfPruneOld = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM tremor_readings WHERE syncedToCloud = 1 AND timestampMs < ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final TremorReading reading, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfTremorReading.insert(reading);
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
  public Object getUnsynced(final Continuation<? super List<TremorReading>> $completion) {
    final String _sql = "SELECT * FROM tremor_readings WHERE syncedToCloud = 0 ORDER BY timestampMs ASC LIMIT 500";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TremorReading>>() {
      @Override
      @NonNull
      public List<TremorReading> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfReadingId = CursorUtil.getColumnIndexOrThrow(_cursor, "readingId");
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfTimestampMs = CursorUtil.getColumnIndexOrThrow(_cursor, "timestampMs");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "durationMs");
          final int _cursorIndexOfPeakFrequencyHz = CursorUtil.getColumnIndexOrThrow(_cursor, "peakFrequencyHz");
          final int _cursorIndexOfPowerAtPeakDb = CursorUtil.getColumnIndexOrThrow(_cursor, "powerAtPeakDb");
          final int _cursorIndexOfPower4to7Hz = CursorUtil.getColumnIndexOrThrow(_cursor, "power4to7Hz");
          final int _cursorIndexOfPower4to12Hz = CursorUtil.getColumnIndexOrThrow(_cursor, "power4to12Hz");
          final int _cursorIndexOfPower2to4Hz = CursorUtil.getColumnIndexOrThrow(_cursor, "power2to4Hz");
          final int _cursorIndexOfTotalPowerDb = CursorUtil.getColumnIndexOrThrow(_cursor, "totalPowerDb");
          final int _cursorIndexOfRmsAmplitude = CursorUtil.getColumnIndexOrThrow(_cursor, "rmsAmplitude");
          final int _cursorIndexOfLaterality = CursorUtil.getColumnIndexOrThrow(_cursor, "laterality");
          final int _cursorIndexOfTremorBandHint = CursorUtil.getColumnIndexOrThrow(_cursor, "tremorBandHint");
          final int _cursorIndexOfContext = CursorUtil.getColumnIndexOrThrow(_cursor, "context");
          final int _cursorIndexOfSyncedToCloud = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedToCloud");
          final List<TremorReading> _result = new ArrayList<TremorReading>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TremorReading _item;
            final String _tmpReadingId;
            _tmpReadingId = _cursor.getString(_cursorIndexOfReadingId);
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final long _tmpTimestampMs;
            _tmpTimestampMs = _cursor.getLong(_cursorIndexOfTimestampMs);
            final long _tmpDurationMs;
            _tmpDurationMs = _cursor.getLong(_cursorIndexOfDurationMs);
            final double _tmpPeakFrequencyHz;
            _tmpPeakFrequencyHz = _cursor.getDouble(_cursorIndexOfPeakFrequencyHz);
            final double _tmpPowerAtPeakDb;
            _tmpPowerAtPeakDb = _cursor.getDouble(_cursorIndexOfPowerAtPeakDb);
            final double _tmpPower4to7Hz;
            _tmpPower4to7Hz = _cursor.getDouble(_cursorIndexOfPower4to7Hz);
            final double _tmpPower4to12Hz;
            _tmpPower4to12Hz = _cursor.getDouble(_cursorIndexOfPower4to12Hz);
            final double _tmpPower2to4Hz;
            _tmpPower2to4Hz = _cursor.getDouble(_cursorIndexOfPower2to4Hz);
            final double _tmpTotalPowerDb;
            _tmpTotalPowerDb = _cursor.getDouble(_cursorIndexOfTotalPowerDb);
            final double _tmpRmsAmplitude;
            _tmpRmsAmplitude = _cursor.getDouble(_cursorIndexOfRmsAmplitude);
            final Laterality _tmpLaterality;
            _tmpLaterality = __Laterality_stringToEnum(_cursor.getString(_cursorIndexOfLaterality));
            final TremorBand _tmpTremorBandHint;
            _tmpTremorBandHint = __TremorBand_stringToEnum(_cursor.getString(_cursorIndexOfTremorBandHint));
            final ContextVector _tmpContext;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfContext);
            _tmpContext = __converters.toContextVector(_tmp);
            final boolean _tmpSyncedToCloud;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfSyncedToCloud);
            _tmpSyncedToCloud = _tmp_1 != 0;
            _item = new TremorReading(_tmpReadingId,_tmpPatientId,_tmpTimestampMs,_tmpDurationMs,_tmpPeakFrequencyHz,_tmpPowerAtPeakDb,_tmpPower4to7Hz,_tmpPower4to12Hz,_tmpPower2to4Hz,_tmpTotalPowerDb,_tmpRmsAmplitude,_tmpLaterality,_tmpTremorBandHint,_tmpContext,_tmpSyncedToCloud);
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
  public Object getRecent(final String patientId,
      final Continuation<? super List<TremorReading>> $completion) {
    final String _sql = "SELECT * FROM tremor_readings WHERE patientId = ? ORDER BY timestampMs DESC LIMIT 50";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, patientId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TremorReading>>() {
      @Override
      @NonNull
      public List<TremorReading> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfReadingId = CursorUtil.getColumnIndexOrThrow(_cursor, "readingId");
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfTimestampMs = CursorUtil.getColumnIndexOrThrow(_cursor, "timestampMs");
          final int _cursorIndexOfDurationMs = CursorUtil.getColumnIndexOrThrow(_cursor, "durationMs");
          final int _cursorIndexOfPeakFrequencyHz = CursorUtil.getColumnIndexOrThrow(_cursor, "peakFrequencyHz");
          final int _cursorIndexOfPowerAtPeakDb = CursorUtil.getColumnIndexOrThrow(_cursor, "powerAtPeakDb");
          final int _cursorIndexOfPower4to7Hz = CursorUtil.getColumnIndexOrThrow(_cursor, "power4to7Hz");
          final int _cursorIndexOfPower4to12Hz = CursorUtil.getColumnIndexOrThrow(_cursor, "power4to12Hz");
          final int _cursorIndexOfPower2to4Hz = CursorUtil.getColumnIndexOrThrow(_cursor, "power2to4Hz");
          final int _cursorIndexOfTotalPowerDb = CursorUtil.getColumnIndexOrThrow(_cursor, "totalPowerDb");
          final int _cursorIndexOfRmsAmplitude = CursorUtil.getColumnIndexOrThrow(_cursor, "rmsAmplitude");
          final int _cursorIndexOfLaterality = CursorUtil.getColumnIndexOrThrow(_cursor, "laterality");
          final int _cursorIndexOfTremorBandHint = CursorUtil.getColumnIndexOrThrow(_cursor, "tremorBandHint");
          final int _cursorIndexOfContext = CursorUtil.getColumnIndexOrThrow(_cursor, "context");
          final int _cursorIndexOfSyncedToCloud = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedToCloud");
          final List<TremorReading> _result = new ArrayList<TremorReading>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TremorReading _item;
            final String _tmpReadingId;
            _tmpReadingId = _cursor.getString(_cursorIndexOfReadingId);
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final long _tmpTimestampMs;
            _tmpTimestampMs = _cursor.getLong(_cursorIndexOfTimestampMs);
            final long _tmpDurationMs;
            _tmpDurationMs = _cursor.getLong(_cursorIndexOfDurationMs);
            final double _tmpPeakFrequencyHz;
            _tmpPeakFrequencyHz = _cursor.getDouble(_cursorIndexOfPeakFrequencyHz);
            final double _tmpPowerAtPeakDb;
            _tmpPowerAtPeakDb = _cursor.getDouble(_cursorIndexOfPowerAtPeakDb);
            final double _tmpPower4to7Hz;
            _tmpPower4to7Hz = _cursor.getDouble(_cursorIndexOfPower4to7Hz);
            final double _tmpPower4to12Hz;
            _tmpPower4to12Hz = _cursor.getDouble(_cursorIndexOfPower4to12Hz);
            final double _tmpPower2to4Hz;
            _tmpPower2to4Hz = _cursor.getDouble(_cursorIndexOfPower2to4Hz);
            final double _tmpTotalPowerDb;
            _tmpTotalPowerDb = _cursor.getDouble(_cursorIndexOfTotalPowerDb);
            final double _tmpRmsAmplitude;
            _tmpRmsAmplitude = _cursor.getDouble(_cursorIndexOfRmsAmplitude);
            final Laterality _tmpLaterality;
            _tmpLaterality = __Laterality_stringToEnum(_cursor.getString(_cursorIndexOfLaterality));
            final TremorBand _tmpTremorBandHint;
            _tmpTremorBandHint = __TremorBand_stringToEnum(_cursor.getString(_cursorIndexOfTremorBandHint));
            final ContextVector _tmpContext;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfContext);
            _tmpContext = __converters.toContextVector(_tmp);
            final boolean _tmpSyncedToCloud;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfSyncedToCloud);
            _tmpSyncedToCloud = _tmp_1 != 0;
            _item = new TremorReading(_tmpReadingId,_tmpPatientId,_tmpTimestampMs,_tmpDurationMs,_tmpPeakFrequencyHz,_tmpPowerAtPeakDb,_tmpPower4to7Hz,_tmpPower4to12Hz,_tmpPower2to4Hz,_tmpTotalPowerDb,_tmpRmsAmplitude,_tmpLaterality,_tmpTremorBandHint,_tmpContext,_tmpSyncedToCloud);
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
        _stringBuilder.append("UPDATE tremor_readings SET syncedToCloud = 1 WHERE readingId IN (");
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

  private String __Laterality_enumToString(@NonNull final Laterality _value) {
    switch (_value) {
      case LEFT_DOMINANT: return "LEFT_DOMINANT";
      case RIGHT_DOMINANT: return "RIGHT_DOMINANT";
      case BILATERAL: return "BILATERAL";
      case UNKNOWN: return "UNKNOWN";
      default: throw new IllegalArgumentException("Can't convert enum to string, unknown enum value: " + _value);
    }
  }

  private String __TremorBand_enumToString(@NonNull final TremorBand _value) {
    switch (_value) {
      case PARKINSONIAN: return "PARKINSONIAN";
      case ESSENTIAL: return "ESSENTIAL";
      case CEREBELLAR: return "CEREBELLAR";
      case PHYSIOLOGICAL: return "PHYSIOLOGICAL";
      case UNKNOWN: return "UNKNOWN";
      default: throw new IllegalArgumentException("Can't convert enum to string, unknown enum value: " + _value);
    }
  }

  private Laterality __Laterality_stringToEnum(@NonNull final String _value) {
    switch (_value) {
      case "LEFT_DOMINANT": return Laterality.LEFT_DOMINANT;
      case "RIGHT_DOMINANT": return Laterality.RIGHT_DOMINANT;
      case "BILATERAL": return Laterality.BILATERAL;
      case "UNKNOWN": return Laterality.UNKNOWN;
      default: throw new IllegalArgumentException("Can't convert value to enum, unknown value: " + _value);
    }
  }

  private TremorBand __TremorBand_stringToEnum(@NonNull final String _value) {
    switch (_value) {
      case "PARKINSONIAN": return TremorBand.PARKINSONIAN;
      case "ESSENTIAL": return TremorBand.ESSENTIAL;
      case "CEREBELLAR": return TremorBand.CEREBELLAR;
      case "PHYSIOLOGICAL": return TremorBand.PHYSIOLOGICAL;
      case "UNKNOWN": return TremorBand.UNKNOWN;
      default: throw new IllegalArgumentException("Can't convert value to enum, unknown value: " + _value);
    }
  }
}
