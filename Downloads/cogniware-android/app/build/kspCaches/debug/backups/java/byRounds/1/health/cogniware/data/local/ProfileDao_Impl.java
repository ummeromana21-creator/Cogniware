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
import health.cogniware.data.models.PatientProfile;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ProfileDao_Impl implements ProfileDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<PatientProfile> __insertionAdapterOfPatientProfile;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public ProfileDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfPatientProfile = new EntityInsertionAdapter<PatientProfile>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `patient_profile` (`patientId`,`enrollmentCode`,`enrolledAtMs`,`yearOfBirth`,`biologicalSex`,`dominantHand`,`primaryDiagnosis`,`secondaryDiagnoses`,`diagnosisStage`,`medications`,`mobilityAids`,`dominantHandDisability`,`visionImpairment`,`hearingImpairment`,`activeModules`,`baselinePhaseDays`,`clinicianId`,`supabaseUserId`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PatientProfile entity) {
        statement.bindString(1, entity.getPatientId());
        statement.bindString(2, entity.getEnrollmentCode());
        statement.bindLong(3, entity.getEnrolledAtMs());
        statement.bindLong(4, entity.getYearOfBirth());
        statement.bindString(5, entity.getBiologicalSex());
        statement.bindString(6, entity.getDominantHand());
        statement.bindString(7, entity.getPrimaryDiagnosis());
        statement.bindString(8, entity.getSecondaryDiagnoses());
        statement.bindString(9, entity.getDiagnosisStage());
        statement.bindString(10, entity.getMedications());
        statement.bindString(11, entity.getMobilityAids());
        final int _tmp = entity.getDominantHandDisability() ? 1 : 0;
        statement.bindLong(12, _tmp);
        final int _tmp_1 = entity.getVisionImpairment() ? 1 : 0;
        statement.bindLong(13, _tmp_1);
        final int _tmp_2 = entity.getHearingImpairment() ? 1 : 0;
        statement.bindLong(14, _tmp_2);
        statement.bindString(15, entity.getActiveModules());
        statement.bindLong(16, entity.getBaselinePhaseDays());
        statement.bindString(17, entity.getClinicianId());
        statement.bindString(18, entity.getSupabaseUserId());
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
  public Object insert(final PatientProfile profile, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfPatientProfile.insert(profile);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
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
  public Object get(final String patientId,
      final Continuation<? super PatientProfile> $completion) {
    final String _sql = "SELECT * FROM patient_profile WHERE patientId = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, patientId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<PatientProfile>() {
      @Override
      @Nullable
      public PatientProfile call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfEnrollmentCode = CursorUtil.getColumnIndexOrThrow(_cursor, "enrollmentCode");
          final int _cursorIndexOfEnrolledAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "enrolledAtMs");
          final int _cursorIndexOfYearOfBirth = CursorUtil.getColumnIndexOrThrow(_cursor, "yearOfBirth");
          final int _cursorIndexOfBiologicalSex = CursorUtil.getColumnIndexOrThrow(_cursor, "biologicalSex");
          final int _cursorIndexOfDominantHand = CursorUtil.getColumnIndexOrThrow(_cursor, "dominantHand");
          final int _cursorIndexOfPrimaryDiagnosis = CursorUtil.getColumnIndexOrThrow(_cursor, "primaryDiagnosis");
          final int _cursorIndexOfSecondaryDiagnoses = CursorUtil.getColumnIndexOrThrow(_cursor, "secondaryDiagnoses");
          final int _cursorIndexOfDiagnosisStage = CursorUtil.getColumnIndexOrThrow(_cursor, "diagnosisStage");
          final int _cursorIndexOfMedications = CursorUtil.getColumnIndexOrThrow(_cursor, "medications");
          final int _cursorIndexOfMobilityAids = CursorUtil.getColumnIndexOrThrow(_cursor, "mobilityAids");
          final int _cursorIndexOfDominantHandDisability = CursorUtil.getColumnIndexOrThrow(_cursor, "dominantHandDisability");
          final int _cursorIndexOfVisionImpairment = CursorUtil.getColumnIndexOrThrow(_cursor, "visionImpairment");
          final int _cursorIndexOfHearingImpairment = CursorUtil.getColumnIndexOrThrow(_cursor, "hearingImpairment");
          final int _cursorIndexOfActiveModules = CursorUtil.getColumnIndexOrThrow(_cursor, "activeModules");
          final int _cursorIndexOfBaselinePhaseDays = CursorUtil.getColumnIndexOrThrow(_cursor, "baselinePhaseDays");
          final int _cursorIndexOfClinicianId = CursorUtil.getColumnIndexOrThrow(_cursor, "clinicianId");
          final int _cursorIndexOfSupabaseUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "supabaseUserId");
          final PatientProfile _result;
          if (_cursor.moveToFirst()) {
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final String _tmpEnrollmentCode;
            _tmpEnrollmentCode = _cursor.getString(_cursorIndexOfEnrollmentCode);
            final long _tmpEnrolledAtMs;
            _tmpEnrolledAtMs = _cursor.getLong(_cursorIndexOfEnrolledAtMs);
            final int _tmpYearOfBirth;
            _tmpYearOfBirth = _cursor.getInt(_cursorIndexOfYearOfBirth);
            final String _tmpBiologicalSex;
            _tmpBiologicalSex = _cursor.getString(_cursorIndexOfBiologicalSex);
            final String _tmpDominantHand;
            _tmpDominantHand = _cursor.getString(_cursorIndexOfDominantHand);
            final String _tmpPrimaryDiagnosis;
            _tmpPrimaryDiagnosis = _cursor.getString(_cursorIndexOfPrimaryDiagnosis);
            final String _tmpSecondaryDiagnoses;
            _tmpSecondaryDiagnoses = _cursor.getString(_cursorIndexOfSecondaryDiagnoses);
            final String _tmpDiagnosisStage;
            _tmpDiagnosisStage = _cursor.getString(_cursorIndexOfDiagnosisStage);
            final String _tmpMedications;
            _tmpMedications = _cursor.getString(_cursorIndexOfMedications);
            final String _tmpMobilityAids;
            _tmpMobilityAids = _cursor.getString(_cursorIndexOfMobilityAids);
            final boolean _tmpDominantHandDisability;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfDominantHandDisability);
            _tmpDominantHandDisability = _tmp != 0;
            final boolean _tmpVisionImpairment;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfVisionImpairment);
            _tmpVisionImpairment = _tmp_1 != 0;
            final boolean _tmpHearingImpairment;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfHearingImpairment);
            _tmpHearingImpairment = _tmp_2 != 0;
            final String _tmpActiveModules;
            _tmpActiveModules = _cursor.getString(_cursorIndexOfActiveModules);
            final int _tmpBaselinePhaseDays;
            _tmpBaselinePhaseDays = _cursor.getInt(_cursorIndexOfBaselinePhaseDays);
            final String _tmpClinicianId;
            _tmpClinicianId = _cursor.getString(_cursorIndexOfClinicianId);
            final String _tmpSupabaseUserId;
            _tmpSupabaseUserId = _cursor.getString(_cursorIndexOfSupabaseUserId);
            _result = new PatientProfile(_tmpPatientId,_tmpEnrollmentCode,_tmpEnrolledAtMs,_tmpYearOfBirth,_tmpBiologicalSex,_tmpDominantHand,_tmpPrimaryDiagnosis,_tmpSecondaryDiagnoses,_tmpDiagnosisStage,_tmpMedications,_tmpMobilityAids,_tmpDominantHandDisability,_tmpVisionImpairment,_tmpHearingImpairment,_tmpActiveModules,_tmpBaselinePhaseDays,_tmpClinicianId,_tmpSupabaseUserId);
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
  public Object getAny(final Continuation<? super PatientProfile> $completion) {
    final String _sql = "SELECT * FROM patient_profile LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<PatientProfile>() {
      @Override
      @Nullable
      public PatientProfile call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfEnrollmentCode = CursorUtil.getColumnIndexOrThrow(_cursor, "enrollmentCode");
          final int _cursorIndexOfEnrolledAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "enrolledAtMs");
          final int _cursorIndexOfYearOfBirth = CursorUtil.getColumnIndexOrThrow(_cursor, "yearOfBirth");
          final int _cursorIndexOfBiologicalSex = CursorUtil.getColumnIndexOrThrow(_cursor, "biologicalSex");
          final int _cursorIndexOfDominantHand = CursorUtil.getColumnIndexOrThrow(_cursor, "dominantHand");
          final int _cursorIndexOfPrimaryDiagnosis = CursorUtil.getColumnIndexOrThrow(_cursor, "primaryDiagnosis");
          final int _cursorIndexOfSecondaryDiagnoses = CursorUtil.getColumnIndexOrThrow(_cursor, "secondaryDiagnoses");
          final int _cursorIndexOfDiagnosisStage = CursorUtil.getColumnIndexOrThrow(_cursor, "diagnosisStage");
          final int _cursorIndexOfMedications = CursorUtil.getColumnIndexOrThrow(_cursor, "medications");
          final int _cursorIndexOfMobilityAids = CursorUtil.getColumnIndexOrThrow(_cursor, "mobilityAids");
          final int _cursorIndexOfDominantHandDisability = CursorUtil.getColumnIndexOrThrow(_cursor, "dominantHandDisability");
          final int _cursorIndexOfVisionImpairment = CursorUtil.getColumnIndexOrThrow(_cursor, "visionImpairment");
          final int _cursorIndexOfHearingImpairment = CursorUtil.getColumnIndexOrThrow(_cursor, "hearingImpairment");
          final int _cursorIndexOfActiveModules = CursorUtil.getColumnIndexOrThrow(_cursor, "activeModules");
          final int _cursorIndexOfBaselinePhaseDays = CursorUtil.getColumnIndexOrThrow(_cursor, "baselinePhaseDays");
          final int _cursorIndexOfClinicianId = CursorUtil.getColumnIndexOrThrow(_cursor, "clinicianId");
          final int _cursorIndexOfSupabaseUserId = CursorUtil.getColumnIndexOrThrow(_cursor, "supabaseUserId");
          final PatientProfile _result;
          if (_cursor.moveToFirst()) {
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final String _tmpEnrollmentCode;
            _tmpEnrollmentCode = _cursor.getString(_cursorIndexOfEnrollmentCode);
            final long _tmpEnrolledAtMs;
            _tmpEnrolledAtMs = _cursor.getLong(_cursorIndexOfEnrolledAtMs);
            final int _tmpYearOfBirth;
            _tmpYearOfBirth = _cursor.getInt(_cursorIndexOfYearOfBirth);
            final String _tmpBiologicalSex;
            _tmpBiologicalSex = _cursor.getString(_cursorIndexOfBiologicalSex);
            final String _tmpDominantHand;
            _tmpDominantHand = _cursor.getString(_cursorIndexOfDominantHand);
            final String _tmpPrimaryDiagnosis;
            _tmpPrimaryDiagnosis = _cursor.getString(_cursorIndexOfPrimaryDiagnosis);
            final String _tmpSecondaryDiagnoses;
            _tmpSecondaryDiagnoses = _cursor.getString(_cursorIndexOfSecondaryDiagnoses);
            final String _tmpDiagnosisStage;
            _tmpDiagnosisStage = _cursor.getString(_cursorIndexOfDiagnosisStage);
            final String _tmpMedications;
            _tmpMedications = _cursor.getString(_cursorIndexOfMedications);
            final String _tmpMobilityAids;
            _tmpMobilityAids = _cursor.getString(_cursorIndexOfMobilityAids);
            final boolean _tmpDominantHandDisability;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfDominantHandDisability);
            _tmpDominantHandDisability = _tmp != 0;
            final boolean _tmpVisionImpairment;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfVisionImpairment);
            _tmpVisionImpairment = _tmp_1 != 0;
            final boolean _tmpHearingImpairment;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfHearingImpairment);
            _tmpHearingImpairment = _tmp_2 != 0;
            final String _tmpActiveModules;
            _tmpActiveModules = _cursor.getString(_cursorIndexOfActiveModules);
            final int _tmpBaselinePhaseDays;
            _tmpBaselinePhaseDays = _cursor.getInt(_cursorIndexOfBaselinePhaseDays);
            final String _tmpClinicianId;
            _tmpClinicianId = _cursor.getString(_cursorIndexOfClinicianId);
            final String _tmpSupabaseUserId;
            _tmpSupabaseUserId = _cursor.getString(_cursorIndexOfSupabaseUserId);
            _result = new PatientProfile(_tmpPatientId,_tmpEnrollmentCode,_tmpEnrolledAtMs,_tmpYearOfBirth,_tmpBiologicalSex,_tmpDominantHand,_tmpPrimaryDiagnosis,_tmpSecondaryDiagnoses,_tmpDiagnosisStage,_tmpMedications,_tmpMobilityAids,_tmpDominantHandDisability,_tmpVisionImpairment,_tmpHearingImpairment,_tmpActiveModules,_tmpBaselinePhaseDays,_tmpClinicianId,_tmpSupabaseUserId);
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
