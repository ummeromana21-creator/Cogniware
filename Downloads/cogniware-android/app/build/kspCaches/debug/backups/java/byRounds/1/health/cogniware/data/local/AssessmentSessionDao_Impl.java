package health.cogniware.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import health.cogniware.data.models.AssessmentSession;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
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
public final class AssessmentSessionDao_Impl implements AssessmentSessionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<AssessmentSession> __insertionAdapterOfAssessmentSession;

  private final SharedSQLiteStatement __preparedStmtOfMarkComplete;

  public AssessmentSessionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfAssessmentSession = new EntityInsertionAdapter<AssessmentSession>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `assessment_sessions` (`sessionId`,`patientId`,`scheduledMs`,`completedMs`,`sessionType`,`postureBaselineId`,`rawVideoStoredRemotely`,`armSwingDelta`,`trunkLeanDelta`,`gaitInitiationDelta`,`flaggedIssues`,`syncedToCloud`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AssessmentSession entity) {
        statement.bindString(1, entity.getSessionId());
        statement.bindString(2, entity.getPatientId());
        statement.bindLong(3, entity.getScheduledMs());
        statement.bindLong(4, entity.getCompletedMs());
        statement.bindString(5, entity.getSessionType());
        statement.bindString(6, entity.getPostureBaselineId());
        final int _tmp = entity.getRawVideoStoredRemotely() ? 1 : 0;
        statement.bindLong(7, _tmp);
        statement.bindDouble(8, entity.getArmSwingDelta());
        statement.bindDouble(9, entity.getTrunkLeanDelta());
        statement.bindDouble(10, entity.getGaitInitiationDelta());
        statement.bindString(11, entity.getFlaggedIssues());
        final int _tmp_1 = entity.getSyncedToCloud() ? 1 : 0;
        statement.bindLong(12, _tmp_1);
      }
    };
    this.__preparedStmtOfMarkComplete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE assessment_sessions SET completedMs = ?, postureBaselineId = ? WHERE sessionId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final AssessmentSession session,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfAssessmentSession.insert(session);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object markComplete(final String sessionId, final long completedMs,
      final String baselineId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkComplete.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, completedMs);
        _argIndex = 2;
        _stmt.bindString(_argIndex, baselineId);
        _argIndex = 3;
        _stmt.bindString(_argIndex, sessionId);
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
          __preparedStmtOfMarkComplete.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getAllForPatient(final String patientId,
      final Continuation<? super List<AssessmentSession>> $completion) {
    final String _sql = "SELECT * FROM assessment_sessions WHERE patientId = ? ORDER BY scheduledMs DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, patientId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<AssessmentSession>>() {
      @Override
      @NonNull
      public List<AssessmentSession> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfScheduledMs = CursorUtil.getColumnIndexOrThrow(_cursor, "scheduledMs");
          final int _cursorIndexOfCompletedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "completedMs");
          final int _cursorIndexOfSessionType = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionType");
          final int _cursorIndexOfPostureBaselineId = CursorUtil.getColumnIndexOrThrow(_cursor, "postureBaselineId");
          final int _cursorIndexOfRawVideoStoredRemotely = CursorUtil.getColumnIndexOrThrow(_cursor, "rawVideoStoredRemotely");
          final int _cursorIndexOfArmSwingDelta = CursorUtil.getColumnIndexOrThrow(_cursor, "armSwingDelta");
          final int _cursorIndexOfTrunkLeanDelta = CursorUtil.getColumnIndexOrThrow(_cursor, "trunkLeanDelta");
          final int _cursorIndexOfGaitInitiationDelta = CursorUtil.getColumnIndexOrThrow(_cursor, "gaitInitiationDelta");
          final int _cursorIndexOfFlaggedIssues = CursorUtil.getColumnIndexOrThrow(_cursor, "flaggedIssues");
          final int _cursorIndexOfSyncedToCloud = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedToCloud");
          final List<AssessmentSession> _result = new ArrayList<AssessmentSession>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AssessmentSession _item;
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final long _tmpScheduledMs;
            _tmpScheduledMs = _cursor.getLong(_cursorIndexOfScheduledMs);
            final long _tmpCompletedMs;
            _tmpCompletedMs = _cursor.getLong(_cursorIndexOfCompletedMs);
            final String _tmpSessionType;
            _tmpSessionType = _cursor.getString(_cursorIndexOfSessionType);
            final String _tmpPostureBaselineId;
            _tmpPostureBaselineId = _cursor.getString(_cursorIndexOfPostureBaselineId);
            final boolean _tmpRawVideoStoredRemotely;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfRawVideoStoredRemotely);
            _tmpRawVideoStoredRemotely = _tmp != 0;
            final float _tmpArmSwingDelta;
            _tmpArmSwingDelta = _cursor.getFloat(_cursorIndexOfArmSwingDelta);
            final float _tmpTrunkLeanDelta;
            _tmpTrunkLeanDelta = _cursor.getFloat(_cursorIndexOfTrunkLeanDelta);
            final float _tmpGaitInitiationDelta;
            _tmpGaitInitiationDelta = _cursor.getFloat(_cursorIndexOfGaitInitiationDelta);
            final String _tmpFlaggedIssues;
            _tmpFlaggedIssues = _cursor.getString(_cursorIndexOfFlaggedIssues);
            final boolean _tmpSyncedToCloud;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfSyncedToCloud);
            _tmpSyncedToCloud = _tmp_1 != 0;
            _item = new AssessmentSession(_tmpSessionId,_tmpPatientId,_tmpScheduledMs,_tmpCompletedMs,_tmpSessionType,_tmpPostureBaselineId,_tmpRawVideoStoredRemotely,_tmpArmSwingDelta,_tmpTrunkLeanDelta,_tmpGaitInitiationDelta,_tmpFlaggedIssues,_tmpSyncedToCloud);
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
  public Object getNextPending(final String patientId,
      final Continuation<? super AssessmentSession> $completion) {
    final String _sql = "SELECT * FROM assessment_sessions WHERE patientId = ? AND completedMs = 0 ORDER BY scheduledMs ASC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, patientId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<AssessmentSession>() {
      @Override
      @Nullable
      public AssessmentSession call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSessionId = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionId");
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfScheduledMs = CursorUtil.getColumnIndexOrThrow(_cursor, "scheduledMs");
          final int _cursorIndexOfCompletedMs = CursorUtil.getColumnIndexOrThrow(_cursor, "completedMs");
          final int _cursorIndexOfSessionType = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionType");
          final int _cursorIndexOfPostureBaselineId = CursorUtil.getColumnIndexOrThrow(_cursor, "postureBaselineId");
          final int _cursorIndexOfRawVideoStoredRemotely = CursorUtil.getColumnIndexOrThrow(_cursor, "rawVideoStoredRemotely");
          final int _cursorIndexOfArmSwingDelta = CursorUtil.getColumnIndexOrThrow(_cursor, "armSwingDelta");
          final int _cursorIndexOfTrunkLeanDelta = CursorUtil.getColumnIndexOrThrow(_cursor, "trunkLeanDelta");
          final int _cursorIndexOfGaitInitiationDelta = CursorUtil.getColumnIndexOrThrow(_cursor, "gaitInitiationDelta");
          final int _cursorIndexOfFlaggedIssues = CursorUtil.getColumnIndexOrThrow(_cursor, "flaggedIssues");
          final int _cursorIndexOfSyncedToCloud = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedToCloud");
          final AssessmentSession _result;
          if (_cursor.moveToFirst()) {
            final String _tmpSessionId;
            _tmpSessionId = _cursor.getString(_cursorIndexOfSessionId);
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final long _tmpScheduledMs;
            _tmpScheduledMs = _cursor.getLong(_cursorIndexOfScheduledMs);
            final long _tmpCompletedMs;
            _tmpCompletedMs = _cursor.getLong(_cursorIndexOfCompletedMs);
            final String _tmpSessionType;
            _tmpSessionType = _cursor.getString(_cursorIndexOfSessionType);
            final String _tmpPostureBaselineId;
            _tmpPostureBaselineId = _cursor.getString(_cursorIndexOfPostureBaselineId);
            final boolean _tmpRawVideoStoredRemotely;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfRawVideoStoredRemotely);
            _tmpRawVideoStoredRemotely = _tmp != 0;
            final float _tmpArmSwingDelta;
            _tmpArmSwingDelta = _cursor.getFloat(_cursorIndexOfArmSwingDelta);
            final float _tmpTrunkLeanDelta;
            _tmpTrunkLeanDelta = _cursor.getFloat(_cursorIndexOfTrunkLeanDelta);
            final float _tmpGaitInitiationDelta;
            _tmpGaitInitiationDelta = _cursor.getFloat(_cursorIndexOfGaitInitiationDelta);
            final String _tmpFlaggedIssues;
            _tmpFlaggedIssues = _cursor.getString(_cursorIndexOfFlaggedIssues);
            final boolean _tmpSyncedToCloud;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfSyncedToCloud);
            _tmpSyncedToCloud = _tmp_1 != 0;
            _result = new AssessmentSession(_tmpSessionId,_tmpPatientId,_tmpScheduledMs,_tmpCompletedMs,_tmpSessionType,_tmpPostureBaselineId,_tmpRawVideoStoredRemotely,_tmpArmSwingDelta,_tmpTrunkLeanDelta,_tmpGaitInitiationDelta,_tmpFlaggedIssues,_tmpSyncedToCloud);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
