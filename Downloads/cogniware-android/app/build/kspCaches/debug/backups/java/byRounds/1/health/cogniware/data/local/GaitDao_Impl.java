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
import health.cogniware.data.models.GaitSession;
import java.lang.Class;
import java.lang.Exception;
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
public final class GaitDao_Impl implements GaitDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<GaitSession> __insertionAdapterOfGaitSession;

  private final Converters __converters = new Converters();

  private final SharedSQLiteStatement __preparedStmtOfPruneOld;

  public GaitDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfGaitSession = new EntityInsertionAdapter<GaitSession>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `gait_sessions` (`sessionId`,`patientId`,`startMs`,`endMs`,`stepCount`,`cadenceStepsPerMin`,`strideRegularityIndex`,`stepAsymmetryIndex`,`walkingSpeedProxy`,`verticalImpactG`,`context`,`syncedToCloud`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final GaitSession entity) {
        statement.bindString(1, entity.getSessionId());
        statement.bindString(2, entity.getPatientId());
        statement.bindLong(3, entity.getStartMs());
        statement.bindLong(4, entity.getEndMs());
        statement.bindLong(5, entity.getStepCount());
        statement.bindDouble(6, entity.getCadenceStepsPerMin());
        statement.bindDouble(7, entity.getStrideRegularityIndex());
        statement.bindDouble(8, entity.getStepAsymmetryIndex());
        statement.bindDouble(9, entity.getWalkingSpeedProxy());
        statement.bindDouble(10, entity.getVerticalImpactG());
        final String _tmp = __converters.fromContextVector(entity.getContext());
        statement.bindString(11, _tmp);
        final int _tmp_1 = entity.getSyncedToCloud() ? 1 : 0;
        statement.bindLong(12, _tmp_1);
      }
    };
    this.__preparedStmtOfPruneOld = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM gait_sessions WHERE syncedToCloud = 1 AND startMs < ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final GaitSession session, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfGaitSession.insert(session);
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
  public Object getUnsynced(final Continuation<? super List<GaitSession>> $completion) {
    final String _sql = "SELECT * FROM gait_sessions WHERE syncedToCloud = 0 ORDER BY startMs ASC LIMIT 200";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<GaitSession>>() {
      @Override
      @NonNull
      public List<GaitSession> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfStartMs = CursorUtil.getColumnIndexOrThrow(_cursor, "startMs");
          final int _cursorIndexOfEndMs = CursorUtil.getColumnIndexOrThrow(_cursor, "endMs");
          final int _cursorIndexOfStepCount = CursorUtil.getColumnIndexOrThrow(_cursor, "stepCount");
          final int _cursorIndexOfCadenceStepsPerMin = CursorUtil.getColumnIndexOrThrow(_cursor, "cadenceStepsPerMin");
          final int _cursorIndexOfStrideRegularityIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "strideRegularityIndex");
          final int _cursorIndexOfStepAsymmetryIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "stepAsymmetryIndex");
          final int _cursorIndexOfWalkingSpeedProxy = CursorUtil.getColumnIndexOrThrow(_cursor, "walkingSpeedProxy");
          final int _cursorIndexOfVerticalImpactG = CursorUtil.getColumnIndexOrThrow(_cursor, "verticalImpactG");
          final int _cursorIndexOfContext = CursorUtil.getColumnIndexOrThrow(_cursor, "context");
          final int _cursorIndexOfSyncedToCloud = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedToCloud");
          final List<GaitSession> _result = new ArrayList<GaitSession>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final GaitSession _item;
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final long _tmpStartMs;
            _tmpStartMs = _cursor.getLong(_cursorIndexOfStartMs);
            final long _tmpEndMs;
            _tmpEndMs = _cursor.getLong(_cursorIndexOfEndMs);
            final int _tmpStepCount;
            _tmpStepCount = _cursor.getInt(_cursorIndexOfStepCount);
            final double _tmpCadenceStepsPerMin;
            _tmpCadenceStepsPerMin = _cursor.getDouble(_cursorIndexOfCadenceStepsPerMin);
            final double _tmpStrideRegularityIndex;
            _tmpStrideRegularityIndex = _cursor.getDouble(_cursorIndexOfStrideRegularityIndex);
            final double _tmpStepAsymmetryIndex;
            _tmpStepAsymmetryIndex = _cursor.getDouble(_cursorIndexOfStepAsymmetryIndex);
            final double _tmpWalkingSpeedProxy;
            _tmpWalkingSpeedProxy = _cursor.getDouble(_cursorIndexOfWalkingSpeedProxy);
            final double _tmpVerticalImpactG;
            _tmpVerticalImpactG = _cursor.getDouble(_cursorIndexOfVerticalImpactG);
            final ContextVector _tmpContext;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfContext);
            _tmpContext = __converters.toContextVector(_tmp);
            final boolean _tmpSyncedToCloud;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfSyncedToCloud);
            _tmpSyncedToCloud = _tmp_1 != 0;
            _item = new GaitSession(_tmpSessionId,_tmpPatientId,_tmpStartMs,_tmpEndMs,_tmpStepCount,_tmpCadenceStepsPerMin,_tmpStrideRegularityIndex,_tmpStepAsymmetryIndex,_tmpWalkingSpeedProxy,_tmpVerticalImpactG,_tmpContext,_tmpSyncedToCloud);
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
      final Continuation<? super List<GaitSession>> $completion) {
    final String _sql = "SELECT * FROM gait_sessions WHERE patientId = ? ORDER BY startMs DESC LIMIT 10";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, patientId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<GaitSession>>() {
      @Override
      @NonNull
      public List<GaitSession> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfStartMs = CursorUtil.getColumnIndexOrThrow(_cursor, "startMs");
          final int _cursorIndexOfEndMs = CursorUtil.getColumnIndexOrThrow(_cursor, "endMs");
          final int _cursorIndexOfStepCount = CursorUtil.getColumnIndexOrThrow(_cursor, "stepCount");
          final int _cursorIndexOfCadenceStepsPerMin = CursorUtil.getColumnIndexOrThrow(_cursor, "cadenceStepsPerMin");
          final int _cursorIndexOfStrideRegularityIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "strideRegularityIndex");
          final int _cursorIndexOfStepAsymmetryIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "stepAsymmetryIndex");
          final int _cursorIndexOfWalkingSpeedProxy = CursorUtil.getColumnIndexOrThrow(_cursor, "walkingSpeedProxy");
          final int _cursorIndexOfVerticalImpactG = CursorUtil.getColumnIndexOrThrow(_cursor, "verticalImpactG");
          final int _cursorIndexOfContext = CursorUtil.getColumnIndexOrThrow(_cursor, "context");
          final int _cursorIndexOfSyncedToCloud = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedToCloud");
          final List<GaitSession> _result = new ArrayList<GaitSession>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final GaitSession _item;
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final long _tmpStartMs;
            _tmpStartMs = _cursor.getLong(_cursorIndexOfStartMs);
            final long _tmpEndMs;
            _tmpEndMs = _cursor.getLong(_cursorIndexOfEndMs);
            final int _tmpStepCount;
            _tmpStepCount = _cursor.getInt(_cursorIndexOfStepCount);
            final double _tmpCadenceStepsPerMin;
            _tmpCadenceStepsPerMin = _cursor.getDouble(_cursorIndexOfCadenceStepsPerMin);
            final double _tmpStrideRegularityIndex;
            _tmpStrideRegularityIndex = _cursor.getDouble(_cursorIndexOfStrideRegularityIndex);
            final double _tmpStepAsymmetryIndex;
            _tmpStepAsymmetryIndex = _cursor.getDouble(_cursorIndexOfStepAsymmetryIndex);
            final double _tmpWalkingSpeedProxy;
            _tmpWalkingSpeedProxy = _cursor.getDouble(_cursorIndexOfWalkingSpeedProxy);
            final double _tmpVerticalImpactG;
            _tmpVerticalImpactG = _cursor.getDouble(_cursorIndexOfVerticalImpactG);
            final ContextVector _tmpContext;
            final String _tmp;
            _tmp = _cursor.getString(_cursorIndexOfContext);
            _tmpContext = __converters.toContextVector(_tmp);
            final boolean _tmpSyncedToCloud;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfSyncedToCloud);
            _tmpSyncedToCloud = _tmp_1 != 0;
            _item = new GaitSession(_tmpSessionId,_tmpPatientId,_tmpStartMs,_tmpEndMs,_tmpStepCount,_tmpCadenceStepsPerMin,_tmpStrideRegularityIndex,_tmpStepAsymmetryIndex,_tmpWalkingSpeedProxy,_tmpVerticalImpactG,_tmpContext,_tmpSyncedToCloud);
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
        _stringBuilder.append("UPDATE gait_sessions SET syncedToCloud = 1 WHERE sessionId IN (");
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
