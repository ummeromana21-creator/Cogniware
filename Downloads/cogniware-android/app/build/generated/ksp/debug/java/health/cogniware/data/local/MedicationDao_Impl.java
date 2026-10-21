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
import health.cogniware.data.models.DoseEvent;
import health.cogniware.data.models.MedicationSchedule;
import java.lang.Class;
import java.lang.Double;
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

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class MedicationDao_Impl implements MedicationDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<MedicationSchedule> __insertionAdapterOfMedicationSchedule;

  private final EntityInsertionAdapter<DoseEvent> __insertionAdapterOfDoseEvent;

  private final SharedSQLiteStatement __preparedStmtOfMarkSynced;

  private final SharedSQLiteStatement __preparedStmtOfDeleteSchedule;

  public MedicationDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMedicationSchedule = new EntityInsertionAdapter<MedicationSchedule>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `medication_schedules` (`scheduleId`,`patientId`,`drugName`,`doseMg`,`frequencyPerDay`,`doseTimesJson`,`conditionTarget`,`medicationModel`,`expectedOnsetMinutes`,`expectedDurationMinutes`,`notes`,`active`,`createdAtMs`,`syncedToCloud`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MedicationSchedule entity) {
        statement.bindString(1, entity.getScheduleId());
        statement.bindString(2, entity.getPatientId());
        statement.bindString(3, entity.getDrugName());
        statement.bindDouble(4, entity.getDoseMg());
        statement.bindLong(5, entity.getFrequencyPerDay());
        statement.bindString(6, entity.getDoseTimesJson());
        statement.bindString(7, entity.getConditionTarget());
        statement.bindString(8, entity.getMedicationModel());
        statement.bindLong(9, entity.getExpectedOnsetMinutes());
        statement.bindLong(10, entity.getExpectedDurationMinutes());
        statement.bindString(11, entity.getNotes());
        final int _tmp = entity.getActive() ? 1 : 0;
        statement.bindLong(12, _tmp);
        statement.bindLong(13, entity.getCreatedAtMs());
        final int _tmp_1 = entity.getSyncedToCloud() ? 1 : 0;
        statement.bindLong(14, _tmp_1);
      }
    };
    this.__insertionAdapterOfDoseEvent = new EntityInsertionAdapter<DoseEvent>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `dose_events` (`eventId`,`patientId`,`scheduleId`,`drugName`,`doseMg`,`takenAtMs`,`loggedAtMs`,`logMethod`,`hrvAtDoseMs`,`hrAtDoseBpm`,`predictedWearOffMs`,`actualWearOffMs`,`syncedToCloud`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final DoseEvent entity) {
        statement.bindString(1, entity.getEventId());
        statement.bindString(2, entity.getPatientId());
        statement.bindString(3, entity.getScheduleId());
        statement.bindString(4, entity.getDrugName());
        statement.bindDouble(5, entity.getDoseMg());
        statement.bindLong(6, entity.getTakenAtMs());
        statement.bindLong(7, entity.getLoggedAtMs());
        statement.bindString(8, entity.getLogMethod());
        if (entity.getHrvAtDoseMs() == null) {
          statement.bindNull(9);
        } else {
          statement.bindDouble(9, entity.getHrvAtDoseMs());
        }
        if (entity.getHrAtDoseBpm() == null) {
          statement.bindNull(10);
        } else {
          statement.bindDouble(10, entity.getHrAtDoseBpm());
        }
        if (entity.getPredictedWearOffMs() == null) {
          statement.bindNull(11);
        } else {
          statement.bindLong(11, entity.getPredictedWearOffMs());
        }
        if (entity.getActualWearOffMs() == null) {
          statement.bindNull(12);
        } else {
          statement.bindLong(12, entity.getActualWearOffMs());
        }
        final int _tmp = entity.getSyncedToCloud() ? 1 : 0;
        statement.bindLong(13, _tmp);
      }
    };
    this.__preparedStmtOfMarkSynced = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE dose_events SET syncedToCloud = 1 WHERE eventId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteSchedule = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE medication_schedules SET active = 0 WHERE scheduleId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertSchedule(final MedicationSchedule schedule,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfMedicationSchedule.insert(schedule);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertDoseEvent(final DoseEvent event,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfDoseEvent.insert(event);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object markSynced(final String eventId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkSynced.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, eventId);
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
          __preparedStmtOfMarkSynced.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteSchedule(final String scheduleId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteSchedule.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, scheduleId);
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
          __preparedStmtOfDeleteSchedule.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getActiveSchedules(final String patientId,
      final Continuation<? super List<MedicationSchedule>> $completion) {
    final String _sql = "SELECT * FROM medication_schedules WHERE patientId = ? AND active = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, patientId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<MedicationSchedule>>() {
      @Override
      @NonNull
      public List<MedicationSchedule> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfScheduleId = CursorUtil.getColumnIndexOrThrow(_cursor, "scheduleId");
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfDrugName = CursorUtil.getColumnIndexOrThrow(_cursor, "drugName");
          final int _cursorIndexOfDoseMg = CursorUtil.getColumnIndexOrThrow(_cursor, "doseMg");
          final int _cursorIndexOfFrequencyPerDay = CursorUtil.getColumnIndexOrThrow(_cursor, "frequencyPerDay");
          final int _cursorIndexOfDoseTimesJson = CursorUtil.getColumnIndexOrThrow(_cursor, "doseTimesJson");
          final int _cursorIndexOfConditionTarget = CursorUtil.getColumnIndexOrThrow(_cursor, "conditionTarget");
          final int _cursorIndexOfMedicationModel = CursorUtil.getColumnIndexOrThrow(_cursor, "medicationModel");
          final int _cursorIndexOfExpectedOnsetMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "expectedOnsetMinutes");
          final int _cursorIndexOfExpectedDurationMinutes = CursorUtil.getColumnIndexOrThrow(_cursor, "expectedDurationMinutes");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final int _cursorIndexOfActive = CursorUtil.getColumnIndexOrThrow(_cursor, "active");
          final int _cursorIndexOfCreatedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAtMs");
          final int _cursorIndexOfSyncedToCloud = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedToCloud");
          final List<MedicationSchedule> _result = new ArrayList<MedicationSchedule>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MedicationSchedule _item;
            final String _tmpScheduleId;
            _tmpScheduleId = _cursor.getString(_cursorIndexOfScheduleId);
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final String _tmpDrugName;
            _tmpDrugName = _cursor.getString(_cursorIndexOfDrugName);
            final double _tmpDoseMg;
            _tmpDoseMg = _cursor.getDouble(_cursorIndexOfDoseMg);
            final int _tmpFrequencyPerDay;
            _tmpFrequencyPerDay = _cursor.getInt(_cursorIndexOfFrequencyPerDay);
            final String _tmpDoseTimesJson;
            _tmpDoseTimesJson = _cursor.getString(_cursorIndexOfDoseTimesJson);
            final String _tmpConditionTarget;
            _tmpConditionTarget = _cursor.getString(_cursorIndexOfConditionTarget);
            final String _tmpMedicationModel;
            _tmpMedicationModel = _cursor.getString(_cursorIndexOfMedicationModel);
            final int _tmpExpectedOnsetMinutes;
            _tmpExpectedOnsetMinutes = _cursor.getInt(_cursorIndexOfExpectedOnsetMinutes);
            final int _tmpExpectedDurationMinutes;
            _tmpExpectedDurationMinutes = _cursor.getInt(_cursorIndexOfExpectedDurationMinutes);
            final String _tmpNotes;
            _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            final boolean _tmpActive;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfActive);
            _tmpActive = _tmp != 0;
            final long _tmpCreatedAtMs;
            _tmpCreatedAtMs = _cursor.getLong(_cursorIndexOfCreatedAtMs);
            final boolean _tmpSyncedToCloud;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfSyncedToCloud);
            _tmpSyncedToCloud = _tmp_1 != 0;
            _item = new MedicationSchedule(_tmpScheduleId,_tmpPatientId,_tmpDrugName,_tmpDoseMg,_tmpFrequencyPerDay,_tmpDoseTimesJson,_tmpConditionTarget,_tmpMedicationModel,_tmpExpectedOnsetMinutes,_tmpExpectedDurationMinutes,_tmpNotes,_tmpActive,_tmpCreatedAtMs,_tmpSyncedToCloud);
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
  public Object getRecentDoseEvents(final String patientId, final long sinceMs,
      final Continuation<? super List<DoseEvent>> $completion) {
    final String _sql = "SELECT * FROM dose_events WHERE patientId = ? AND takenAtMs >= ? ORDER BY takenAtMs DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, patientId);
    _argIndex = 2;
    _statement.bindLong(_argIndex, sinceMs);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<DoseEvent>>() {
      @Override
      @NonNull
      public List<DoseEvent> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfEventId = CursorUtil.getColumnIndexOrThrow(_cursor, "eventId");
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfScheduleId = CursorUtil.getColumnIndexOrThrow(_cursor, "scheduleId");
          final int _cursorIndexOfDrugName = CursorUtil.getColumnIndexOrThrow(_cursor, "drugName");
          final int _cursorIndexOfDoseMg = CursorUtil.getColumnIndexOrThrow(_cursor, "doseMg");
          final int _cursorIndexOfTakenAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "takenAtMs");
          final int _cursorIndexOfLoggedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "loggedAtMs");
          final int _cursorIndexOfLogMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "logMethod");
          final int _cursorIndexOfHrvAtDoseMs = CursorUtil.getColumnIndexOrThrow(_cursor, "hrvAtDoseMs");
          final int _cursorIndexOfHrAtDoseBpm = CursorUtil.getColumnIndexOrThrow(_cursor, "hrAtDoseBpm");
          final int _cursorIndexOfPredictedWearOffMs = CursorUtil.getColumnIndexOrThrow(_cursor, "predictedWearOffMs");
          final int _cursorIndexOfActualWearOffMs = CursorUtil.getColumnIndexOrThrow(_cursor, "actualWearOffMs");
          final int _cursorIndexOfSyncedToCloud = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedToCloud");
          final List<DoseEvent> _result = new ArrayList<DoseEvent>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DoseEvent _item;
            final String _tmpEventId;
            _tmpEventId = _cursor.getString(_cursorIndexOfEventId);
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final String _tmpScheduleId;
            _tmpScheduleId = _cursor.getString(_cursorIndexOfScheduleId);
            final String _tmpDrugName;
            _tmpDrugName = _cursor.getString(_cursorIndexOfDrugName);
            final double _tmpDoseMg;
            _tmpDoseMg = _cursor.getDouble(_cursorIndexOfDoseMg);
            final long _tmpTakenAtMs;
            _tmpTakenAtMs = _cursor.getLong(_cursorIndexOfTakenAtMs);
            final long _tmpLoggedAtMs;
            _tmpLoggedAtMs = _cursor.getLong(_cursorIndexOfLoggedAtMs);
            final String _tmpLogMethod;
            _tmpLogMethod = _cursor.getString(_cursorIndexOfLogMethod);
            final Double _tmpHrvAtDoseMs;
            if (_cursor.isNull(_cursorIndexOfHrvAtDoseMs)) {
              _tmpHrvAtDoseMs = null;
            } else {
              _tmpHrvAtDoseMs = _cursor.getDouble(_cursorIndexOfHrvAtDoseMs);
            }
            final Double _tmpHrAtDoseBpm;
            if (_cursor.isNull(_cursorIndexOfHrAtDoseBpm)) {
              _tmpHrAtDoseBpm = null;
            } else {
              _tmpHrAtDoseBpm = _cursor.getDouble(_cursorIndexOfHrAtDoseBpm);
            }
            final Long _tmpPredictedWearOffMs;
            if (_cursor.isNull(_cursorIndexOfPredictedWearOffMs)) {
              _tmpPredictedWearOffMs = null;
            } else {
              _tmpPredictedWearOffMs = _cursor.getLong(_cursorIndexOfPredictedWearOffMs);
            }
            final Long _tmpActualWearOffMs;
            if (_cursor.isNull(_cursorIndexOfActualWearOffMs)) {
              _tmpActualWearOffMs = null;
            } else {
              _tmpActualWearOffMs = _cursor.getLong(_cursorIndexOfActualWearOffMs);
            }
            final boolean _tmpSyncedToCloud;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSyncedToCloud);
            _tmpSyncedToCloud = _tmp != 0;
            _item = new DoseEvent(_tmpEventId,_tmpPatientId,_tmpScheduleId,_tmpDrugName,_tmpDoseMg,_tmpTakenAtMs,_tmpLoggedAtMs,_tmpLogMethod,_tmpHrvAtDoseMs,_tmpHrAtDoseBpm,_tmpPredictedWearOffMs,_tmpActualWearOffMs,_tmpSyncedToCloud);
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
  public Object getLastDoseEvent(final String patientId,
      final Continuation<? super DoseEvent> $completion) {
    final String _sql = "SELECT * FROM dose_events WHERE patientId = ? ORDER BY takenAtMs DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, patientId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<DoseEvent>() {
      @Override
      @Nullable
      public DoseEvent call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfEventId = CursorUtil.getColumnIndexOrThrow(_cursor, "eventId");
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfScheduleId = CursorUtil.getColumnIndexOrThrow(_cursor, "scheduleId");
          final int _cursorIndexOfDrugName = CursorUtil.getColumnIndexOrThrow(_cursor, "drugName");
          final int _cursorIndexOfDoseMg = CursorUtil.getColumnIndexOrThrow(_cursor, "doseMg");
          final int _cursorIndexOfTakenAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "takenAtMs");
          final int _cursorIndexOfLoggedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "loggedAtMs");
          final int _cursorIndexOfLogMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "logMethod");
          final int _cursorIndexOfHrvAtDoseMs = CursorUtil.getColumnIndexOrThrow(_cursor, "hrvAtDoseMs");
          final int _cursorIndexOfHrAtDoseBpm = CursorUtil.getColumnIndexOrThrow(_cursor, "hrAtDoseBpm");
          final int _cursorIndexOfPredictedWearOffMs = CursorUtil.getColumnIndexOrThrow(_cursor, "predictedWearOffMs");
          final int _cursorIndexOfActualWearOffMs = CursorUtil.getColumnIndexOrThrow(_cursor, "actualWearOffMs");
          final int _cursorIndexOfSyncedToCloud = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedToCloud");
          final DoseEvent _result;
          if (_cursor.moveToFirst()) {
            final String _tmpEventId;
            _tmpEventId = _cursor.getString(_cursorIndexOfEventId);
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final String _tmpScheduleId;
            _tmpScheduleId = _cursor.getString(_cursorIndexOfScheduleId);
            final String _tmpDrugName;
            _tmpDrugName = _cursor.getString(_cursorIndexOfDrugName);
            final double _tmpDoseMg;
            _tmpDoseMg = _cursor.getDouble(_cursorIndexOfDoseMg);
            final long _tmpTakenAtMs;
            _tmpTakenAtMs = _cursor.getLong(_cursorIndexOfTakenAtMs);
            final long _tmpLoggedAtMs;
            _tmpLoggedAtMs = _cursor.getLong(_cursorIndexOfLoggedAtMs);
            final String _tmpLogMethod;
            _tmpLogMethod = _cursor.getString(_cursorIndexOfLogMethod);
            final Double _tmpHrvAtDoseMs;
            if (_cursor.isNull(_cursorIndexOfHrvAtDoseMs)) {
              _tmpHrvAtDoseMs = null;
            } else {
              _tmpHrvAtDoseMs = _cursor.getDouble(_cursorIndexOfHrvAtDoseMs);
            }
            final Double _tmpHrAtDoseBpm;
            if (_cursor.isNull(_cursorIndexOfHrAtDoseBpm)) {
              _tmpHrAtDoseBpm = null;
            } else {
              _tmpHrAtDoseBpm = _cursor.getDouble(_cursorIndexOfHrAtDoseBpm);
            }
            final Long _tmpPredictedWearOffMs;
            if (_cursor.isNull(_cursorIndexOfPredictedWearOffMs)) {
              _tmpPredictedWearOffMs = null;
            } else {
              _tmpPredictedWearOffMs = _cursor.getLong(_cursorIndexOfPredictedWearOffMs);
            }
            final Long _tmpActualWearOffMs;
            if (_cursor.isNull(_cursorIndexOfActualWearOffMs)) {
              _tmpActualWearOffMs = null;
            } else {
              _tmpActualWearOffMs = _cursor.getLong(_cursorIndexOfActualWearOffMs);
            }
            final boolean _tmpSyncedToCloud;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSyncedToCloud);
            _tmpSyncedToCloud = _tmp != 0;
            _result = new DoseEvent(_tmpEventId,_tmpPatientId,_tmpScheduleId,_tmpDrugName,_tmpDoseMg,_tmpTakenAtMs,_tmpLoggedAtMs,_tmpLogMethod,_tmpHrvAtDoseMs,_tmpHrAtDoseBpm,_tmpPredictedWearOffMs,_tmpActualWearOffMs,_tmpSyncedToCloud);
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
  public Object getLastDoseForDrug(final String patientId, final String drugName,
      final Continuation<? super DoseEvent> $completion) {
    final String _sql = "SELECT * FROM dose_events WHERE patientId = ? AND drugName = ? ORDER BY takenAtMs DESC LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, patientId);
    _argIndex = 2;
    _statement.bindString(_argIndex, drugName);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<DoseEvent>() {
      @Override
      @Nullable
      public DoseEvent call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfEventId = CursorUtil.getColumnIndexOrThrow(_cursor, "eventId");
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfScheduleId = CursorUtil.getColumnIndexOrThrow(_cursor, "scheduleId");
          final int _cursorIndexOfDrugName = CursorUtil.getColumnIndexOrThrow(_cursor, "drugName");
          final int _cursorIndexOfDoseMg = CursorUtil.getColumnIndexOrThrow(_cursor, "doseMg");
          final int _cursorIndexOfTakenAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "takenAtMs");
          final int _cursorIndexOfLoggedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "loggedAtMs");
          final int _cursorIndexOfLogMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "logMethod");
          final int _cursorIndexOfHrvAtDoseMs = CursorUtil.getColumnIndexOrThrow(_cursor, "hrvAtDoseMs");
          final int _cursorIndexOfHrAtDoseBpm = CursorUtil.getColumnIndexOrThrow(_cursor, "hrAtDoseBpm");
          final int _cursorIndexOfPredictedWearOffMs = CursorUtil.getColumnIndexOrThrow(_cursor, "predictedWearOffMs");
          final int _cursorIndexOfActualWearOffMs = CursorUtil.getColumnIndexOrThrow(_cursor, "actualWearOffMs");
          final int _cursorIndexOfSyncedToCloud = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedToCloud");
          final DoseEvent _result;
          if (_cursor.moveToFirst()) {
            final String _tmpEventId;
            _tmpEventId = _cursor.getString(_cursorIndexOfEventId);
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final String _tmpScheduleId;
            _tmpScheduleId = _cursor.getString(_cursorIndexOfScheduleId);
            final String _tmpDrugName;
            _tmpDrugName = _cursor.getString(_cursorIndexOfDrugName);
            final double _tmpDoseMg;
            _tmpDoseMg = _cursor.getDouble(_cursorIndexOfDoseMg);
            final long _tmpTakenAtMs;
            _tmpTakenAtMs = _cursor.getLong(_cursorIndexOfTakenAtMs);
            final long _tmpLoggedAtMs;
            _tmpLoggedAtMs = _cursor.getLong(_cursorIndexOfLoggedAtMs);
            final String _tmpLogMethod;
            _tmpLogMethod = _cursor.getString(_cursorIndexOfLogMethod);
            final Double _tmpHrvAtDoseMs;
            if (_cursor.isNull(_cursorIndexOfHrvAtDoseMs)) {
              _tmpHrvAtDoseMs = null;
            } else {
              _tmpHrvAtDoseMs = _cursor.getDouble(_cursorIndexOfHrvAtDoseMs);
            }
            final Double _tmpHrAtDoseBpm;
            if (_cursor.isNull(_cursorIndexOfHrAtDoseBpm)) {
              _tmpHrAtDoseBpm = null;
            } else {
              _tmpHrAtDoseBpm = _cursor.getDouble(_cursorIndexOfHrAtDoseBpm);
            }
            final Long _tmpPredictedWearOffMs;
            if (_cursor.isNull(_cursorIndexOfPredictedWearOffMs)) {
              _tmpPredictedWearOffMs = null;
            } else {
              _tmpPredictedWearOffMs = _cursor.getLong(_cursorIndexOfPredictedWearOffMs);
            }
            final Long _tmpActualWearOffMs;
            if (_cursor.isNull(_cursorIndexOfActualWearOffMs)) {
              _tmpActualWearOffMs = null;
            } else {
              _tmpActualWearOffMs = _cursor.getLong(_cursorIndexOfActualWearOffMs);
            }
            final boolean _tmpSyncedToCloud;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSyncedToCloud);
            _tmpSyncedToCloud = _tmp != 0;
            _result = new DoseEvent(_tmpEventId,_tmpPatientId,_tmpScheduleId,_tmpDrugName,_tmpDoseMg,_tmpTakenAtMs,_tmpLoggedAtMs,_tmpLogMethod,_tmpHrvAtDoseMs,_tmpHrAtDoseBpm,_tmpPredictedWearOffMs,_tmpActualWearOffMs,_tmpSyncedToCloud);
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
  public Object getPendingSync(final Continuation<? super List<DoseEvent>> $completion) {
    final String _sql = "SELECT * FROM dose_events WHERE syncedToCloud = 0";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<DoseEvent>>() {
      @Override
      @NonNull
      public List<DoseEvent> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfEventId = CursorUtil.getColumnIndexOrThrow(_cursor, "eventId");
          final int _cursorIndexOfPatientId = CursorUtil.getColumnIndexOrThrow(_cursor, "patientId");
          final int _cursorIndexOfScheduleId = CursorUtil.getColumnIndexOrThrow(_cursor, "scheduleId");
          final int _cursorIndexOfDrugName = CursorUtil.getColumnIndexOrThrow(_cursor, "drugName");
          final int _cursorIndexOfDoseMg = CursorUtil.getColumnIndexOrThrow(_cursor, "doseMg");
          final int _cursorIndexOfTakenAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "takenAtMs");
          final int _cursorIndexOfLoggedAtMs = CursorUtil.getColumnIndexOrThrow(_cursor, "loggedAtMs");
          final int _cursorIndexOfLogMethod = CursorUtil.getColumnIndexOrThrow(_cursor, "logMethod");
          final int _cursorIndexOfHrvAtDoseMs = CursorUtil.getColumnIndexOrThrow(_cursor, "hrvAtDoseMs");
          final int _cursorIndexOfHrAtDoseBpm = CursorUtil.getColumnIndexOrThrow(_cursor, "hrAtDoseBpm");
          final int _cursorIndexOfPredictedWearOffMs = CursorUtil.getColumnIndexOrThrow(_cursor, "predictedWearOffMs");
          final int _cursorIndexOfActualWearOffMs = CursorUtil.getColumnIndexOrThrow(_cursor, "actualWearOffMs");
          final int _cursorIndexOfSyncedToCloud = CursorUtil.getColumnIndexOrThrow(_cursor, "syncedToCloud");
          final List<DoseEvent> _result = new ArrayList<DoseEvent>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final DoseEvent _item;
            final String _tmpEventId;
            _tmpEventId = _cursor.getString(_cursorIndexOfEventId);
            final String _tmpPatientId;
            _tmpPatientId = _cursor.getString(_cursorIndexOfPatientId);
            final String _tmpScheduleId;
            _tmpScheduleId = _cursor.getString(_cursorIndexOfScheduleId);
            final String _tmpDrugName;
            _tmpDrugName = _cursor.getString(_cursorIndexOfDrugName);
            final double _tmpDoseMg;
            _tmpDoseMg = _cursor.getDouble(_cursorIndexOfDoseMg);
            final long _tmpTakenAtMs;
            _tmpTakenAtMs = _cursor.getLong(_cursorIndexOfTakenAtMs);
            final long _tmpLoggedAtMs;
            _tmpLoggedAtMs = _cursor.getLong(_cursorIndexOfLoggedAtMs);
            final String _tmpLogMethod;
            _tmpLogMethod = _cursor.getString(_cursorIndexOfLogMethod);
            final Double _tmpHrvAtDoseMs;
            if (_cursor.isNull(_cursorIndexOfHrvAtDoseMs)) {
              _tmpHrvAtDoseMs = null;
            } else {
              _tmpHrvAtDoseMs = _cursor.getDouble(_cursorIndexOfHrvAtDoseMs);
            }
            final Double _tmpHrAtDoseBpm;
            if (_cursor.isNull(_cursorIndexOfHrAtDoseBpm)) {
              _tmpHrAtDoseBpm = null;
            } else {
              _tmpHrAtDoseBpm = _cursor.getDouble(_cursorIndexOfHrAtDoseBpm);
            }
            final Long _tmpPredictedWearOffMs;
            if (_cursor.isNull(_cursorIndexOfPredictedWearOffMs)) {
              _tmpPredictedWearOffMs = null;
            } else {
              _tmpPredictedWearOffMs = _cursor.getLong(_cursorIndexOfPredictedWearOffMs);
            }
            final Long _tmpActualWearOffMs;
            if (_cursor.isNull(_cursorIndexOfActualWearOffMs)) {
              _tmpActualWearOffMs = null;
            } else {
              _tmpActualWearOffMs = _cursor.getLong(_cursorIndexOfActualWearOffMs);
            }
            final boolean _tmpSyncedToCloud;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfSyncedToCloud);
            _tmpSyncedToCloud = _tmp != 0;
            _item = new DoseEvent(_tmpEventId,_tmpPatientId,_tmpScheduleId,_tmpDrugName,_tmpDoseMg,_tmpTakenAtMs,_tmpLoggedAtMs,_tmpLogMethod,_tmpHrvAtDoseMs,_tmpHrAtDoseBpm,_tmpPredictedWearOffMs,_tmpActualWearOffMs,_tmpSyncedToCloud);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
