package health.cogniware.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import health.cogniware.data.models.DiagnosisProfile;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
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
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class DiagnosisProfileDao_Impl implements DiagnosisProfileDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<DiagnosisProfile> __insertionAdapterOfDiagnosisProfile;

  private final EntityDeletionOrUpdateAdapter<DiagnosisProfile> __updateAdapterOfDiagnosisProfile;

  private final SharedSQLiteStatement __preparedStmtOfDelete;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public DiagnosisProfileDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfDiagnosisProfile = new EntityInsertionAdapter<DiagnosisProfile>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `diagnosis_profiles` (`profileId`,`patientId`,`conditionType`,`ageGroup`,`subtype`,`diagnosisDateMs`,`caregiverId`,`notifyTarget`,`anomalyModel`,`medicationModel`,`riskWeightsJson`,`createdAtMs`,`syncedToCloud`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DiagnosisProfile entity) {
        statement.bindString(1, entity.getProfileId());
        statement.bindString(2, entity.getPatientId());
        statement.bindString(3, entity.getConditionType());
        statement.bindString(4, entity.getAgeGroup());
        if (entity.getSubtype() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getSubtype());
        }
        if (entity.getDiagnosisDateMs() == null) {
          statement.bindNull(6);
        } else {
          statement.bindLong(6, entity.getDiagnosisDateMs());
        }
        if (entity.getCaregiverId() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getCaregiverId());
        }
        statement.bindString(8, entity.getNotifyTarget());
        statement.bindString(9, entity.getAnomalyModel());
        statement.bindString(10, entity.getMedicationModel());
        statement.bindString(11, entity.getRiskWeightsJson());
        statement.bindLong(12, entity.getCreatedAtMs());
        final int _tmp = entity.getSyncedToCloud() ? 1 : 0;
        statement.bindLong(13, _tmp);
      }
    };
    this.__updateAdapterOfDiagnosisProfile = new EntityDeletionOrUpdateAdapter<DiagnosisProfile>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `diagnosis_profiles` SET `profileId` = ?,`patientId` = ?,`conditionType` = ?,`ageGroup` = ?,`subtype` = ?,`diagnosisDateMs` = ?,`caregiverId` = ?,`notifyTarget` = ?,`anomalyModel` = ?,`medicationModel` = ?,`riskWeightsJson` = ?,`createdAtMs` = ?,`syncedToCloud` = ? WHERE `profileId` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DiagnosisProfile entity) {
        statement.bindString(1, entity.getProfileId());
        statement.bindString(2, entity.getPatientId());
        statement.bindString(3, entity.getConditionType());
        statement.bindString(4, entity.getAgeGroup());
        if (entity.getSubtype() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getSubtype());
        }
        if (entity.getDiagnosisDateMs() == null) {
          statement.bindNull(6);
        } else {
          statement.bindLong(6, entity.getDiagnosisDateMs());
        }
        if (entity.getCaregiverId() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getCaregiverId());
        }
        statement.bindString(8, entity.getNotifyTarget());
        statement.bindString(9, entity.getAnomalyModel());
        statement.bindString(10, entity.getMedicationModel());
        statement.bindString(11, entity.getRiskWeightsJson());
        statement.bindLong(12, entity.getCreatedAtMs());
        final int _tmp = entity.getSyncedToCloud() ? 1 : 0;
        statement.bindLong(13, _tmp);
        statement.bindString(14, entity.getProfileId());
      }
    };
    this.__preparedStmtOfDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM diagnosis_profiles WHERE profileId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM patient_profile";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final DiagnosisProfile profile,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfDiagnosisProfile.insert(profile);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final DiagnosisProfile profile,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfDiagnosisProfile.handle(profile);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final String profileId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDelete.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, profileId);
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
          __preparedStmtOfDelete.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
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
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getProfileForPatient(final String patientId,
      final Continuation<? super DiagnosisProfile> $completion) {
    final String _sql = "SELECT * FROM diagnosis_profiles WHERE patientId = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, patientId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<DiagnosisProfile>() {
      @Override
      @Nullable
      public DiagnosisProfile call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfProfileId = CursorUtil.getColumnIndexOrThrow(_cursor, "profileId");
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfConditionType = CursorUtil.getColumnIndexOrThrow(_cursor, "conditionType");
          final int _cursorIndexOfAgeGroup = CursorUtil.getColumnIndexOrThrow(_cursor, "ageGroup");
          final int _cursorIndexOfSubtype = CursorUtil.getColumnIndexOrThrow(_cursor, "subtype");
          final int _cursorIndexOfDiagnosisDateMs = CursorUtil.getColumnIndexOrThrow(_cursor, "diagnosisDateMs");
          final int _cursorIndexOfCaregiverId = CursorUtil.getColumnIndexOrThrow(_cursor, "caregiverId");
          final int _cursorIndexOfNotifyTarget = CursorUtil.getColumnIndexOrThrow(_cursor, "notifyTarget");
          final int _cursorIndexOfAnomalyModel = CursorUtil.getColumnIndexOrThrow(_cursor, "anomalyModel");
          final int _cursorIndexOfMedicationModel = CursorUtil.getColumnIndexOrThrow(_cursor, "medicationModel");
          final int _cursorIndexOfRiskWeightsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "riskWeightsJson");
          final int _cursorIndexOfCreatedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtMs");
          final int _cursorIndexOfSyncedToCloud = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedToCloud");
          final DiagnosisProfile _result;
          if (_cursor.moveToFirst()) {
            final String _tmpProfileId;
            _tmpProfileId = _cursor.getString(_cursorIndexOfProfileId);
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final String _tmpConditionType;
            _tmpConditionType = _cursor.getString(_cursorIndexOfConditionType);
            final String _tmpAgeGroup;
            _tmpAgeGroup = _cursor.getString(_cursorIndexOfAgeGroup);
            final String _tmpSubtype;
            if (_cursor.isNull(_cursorIndexOfSubtype)) {
              _tmpSubtype = null;
            } else {
              _tmpSubtype = _cursor.getString(_cursorIndexOfSubtype);
            }
            final Long _tmpDiagnosisDateMs;
            if (_cursor.isNull(_cursorIndexOfDiagnosisDateMs)) {
              _tmpDiagnosisDateMs = null;
            } else {
              _tmpDiagnosisDateMs = _cursor.getLong(_cursorIndexOfDiagnosisDateMs);
            }
            final String _tmpCaregiverId;
            if (_cursor.isNull(_cursorIndexOfCaregiverId)) {
              _tmpCaregiverId = null;
            } else {
              _tmpCaregiverId = _cursor.getString(_cursorIndexOfCaregiverId);
            }
            final String _tmpNotifyTarget;
            _tmpNotifyTarget = _cursor.getString(_cursorIndexOfNotifyTarget);
            final String _tmpAnomalyModel;
            _tmpAnomalyModel = _cursor.getString(_cursorIndexOfAnomalyModel);
            final String _tmpMedicationModel;
            _tmpMedicationModel = _cursor.getString(_cursorIndexOfMedicationModel);
            final String _tmpRiskWeightsJson;
            _tmpRiskWeightsJson = _cursor.getString(_cursorIndexOfRiskWeightsJson);
            final long _tmpCreatedAtMs;
            _tmpCreatedAtMs = _cursor.getLong(_cursorIndexOfCreatedAtMs);
            final boolean _tmpSyncedToCloud;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSyncedToCloud);
            _tmpSyncedToCloud = _tmp != 0;
            _result = new DiagnosisProfile(_tmpProfileId,_tmpPatientId,_tmpConditionType,_tmpAgeGroup,_tmpSubtype,_tmpDiagnosisDateMs,_tmpCaregiverId,_tmpNotifyTarget,_tmpAnomalyModel,_tmpMedicationModel,_tmpRiskWeightsJson,_tmpCreatedAtMs,_tmpSyncedToCloud);
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
  public Flow<List<DiagnosisProfile>> getAllProfiles() {
    final String _sql = "SELECT * FROM diagnosis_profiles ORDER BY createdAtMs DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"diagnosis_profiles"}, new Callable<List<DiagnosisProfile>>() {
      @Override
      @NonNull
      public List<DiagnosisProfile> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfProfileId = CursorUtil.getColumnIndexOrThrow(_cursor, "profileId");
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfConditionType = CursorUtil.getColumnIndexOrThrow(_cursor, "conditionType");
          final int _cursorIndexOfAgeGroup = CursorUtil.getColumnIndexOrThrow(_cursor, "ageGroup");
          final int _cursorIndexOfSubtype = CursorUtil.getColumnIndexOrThrow(_cursor, "subtype");
          final int _cursorIndexOfDiagnosisDateMs = CursorUtil.getColumnIndexOrThrow(_cursor, "diagnosisDateMs");
          final int _cursorIndexOfCaregiverId = CursorUtil.getColumnIndexOrThrow(_cursor, "caregiverId");
          final int _cursorIndexOfNotifyTarget = CursorUtil.getColumnIndexOrThrow(_cursor, "notifyTarget");
          final int _cursorIndexOfAnomalyModel = CursorUtil.getColumnIndexOrThrow(_cursor, "anomalyModel");
          final int _cursorIndexOfMedicationModel = CursorUtil.getColumnIndexOrThrow(_cursor, "medicationModel");
          final int _cursorIndexOfRiskWeightsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "riskWeightsJson");
          final int _cursorIndexOfCreatedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtMs");
          final int _cursorIndexOfSyncedToCloud = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedToCloud");
          final List<DiagnosisProfile> _result = new ArrayList<DiagnosisProfile>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DiagnosisProfile _item;
            final String _tmpProfileId;
            _tmpProfileId = _cursor.getString(_cursorIndexOfProfileId);
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final String _tmpConditionType;
            _tmpConditionType = _cursor.getString(_cursorIndexOfConditionType);
            final String _tmpAgeGroup;
            _tmpAgeGroup = _cursor.getString(_cursorIndexOfAgeGroup);
            final String _tmpSubtype;
            if (_cursor.isNull(_cursorIndexOfSubtype)) {
              _tmpSubtype = null;
            } else {
              _tmpSubtype = _cursor.getString(_cursorIndexOfSubtype);
            }
            final Long _tmpDiagnosisDateMs;
            if (_cursor.isNull(_cursorIndexOfDiagnosisDateMs)) {
              _tmpDiagnosisDateMs = null;
            } else {
              _tmpDiagnosisDateMs = _cursor.getLong(_cursorIndexOfDiagnosisDateMs);
            }
            final String _tmpCaregiverId;
            if (_cursor.isNull(_cursorIndexOfCaregiverId)) {
              _tmpCaregiverId = null;
            } else {
              _tmpCaregiverId = _cursor.getString(_cursorIndexOfCaregiverId);
            }
            final String _tmpNotifyTarget;
            _tmpNotifyTarget = _cursor.getString(_cursorIndexOfNotifyTarget);
            final String _tmpAnomalyModel;
            _tmpAnomalyModel = _cursor.getString(_cursorIndexOfAnomalyModel);
            final String _tmpMedicationModel;
            _tmpMedicationModel = _cursor.getString(_cursorIndexOfMedicationModel);
            final String _tmpRiskWeightsJson;
            _tmpRiskWeightsJson = _cursor.getString(_cursorIndexOfRiskWeightsJson);
            final long _tmpCreatedAtMs;
            _tmpCreatedAtMs = _cursor.getLong(_cursorIndexOfCreatedAtMs);
            final boolean _tmpSyncedToCloud;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSyncedToCloud);
            _tmpSyncedToCloud = _tmp != 0;
            _item = new DiagnosisProfile(_tmpProfileId,_tmpPatientId,_tmpConditionType,_tmpAgeGroup,_tmpSubtype,_tmpDiagnosisDateMs,_tmpCaregiverId,_tmpNotifyTarget,_tmpAnomalyModel,_tmpMedicationModel,_tmpRiskWeightsJson,_tmpCreatedAtMs,_tmpSyncedToCloud);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
