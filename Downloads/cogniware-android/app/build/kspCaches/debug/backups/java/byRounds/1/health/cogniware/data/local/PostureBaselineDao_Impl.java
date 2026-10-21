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
import androidx.sqlite.db.SupportSQLiteStatement;
import health.cogniware.data.models.PostureBaseline;
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
public final class PostureBaselineDao_Impl implements PostureBaselineDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<PostureBaseline> __insertionAdapterOfPostureBaseline;

  public PostureBaselineDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfPostureBaseline = new EntityInsertionAdapter<PostureBaseline>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `posture_baselines` (`baselineId`,`patientId`,`capturedAtMs`,`sessionType`,`armSwingAsymmetryIndex`,`gaitInitiationDelayMs`,`trunkForwardLeanDeg`,`stepSymmetryIndex`,`cadenceStepsPerMin`,`landmarkSeriesJson`,`qualityScore`,`flaggedIssues`,`syncedToCloud`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PostureBaseline entity) {
        statement.bindString(1, entity.getBaselineId());
        statement.bindString(2, entity.getPatientId());
        statement.bindLong(3, entity.getCapturedAtMs());
        statement.bindString(4, entity.getSessionType());
        statement.bindDouble(5, entity.getArmSwingAsymmetryIndex());
        statement.bindDouble(6, entity.getGaitInitiationDelayMs());
        statement.bindDouble(7, entity.getTrunkForwardLeanDeg());
        statement.bindDouble(8, entity.getStepSymmetryIndex());
        statement.bindDouble(9, entity.getCadenceStepsPerMin());
        statement.bindString(10, entity.getLandmarkSeriesJson());
        statement.bindDouble(11, entity.getQualityScore());
        statement.bindString(12, entity.getFlaggedIssues());
        final int _tmp = entity.getSyncedToCloud() ? 1 : 0;
        statement.bindLong(13, _tmp);
      }
    };
  }

  @Override
  public Object insert(final PostureBaseline baseline,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfPostureBaseline.insert(baseline);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object getAllForPatient(final String patientId,
      final Continuation<? super List<PostureBaseline>> $completion) {
    final String _sql = "SELECT * FROM posture_baselines WHERE patientId = ? ORDER BY capturedAtMs DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, patientId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<PostureBaseline>>() {
      @Override
      @NonNull
      public List<PostureBaseline> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfBaselineId = CursorUtil.getColumnIndexOrThrow(_cursor, "baselineId");
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfCapturedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "capturedAtMs");
          final int _cursorIndexOfSessionType = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionType");
          final int _cursorIndexOfArmSwingAsymmetryIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "armSwingAsymmetryIndex");
          final int _cursorIndexOfGaitInitiationDelayMs = CursorUtil.getColumnIndexOrThrow(_cursor, "gaitInitiationDelayMs");
          final int _cursorIndexOfTrunkForwardLeanDeg = CursorUtil.getColumnIndexOrThrow(_cursor, "trunkForwardLeanDeg");
          final int _cursorIndexOfStepSymmetryIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "stepSymmetryIndex");
          final int _cursorIndexOfCadenceStepsPerMin = CursorUtil.getColumnIndexOrThrow(_cursor, "cadenceStepsPerMin");
          final int _cursorIndexOfLandmarkSeriesJson = CursorUtil.getColumnIndexOrThrow(_cursor, "landmarkSeriesJson");
          final int _cursorIndexOfQualityScore = CursorUtil.getColumnIndexOrThrow(_cursor, "qualityScore");
          final int _cursorIndexOfFlaggedIssues = CursorUtil.getColumnIndexOrThrow(_cursor, "flaggedIssues");
          final int _cursorIndexOfSyncedToCloud = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedToCloud");
          final List<PostureBaseline> _result = new ArrayList<PostureBaseline>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PostureBaseline _item;
            final String _tmpBaselineId;
            _tmpBaselineId = _cursor.getString(_cursorIndexOfBaselineId);
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final long _tmpCapturedAtMs;
            _tmpCapturedAtMs = _cursor.getLong(_cursorIndexOfCapturedAtMs);
            final String _tmpSessionType;
            _tmpSessionType = _cursor.getString(_cursorIndexOfSessionType);
            final float _tmpArmSwingAsymmetryIndex;
            _tmpArmSwingAsymmetryIndex = _cursor.getFloat(_cursorIndexOfArmSwingAsymmetryIndex);
            final float _tmpGaitInitiationDelayMs;
            _tmpGaitInitiationDelayMs = _cursor.getFloat(_cursorIndexOfGaitInitiationDelayMs);
            final float _tmpTrunkForwardLeanDeg;
            _tmpTrunkForwardLeanDeg = _cursor.getFloat(_cursorIndexOfTrunkForwardLeanDeg);
            final float _tmpStepSymmetryIndex;
            _tmpStepSymmetryIndex = _cursor.getFloat(_cursorIndexOfStepSymmetryIndex);
            final float _tmpCadenceStepsPerMin;
            _tmpCadenceStepsPerMin = _cursor.getFloat(_cursorIndexOfCadenceStepsPerMin);
            final String _tmpLandmarkSeriesJson;
            _tmpLandmarkSeriesJson = _cursor.getString(_cursorIndexOfLandmarkSeriesJson);
            final float _tmpQualityScore;
            _tmpQualityScore = _cursor.getFloat(_cursorIndexOfQualityScore);
            final String _tmpFlaggedIssues;
            _tmpFlaggedIssues = _cursor.getString(_cursorIndexOfFlaggedIssues);
            final boolean _tmpSyncedToCloud;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSyncedToCloud);
            _tmpSyncedToCloud = _tmp != 0;
            _item = new PostureBaseline(_tmpBaselineId,_tmpPatientId,_tmpCapturedAtMs,_tmpSessionType,_tmpArmSwingAsymmetryIndex,_tmpGaitInitiationDelayMs,_tmpTrunkForwardLeanDeg,_tmpStepSymmetryIndex,_tmpCadenceStepsPerMin,_tmpLandmarkSeriesJson,_tmpQualityScore,_tmpFlaggedIssues,_tmpSyncedToCloud);
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
  public Object getEnrollmentBaseline(final String patientId,
      final Continuation<? super PostureBaseline> $completion) {
    final String _sql = "SELECT * FROM posture_baselines WHERE patientId = ? ORDER BY capturedAtMs ASC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, patientId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<PostureBaseline>() {
      @Override
      @Nullable
      public PostureBaseline call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfBaselineId = CursorUtil.getColumnIndexOrThrow(_cursor, "baselineId");
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfCapturedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "capturedAtMs");
          final int _cursorIndexOfSessionType = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionType");
          final int _cursorIndexOfArmSwingAsymmetryIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "armSwingAsymmetryIndex");
          final int _cursorIndexOfGaitInitiationDelayMs = CursorUtil.getColumnIndexOrThrow(_cursor, "gaitInitiationDelayMs");
          final int _cursorIndexOfTrunkForwardLeanDeg = CursorUtil.getColumnIndexOrThrow(_cursor, "trunkForwardLeanDeg");
          final int _cursorIndexOfStepSymmetryIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "stepSymmetryIndex");
          final int _cursorIndexOfCadenceStepsPerMin = CursorUtil.getColumnIndexOrThrow(_cursor, "cadenceStepsPerMin");
          final int _cursorIndexOfLandmarkSeriesJson = CursorUtil.getColumnIndexOrThrow(_cursor, "landmarkSeriesJson");
          final int _cursorIndexOfQualityScore = CursorUtil.getColumnIndexOrThrow(_cursor, "qualityScore");
          final int _cursorIndexOfFlaggedIssues = CursorUtil.getColumnIndexOrThrow(_cursor, "flaggedIssues");
          final int _cursorIndexOfSyncedToCloud = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedToCloud");
          final PostureBaseline _result;
          if (_cursor.moveToFirst()) {
            final String _tmpBaselineId;
            _tmpBaselineId = _cursor.getString(_cursorIndexOfBaselineId);
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final long _tmpCapturedAtMs;
            _tmpCapturedAtMs = _cursor.getLong(_cursorIndexOfCapturedAtMs);
            final String _tmpSessionType;
            _tmpSessionType = _cursor.getString(_cursorIndexOfSessionType);
            final float _tmpArmSwingAsymmetryIndex;
            _tmpArmSwingAsymmetryIndex = _cursor.getFloat(_cursorIndexOfArmSwingAsymmetryIndex);
            final float _tmpGaitInitiationDelayMs;
            _tmpGaitInitiationDelayMs = _cursor.getFloat(_cursorIndexOfGaitInitiationDelayMs);
            final float _tmpTrunkForwardLeanDeg;
            _tmpTrunkForwardLeanDeg = _cursor.getFloat(_cursorIndexOfTrunkForwardLeanDeg);
            final float _tmpStepSymmetryIndex;
            _tmpStepSymmetryIndex = _cursor.getFloat(_cursorIndexOfStepSymmetryIndex);
            final float _tmpCadenceStepsPerMin;
            _tmpCadenceStepsPerMin = _cursor.getFloat(_cursorIndexOfCadenceStepsPerMin);
            final String _tmpLandmarkSeriesJson;
            _tmpLandmarkSeriesJson = _cursor.getString(_cursorIndexOfLandmarkSeriesJson);
            final float _tmpQualityScore;
            _tmpQualityScore = _cursor.getFloat(_cursorIndexOfQualityScore);
            final String _tmpFlaggedIssues;
            _tmpFlaggedIssues = _cursor.getString(_cursorIndexOfFlaggedIssues);
            final boolean _tmpSyncedToCloud;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSyncedToCloud);
            _tmpSyncedToCloud = _tmp != 0;
            _result = new PostureBaseline(_tmpBaselineId,_tmpPatientId,_tmpCapturedAtMs,_tmpSessionType,_tmpArmSwingAsymmetryIndex,_tmpGaitInitiationDelayMs,_tmpTrunkForwardLeanDeg,_tmpStepSymmetryIndex,_tmpCadenceStepsPerMin,_tmpLandmarkSeriesJson,_tmpQualityScore,_tmpFlaggedIssues,_tmpSyncedToCloud);
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
  public Object getLatest(final String patientId,
      final Continuation<? super PostureBaseline> $completion) {
    final String _sql = "SELECT * FROM posture_baselines WHERE patientId = ? ORDER BY capturedAtMs DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, patientId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<PostureBaseline>() {
      @Override
      @Nullable
      public PostureBaseline call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfBaselineId = CursorUtil.getColumnIndexOrThrow(_cursor, "baselineId");
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfCapturedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "capturedAtMs");
          final int _cursorIndexOfSessionType = CursorUtil.getColumnIndexOrThrow(_cursor, "sessionType");
          final int _cursorIndexOfArmSwingAsymmetryIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "armSwingAsymmetryIndex");
          final int _cursorIndexOfGaitInitiationDelayMs = CursorUtil.getColumnIndexOrThrow(_cursor, "gaitInitiationDelayMs");
          final int _cursorIndexOfTrunkForwardLeanDeg = CursorUtil.getColumnIndexOrThrow(_cursor, "trunkForwardLeanDeg");
          final int _cursorIndexOfStepSymmetryIndex = CursorUtil.getColumnIndexOrThrow(_cursor, "stepSymmetryIndex");
          final int _cursorIndexOfCadenceStepsPerMin = CursorUtil.getColumnIndexOrThrow(_cursor, "cadenceStepsPerMin");
          final int _cursorIndexOfLandmarkSeriesJson = CursorUtil.getColumnIndexOrThrow(_cursor, "landmarkSeriesJson");
          final int _cursorIndexOfQualityScore = CursorUtil.getColumnIndexOrThrow(_cursor, "qualityScore");
          final int _cursorIndexOfFlaggedIssues = CursorUtil.getColumnIndexOrThrow(_cursor, "flaggedIssues");
          final int _cursorIndexOfSyncedToCloud = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedToCloud");
          final PostureBaseline _result;
          if (_cursor.moveToFirst()) {
            final String _tmpBaselineId;
            _tmpBaselineId = _cursor.getString(_cursorIndexOfBaselineId);
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final long _tmpCapturedAtMs;
            _tmpCapturedAtMs = _cursor.getLong(_cursorIndexOfCapturedAtMs);
            final String _tmpSessionType;
            _tmpSessionType = _cursor.getString(_cursorIndexOfSessionType);
            final float _tmpArmSwingAsymmetryIndex;
            _tmpArmSwingAsymmetryIndex = _cursor.getFloat(_cursorIndexOfArmSwingAsymmetryIndex);
            final float _tmpGaitInitiationDelayMs;
            _tmpGaitInitiationDelayMs = _cursor.getFloat(_cursorIndexOfGaitInitiationDelayMs);
            final float _tmpTrunkForwardLeanDeg;
            _tmpTrunkForwardLeanDeg = _cursor.getFloat(_cursorIndexOfTrunkForwardLeanDeg);
            final float _tmpStepSymmetryIndex;
            _tmpStepSymmetryIndex = _cursor.getFloat(_cursorIndexOfStepSymmetryIndex);
            final float _tmpCadenceStepsPerMin;
            _tmpCadenceStepsPerMin = _cursor.getFloat(_cursorIndexOfCadenceStepsPerMin);
            final String _tmpLandmarkSeriesJson;
            _tmpLandmarkSeriesJson = _cursor.getString(_cursorIndexOfLandmarkSeriesJson);
            final float _tmpQualityScore;
            _tmpQualityScore = _cursor.getFloat(_cursorIndexOfQualityScore);
            final String _tmpFlaggedIssues;
            _tmpFlaggedIssues = _cursor.getString(_cursorIndexOfFlaggedIssues);
            final boolean _tmpSyncedToCloud;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSyncedToCloud);
            _tmpSyncedToCloud = _tmp != 0;
            _result = new PostureBaseline(_tmpBaselineId,_tmpPatientId,_tmpCapturedAtMs,_tmpSessionType,_tmpArmSwingAsymmetryIndex,_tmpGaitInitiationDelayMs,_tmpTrunkForwardLeanDeg,_tmpStepSymmetryIndex,_tmpCadenceStepsPerMin,_tmpLandmarkSeriesJson,_tmpQualityScore,_tmpFlaggedIssues,_tmpSyncedToCloud);
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
