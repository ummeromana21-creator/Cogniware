package health.cogniware.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.room.util.StringUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import health.cogniware.data.models.HealthDailySummary;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
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
public final class HealthDao_Impl implements HealthDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<HealthDailySummary> __insertionAdapterOfHealthDailySummary;

  public HealthDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfHealthDailySummary = new EntityInsertionAdapter<HealthDailySummary>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `health_daily` (`dateKey`,`patientId`,`restingHeartRateBpm`,`heartRateMeanBpm`,`heartRateMinBpm`,`heartRateMaxBpm`,`hrvRmssdMs`,`sleepTotalMinutes`,`sleepDeepMinutes`,`sleepRemMinutes`,`sleepLightMinutes`,`sleepAwakeMinutes`,`sleepOnsetMs`,`wakeTimeMs`,`stepCount`,`activeCaloriesBurned`,`totalCaloriesBurned`,`activeMinutes`,`exerciseSessionMinutes`,`spO2MeanPercent`,`spO2MinPercent`,`syncedToCloud`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final HealthDailySummary entity) {
        statement.bindString(1, entity.getDateKey());
        statement.bindString(2, entity.getPatientId());
        if (entity.getRestingHeartRateBpm() == null) {
          statement.bindNull(3);
        } else {
          statement.bindDouble(3, entity.getRestingHeartRateBpm());
        }
        if (entity.getHeartRateMeanBpm() == null) {
          statement.bindNull(4);
        } else {
          statement.bindDouble(4, entity.getHeartRateMeanBpm());
        }
        if (entity.getHeartRateMinBpm() == null) {
          statement.bindNull(5);
        } else {
          statement.bindDouble(5, entity.getHeartRateMinBpm());
        }
        if (entity.getHeartRateMaxBpm() == null) {
          statement.bindNull(6);
        } else {
          statement.bindDouble(6, entity.getHeartRateMaxBpm());
        }
        if (entity.getHrvRmssdMs() == null) {
          statement.bindNull(7);
        } else {
          statement.bindDouble(7, entity.getHrvRmssdMs());
        }
        if (entity.getSleepTotalMinutes() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getSleepTotalMinutes());
        }
        if (entity.getSleepDeepMinutes() == null) {
          statement.bindNull(9);
        } else {
          statement.bindLong(9, entity.getSleepDeepMinutes());
        }
        if (entity.getSleepRemMinutes() == null) {
          statement.bindNull(10);
        } else {
          statement.bindLong(10, entity.getSleepRemMinutes());
        }
        if (entity.getSleepLightMinutes() == null) {
          statement.bindNull(11);
        } else {
          statement.bindLong(11, entity.getSleepLightMinutes());
        }
        if (entity.getSleepAwakeMinutes() == null) {
          statement.bindNull(12);
        } else {
          statement.bindLong(12, entity.getSleepAwakeMinutes());
        }
        if (entity.getSleepOnsetMs() == null) {
          statement.bindNull(13);
        } else {
          statement.bindLong(13, entity.getSleepOnsetMs());
        }
        if (entity.getWakeTimeMs() == null) {
          statement.bindNull(14);
        } else {
          statement.bindLong(14, entity.getWakeTimeMs());
        }
        if (entity.getStepCount() == null) {
          statement.bindNull(15);
        } else {
          statement.bindLong(15, entity.getStepCount());
        }
        if (entity.getActiveCaloriesBurned() == null) {
          statement.bindNull(16);
        } else {
          statement.bindDouble(16, entity.getActiveCaloriesBurned());
        }
        if (entity.getTotalCaloriesBurned() == null) {
          statement.bindNull(17);
        } else {
          statement.bindDouble(17, entity.getTotalCaloriesBurned());
        }
        if (entity.getActiveMinutes() == null) {
          statement.bindNull(18);
        } else {
          statement.bindLong(18, entity.getActiveMinutes());
        }
        if (entity.getExerciseSessionMinutes() == null) {
          statement.bindNull(19);
        } else {
          statement.bindLong(19, entity.getExerciseSessionMinutes());
        }
        if (entity.getSpO2MeanPercent() == null) {
          statement.bindNull(20);
        } else {
          statement.bindDouble(20, entity.getSpO2MeanPercent());
        }
        if (entity.getSpO2MinPercent() == null) {
          statement.bindNull(21);
        } else {
          statement.bindDouble(21, entity.getSpO2MinPercent());
        }
        final int _tmp = entity.getSyncedToCloud() ? 1 : 0;
        statement.bindLong(22, _tmp);
      }
    };
  }

  @Override
  public Object insert(final HealthDailySummary summary,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfHealthDailySummary.insert(summary);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object getUnsynced(final Continuation<? super List<HealthDailySummary>> $completion) {
    final String _sql = "SELECT * FROM health_daily WHERE syncedToCloud = 0 ORDER BY dateKey ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<HealthDailySummary>>() {
      @Override
      @NonNull
      public List<HealthDailySummary> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDateKey = CursorUtil.getColumnIndexOrThrow(_cursor, "dateKey");
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfRestingHeartRateBpm = CursorUtil.getColumnIndexOrThrow(_cursor, "restingHeartRateBpm");
          final int _cursorIndexOfHeartRateMeanBpm = CursorUtil.getColumnIndexOrThrow(_cursor, "heartRateMeanBpm");
          final int _cursorIndexOfHeartRateMinBpm = CursorUtil.getColumnIndexOrThrow(_cursor, "heartRateMinBpm");
          final int _cursorIndexOfHeartRateMaxBpm = CursorUtil.getColumnIndexOrThrow(_cursor, "heartRateMaxBpm");
          final int _cursorIndexOfHrvRmssdMs = CursorUtil.getColumnIndexOrThrow(_cursor, "hrvRmssdMs");
          final int _cursorIndexOfSleepTotalMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "sleepTotalMinutes");
          final int _cursorIndexOfSleepDeepMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "sleepDeepMinutes");
          final int _cursorIndexOfSleepRemMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "sleepRemMinutes");
          final int _cursorIndexOfSleepLightMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "sleepLightMinutes");
          final int _cursorIndexOfSleepAwakeMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "sleepAwakeMinutes");
          final int _cursorIndexOfSleepOnsetMs = CursorUtil.getColumnIndexOrThrow(_cursor, "sleepOnsetMs");
          final int _cursorIndexOfWakeTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "wakeTimeMs");
          final int _cursorIndexOfStepCount = CursorUtil.getColumnIndexOrThrow(_cursor, "stepCount");
          final int _cursorIndexOfActiveCaloriesBurned = CursorUtil.getColumnIndexOrThrow(_cursor, "activeCaloriesBurned");
          final int _cursorIndexOfTotalCaloriesBurned = CursorUtil.getColumnIndexOrThrow(_cursor, "totalCaloriesBurned");
          final int _cursorIndexOfActiveMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "activeMinutes");
          final int _cursorIndexOfExerciseSessionMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "exerciseSessionMinutes");
          final int _cursorIndexOfSpO2MeanPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "spO2MeanPercent");
          final int _cursorIndexOfSpO2MinPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "spO2MinPercent");
          final int _cursorIndexOfSyncedToCloud = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedToCloud");
          final List<HealthDailySummary> _result = new ArrayList<HealthDailySummary>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final HealthDailySummary _item;
            final String _tmpDateKey;
            _tmpDateKey = _cursor.getString(_cursorIndexOfDateKey);
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final Double _tmpRestingHeartRateBpm;
            if (_cursor.isNull(_cursorIndexOfRestingHeartRateBpm)) {
              _tmpRestingHeartRateBpm = null;
            } else {
              _tmpRestingHeartRateBpm = _cursor.getDouble(_cursorIndexOfRestingHeartRateBpm);
            }
            final Double _tmpHeartRateMeanBpm;
            if (_cursor.isNull(_cursorIndexOfHeartRateMeanBpm)) {
              _tmpHeartRateMeanBpm = null;
            } else {
              _tmpHeartRateMeanBpm = _cursor.getDouble(_cursorIndexOfHeartRateMeanBpm);
            }
            final Double _tmpHeartRateMinBpm;
            if (_cursor.isNull(_cursorIndexOfHeartRateMinBpm)) {
              _tmpHeartRateMinBpm = null;
            } else {
              _tmpHeartRateMinBpm = _cursor.getDouble(_cursorIndexOfHeartRateMinBpm);
            }
            final Double _tmpHeartRateMaxBpm;
            if (_cursor.isNull(_cursorIndexOfHeartRateMaxBpm)) {
              _tmpHeartRateMaxBpm = null;
            } else {
              _tmpHeartRateMaxBpm = _cursor.getDouble(_cursorIndexOfHeartRateMaxBpm);
            }
            final Double _tmpHrvRmssdMs;
            if (_cursor.isNull(_cursorIndexOfHrvRmssdMs)) {
              _tmpHrvRmssdMs = null;
            } else {
              _tmpHrvRmssdMs = _cursor.getDouble(_cursorIndexOfHrvRmssdMs);
            }
            final Integer _tmpSleepTotalMinutes;
            if (_cursor.isNull(_cursorIndexOfSleepTotalMinutes)) {
              _tmpSleepTotalMinutes = null;
            } else {
              _tmpSleepTotalMinutes = _cursor.getInt(_cursorIndexOfSleepTotalMinutes);
            }
            final Integer _tmpSleepDeepMinutes;
            if (_cursor.isNull(_cursorIndexOfSleepDeepMinutes)) {
              _tmpSleepDeepMinutes = null;
            } else {
              _tmpSleepDeepMinutes = _cursor.getInt(_cursorIndexOfSleepDeepMinutes);
            }
            final Integer _tmpSleepRemMinutes;
            if (_cursor.isNull(_cursorIndexOfSleepRemMinutes)) {
              _tmpSleepRemMinutes = null;
            } else {
              _tmpSleepRemMinutes = _cursor.getInt(_cursorIndexOfSleepRemMinutes);
            }
            final Integer _tmpSleepLightMinutes;
            if (_cursor.isNull(_cursorIndexOfSleepLightMinutes)) {
              _tmpSleepLightMinutes = null;
            } else {
              _tmpSleepLightMinutes = _cursor.getInt(_cursorIndexOfSleepLightMinutes);
            }
            final Integer _tmpSleepAwakeMinutes;
            if (_cursor.isNull(_cursorIndexOfSleepAwakeMinutes)) {
              _tmpSleepAwakeMinutes = null;
            } else {
              _tmpSleepAwakeMinutes = _cursor.getInt(_cursorIndexOfSleepAwakeMinutes);
            }
            final Long _tmpSleepOnsetMs;
            if (_cursor.isNull(_cursorIndexOfSleepOnsetMs)) {
              _tmpSleepOnsetMs = null;
            } else {
              _tmpSleepOnsetMs = _cursor.getLong(_cursorIndexOfSleepOnsetMs);
            }
            final Long _tmpWakeTimeMs;
            if (_cursor.isNull(_cursorIndexOfWakeTimeMs)) {
              _tmpWakeTimeMs = null;
            } else {
              _tmpWakeTimeMs = _cursor.getLong(_cursorIndexOfWakeTimeMs);
            }
            final Integer _tmpStepCount;
            if (_cursor.isNull(_cursorIndexOfStepCount)) {
              _tmpStepCount = null;
            } else {
              _tmpStepCount = _cursor.getInt(_cursorIndexOfStepCount);
            }
            final Double _tmpActiveCaloriesBurned;
            if (_cursor.isNull(_cursorIndexOfActiveCaloriesBurned)) {
              _tmpActiveCaloriesBurned = null;
            } else {
              _tmpActiveCaloriesBurned = _cursor.getDouble(_cursorIndexOfActiveCaloriesBurned);
            }
            final Double _tmpTotalCaloriesBurned;
            if (_cursor.isNull(_cursorIndexOfTotalCaloriesBurned)) {
              _tmpTotalCaloriesBurned = null;
            } else {
              _tmpTotalCaloriesBurned = _cursor.getDouble(_cursorIndexOfTotalCaloriesBurned);
            }
            final Integer _tmpActiveMinutes;
            if (_cursor.isNull(_cursorIndexOfActiveMinutes)) {
              _tmpActiveMinutes = null;
            } else {
              _tmpActiveMinutes = _cursor.getInt(_cursorIndexOfActiveMinutes);
            }
            final Integer _tmpExerciseSessionMinutes;
            if (_cursor.isNull(_cursorIndexOfExerciseSessionMinutes)) {
              _tmpExerciseSessionMinutes = null;
            } else {
              _tmpExerciseSessionMinutes = _cursor.getInt(_cursorIndexOfExerciseSessionMinutes);
            }
            final Double _tmpSpO2MeanPercent;
            if (_cursor.isNull(_cursorIndexOfSpO2MeanPercent)) {
              _tmpSpO2MeanPercent = null;
            } else {
              _tmpSpO2MeanPercent = _cursor.getDouble(_cursorIndexOfSpO2MeanPercent);
            }
            final Double _tmpSpO2MinPercent;
            if (_cursor.isNull(_cursorIndexOfSpO2MinPercent)) {
              _tmpSpO2MinPercent = null;
            } else {
              _tmpSpO2MinPercent = _cursor.getDouble(_cursorIndexOfSpO2MinPercent);
            }
            final boolean _tmpSyncedToCloud;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSyncedToCloud);
            _tmpSyncedToCloud = _tmp != 0;
            _item = new HealthDailySummary(_tmpDateKey,_tmpPatientId,_tmpRestingHeartRateBpm,_tmpHeartRateMeanBpm,_tmpHeartRateMinBpm,_tmpHeartRateMaxBpm,_tmpHrvRmssdMs,_tmpSleepTotalMinutes,_tmpSleepDeepMinutes,_tmpSleepRemMinutes,_tmpSleepLightMinutes,_tmpSleepAwakeMinutes,_tmpSleepOnsetMs,_tmpWakeTimeMs,_tmpStepCount,_tmpActiveCaloriesBurned,_tmpTotalCaloriesBurned,_tmpActiveMinutes,_tmpExerciseSessionMinutes,_tmpSpO2MeanPercent,_tmpSpO2MinPercent,_tmpSyncedToCloud);
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
  public Object getLatest(final String patientId,
      final Continuation<? super HealthDailySummary> $completion) {
    final String _sql = "SELECT * FROM health_daily WHERE patientId = ? ORDER BY dateKey DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, patientId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<HealthDailySummary>() {
      @Override
      @Nullable
      public HealthDailySummary call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDateKey = CursorUtil.getColumnIndexOrThrow(_cursor, "dateKey");
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfRestingHeartRateBpm = CursorUtil.getColumnIndexOrThrow(_cursor, "restingHeartRateBpm");
          final int _cursorIndexOfHeartRateMeanBpm = CursorUtil.getColumnIndexOrThrow(_cursor, "heartRateMeanBpm");
          final int _cursorIndexOfHeartRateMinBpm = CursorUtil.getColumnIndexOrThrow(_cursor, "heartRateMinBpm");
          final int _cursorIndexOfHeartRateMaxBpm = CursorUtil.getColumnIndexOrThrow(_cursor, "heartRateMaxBpm");
          final int _cursorIndexOfHrvRmssdMs = CursorUtil.getColumnIndexOrThrow(_cursor, "hrvRmssdMs");
          final int _cursorIndexOfSleepTotalMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "sleepTotalMinutes");
          final int _cursorIndexOfSleepDeepMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "sleepDeepMinutes");
          final int _cursorIndexOfSleepRemMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "sleepRemMinutes");
          final int _cursorIndexOfSleepLightMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "sleepLightMinutes");
          final int _cursorIndexOfSleepAwakeMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "sleepAwakeMinutes");
          final int _cursorIndexOfSleepOnsetMs = CursorUtil.getColumnIndexOrThrow(_cursor, "sleepOnsetMs");
          final int _cursorIndexOfWakeTimeMs = CursorUtil.getColumnIndexOrThrow(_cursor, "wakeTimeMs");
          final int _cursorIndexOfStepCount = CursorUtil.getColumnIndexOrThrow(_cursor, "stepCount");
          final int _cursorIndexOfActiveCaloriesBurned = CursorUtil.getColumnIndexOrThrow(_cursor, "activeCaloriesBurned");
          final int _cursorIndexOfTotalCaloriesBurned = CursorUtil.getColumnIndexOrThrow(_cursor, "totalCaloriesBurned");
          final int _cursorIndexOfActiveMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "activeMinutes");
          final int _cursorIndexOfExerciseSessionMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "exerciseSessionMinutes");
          final int _cursorIndexOfSpO2MeanPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "spO2MeanPercent");
          final int _cursorIndexOfSpO2MinPercent = CursorUtil.getColumnIndexOrThrow(_cursor, "spO2MinPercent");
          final int _cursorIndexOfSyncedToCloud = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedToCloud");
          final HealthDailySummary _result;
          if (_cursor.moveToFirst()) {
            final String _tmpDateKey;
            _tmpDateKey = _cursor.getString(_cursorIndexOfDateKey);
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final Double _tmpRestingHeartRateBpm;
            if (_cursor.isNull(_cursorIndexOfRestingHeartRateBpm)) {
              _tmpRestingHeartRateBpm = null;
            } else {
              _tmpRestingHeartRateBpm = _cursor.getDouble(_cursorIndexOfRestingHeartRateBpm);
            }
            final Double _tmpHeartRateMeanBpm;
            if (_cursor.isNull(_cursorIndexOfHeartRateMeanBpm)) {
              _tmpHeartRateMeanBpm = null;
            } else {
              _tmpHeartRateMeanBpm = _cursor.getDouble(_cursorIndexOfHeartRateMeanBpm);
            }
            final Double _tmpHeartRateMinBpm;
            if (_cursor.isNull(_cursorIndexOfHeartRateMinBpm)) {
              _tmpHeartRateMinBpm = null;
            } else {
              _tmpHeartRateMinBpm = _cursor.getDouble(_cursorIndexOfHeartRateMinBpm);
            }
            final Double _tmpHeartRateMaxBpm;
            if (_cursor.isNull(_cursorIndexOfHeartRateMaxBpm)) {
              _tmpHeartRateMaxBpm = null;
            } else {
              _tmpHeartRateMaxBpm = _cursor.getDouble(_cursorIndexOfHeartRateMaxBpm);
            }
            final Double _tmpHrvRmssdMs;
            if (_cursor.isNull(_cursorIndexOfHrvRmssdMs)) {
              _tmpHrvRmssdMs = null;
            } else {
              _tmpHrvRmssdMs = _cursor.getDouble(_cursorIndexOfHrvRmssdMs);
            }
            final Integer _tmpSleepTotalMinutes;
            if (_cursor.isNull(_cursorIndexOfSleepTotalMinutes)) {
              _tmpSleepTotalMinutes = null;
            } else {
              _tmpSleepTotalMinutes = _cursor.getInt(_cursorIndexOfSleepTotalMinutes);
            }
            final Integer _tmpSleepDeepMinutes;
            if (_cursor.isNull(_cursorIndexOfSleepDeepMinutes)) {
              _tmpSleepDeepMinutes = null;
            } else {
              _tmpSleepDeepMinutes = _cursor.getInt(_cursorIndexOfSleepDeepMinutes);
            }
            final Integer _tmpSleepRemMinutes;
            if (_cursor.isNull(_cursorIndexOfSleepRemMinutes)) {
              _tmpSleepRemMinutes = null;
            } else {
              _tmpSleepRemMinutes = _cursor.getInt(_cursorIndexOfSleepRemMinutes);
            }
            final Integer _tmpSleepLightMinutes;
            if (_cursor.isNull(_cursorIndexOfSleepLightMinutes)) {
              _tmpSleepLightMinutes = null;
            } else {
              _tmpSleepLightMinutes = _cursor.getInt(_cursorIndexOfSleepLightMinutes);
            }
            final Integer _tmpSleepAwakeMinutes;
            if (_cursor.isNull(_cursorIndexOfSleepAwakeMinutes)) {
              _tmpSleepAwakeMinutes = null;
            } else {
              _tmpSleepAwakeMinutes = _cursor.getInt(_cursorIndexOfSleepAwakeMinutes);
            }
            final Long _tmpSleepOnsetMs;
            if (_cursor.isNull(_cursorIndexOfSleepOnsetMs)) {
              _tmpSleepOnsetMs = null;
            } else {
              _tmpSleepOnsetMs = _cursor.getLong(_cursorIndexOfSleepOnsetMs);
            }
            final Long _tmpWakeTimeMs;
            if (_cursor.isNull(_cursorIndexOfWakeTimeMs)) {
              _tmpWakeTimeMs = null;
            } else {
              _tmpWakeTimeMs = _cursor.getLong(_cursorIndexOfWakeTimeMs);
            }
            final Integer _tmpStepCount;
            if (_cursor.isNull(_cursorIndexOfStepCount)) {
              _tmpStepCount = null;
            } else {
              _tmpStepCount = _cursor.getInt(_cursorIndexOfStepCount);
            }
            final Double _tmpActiveCaloriesBurned;
            if (_cursor.isNull(_cursorIndexOfActiveCaloriesBurned)) {
              _tmpActiveCaloriesBurned = null;
            } else {
              _tmpActiveCaloriesBurned = _cursor.getDouble(_cursorIndexOfActiveCaloriesBurned);
            }
            final Double _tmpTotalCaloriesBurned;
            if (_cursor.isNull(_cursorIndexOfTotalCaloriesBurned)) {
              _tmpTotalCaloriesBurned = null;
            } else {
              _tmpTotalCaloriesBurned = _cursor.getDouble(_cursorIndexOfTotalCaloriesBurned);
            }
            final Integer _tmpActiveMinutes;
            if (_cursor.isNull(_cursorIndexOfActiveMinutes)) {
              _tmpActiveMinutes = null;
            } else {
              _tmpActiveMinutes = _cursor.getInt(_cursorIndexOfActiveMinutes);
            }
            final Integer _tmpExerciseSessionMinutes;
            if (_cursor.isNull(_cursorIndexOfExerciseSessionMinutes)) {
              _tmpExerciseSessionMinutes = null;
            } else {
              _tmpExerciseSessionMinutes = _cursor.getInt(_cursorIndexOfExerciseSessionMinutes);
            }
            final Double _tmpSpO2MeanPercent;
            if (_cursor.isNull(_cursorIndexOfSpO2MeanPercent)) {
              _tmpSpO2MeanPercent = null;
            } else {
              _tmpSpO2MeanPercent = _cursor.getDouble(_cursorIndexOfSpO2MeanPercent);
            }
            final Double _tmpSpO2MinPercent;
            if (_cursor.isNull(_cursorIndexOfSpO2MinPercent)) {
              _tmpSpO2MinPercent = null;
            } else {
              _tmpSpO2MinPercent = _cursor.getDouble(_cursorIndexOfSpO2MinPercent);
            }
            final boolean _tmpSyncedToCloud;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSyncedToCloud);
            _tmpSyncedToCloud = _tmp != 0;
            _result = new HealthDailySummary(_tmpDateKey,_tmpPatientId,_tmpRestingHeartRateBpm,_tmpHeartRateMeanBpm,_tmpHeartRateMinBpm,_tmpHeartRateMaxBpm,_tmpHrvRmssdMs,_tmpSleepTotalMinutes,_tmpSleepDeepMinutes,_tmpSleepRemMinutes,_tmpSleepLightMinutes,_tmpSleepAwakeMinutes,_tmpSleepOnsetMs,_tmpWakeTimeMs,_tmpStepCount,_tmpActiveCaloriesBurned,_tmpTotalCaloriesBurned,_tmpActiveMinutes,_tmpExerciseSessionMinutes,_tmpSpO2MeanPercent,_tmpSpO2MinPercent,_tmpSyncedToCloud);
          } else {
            _result = null;
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
  public Object markSynced(final List<String> dateKeys,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final StringBuilder _stringBuilder = StringUtil.newStringBuilder();
        _stringBuilder.append("UPDATE health_daily SET syncedToCloud = 1 WHERE dateKey IN (");
        final int _inputSize = dateKeys.size();
        StringUtil.appendPlaceholders(_stringBuilder, _inputSize);
        _stringBuilder.append(")");
        final String _sql = _stringBuilder.toString();
        final SupportSQLiteStatement _stmt = __db.compileStatement(_sql);
        int _argIndex = 1;
        for (String _item : dateKeys) {
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
