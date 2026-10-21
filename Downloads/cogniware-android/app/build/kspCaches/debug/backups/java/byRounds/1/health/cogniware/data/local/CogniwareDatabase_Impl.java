package health.cogniware.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class CogniwareDatabase_Impl extends CogniwareDatabase {
  private volatile KeystrokeDao _keystrokeDao;

  private volatile TremorDao _tremorDao;

  private volatile GaitDao _gaitDao;

  private volatile HealthDao _healthDao;

  private volatile ProfileDao _profileDao;

  private volatile MedicationDao _medicationDao;

  private volatile DiagnosisProfileDao _diagnosisProfileDao;

  private volatile PostureBaselineDao _postureBaselineDao;

  private volatile AssessmentSessionDao _assessmentSessionDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(5) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `keystroke_sessions` (`sessionId` TEXT NOT NULL, `patientId` TEXT NOT NULL, `startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `ikiMeanMs` REAL NOT NULL, `ikiCvPercent` REAL NOT NULL, `ikiP10Ms` REAL NOT NULL, `ikiP90Ms` REAL NOT NULL, `flightTimeMeanMs` REAL NOT NULL, `flightTimeCvPercent` REAL NOT NULL, `backspaceRatePercent` REAL NOT NULL, `correctionBurstCount` INTEGER NOT NULL, `wordBoundaryPauseMeanMs` REAL NOT NULL, `withinWordPauseMeanMs` REAL NOT NULL, `longPauseCount` INTEGER NOT NULL, `estimatedWpm` REAL NOT NULL, `totalKeystrokes` INTEGER NOT NULL, `context` TEXT NOT NULL, `syncedToCloud` INTEGER NOT NULL, PRIMARY KEY(`sessionId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `tremor_readings` (`readingId` TEXT NOT NULL, `patientId` TEXT NOT NULL, `timestampMs` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `peakFrequencyHz` REAL NOT NULL, `powerAtPeakDb` REAL NOT NULL, `power4to7Hz` REAL NOT NULL, `power4to12Hz` REAL NOT NULL, `power2to4Hz` REAL NOT NULL, `totalPowerDb` REAL NOT NULL, `rmsAmplitude` REAL NOT NULL, `laterality` TEXT NOT NULL, `tremorBandHint` TEXT NOT NULL, `context` TEXT NOT NULL, `syncedToCloud` INTEGER NOT NULL, PRIMARY KEY(`readingId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `gait_sessions` (`sessionId` TEXT NOT NULL, `patientId` TEXT NOT NULL, `startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `stepCount` INTEGER NOT NULL, `cadenceStepsPerMin` REAL NOT NULL, `strideRegularityIndex` REAL NOT NULL, `stepAsymmetryIndex` REAL NOT NULL, `walkingSpeedProxy` REAL NOT NULL, `verticalImpactG` REAL NOT NULL, `context` TEXT NOT NULL, `syncedToCloud` INTEGER NOT NULL, PRIMARY KEY(`sessionId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `structural_baseline` (`patientId` TEXT NOT NULL, `capturedAtMs` INTEGER NOT NULL, `forwardHeadPostureDeg` REAL NOT NULL, `shoulderAsymmetryDeg` REAL NOT NULL, `spinalLateralCurvatureDeg` REAL NOT NULL, `leftArmLengthRatio` REAL NOT NULL, `rightArmLengthRatio` REAL NOT NULL, `leftLegLengthRatio` REAL NOT NULL, `rightLegLengthRatio` REAL NOT NULL, `hipLevelAsymmetryDeg` REAL NOT NULL, `kneeValgusAngleLeftDeg` REAL NOT NULL, `kneeValgusAngleRightDeg` REAL NOT NULL, `postureSymmetryScore` REAL NOT NULL, `captureQualityScore` REAL NOT NULL, `captureNotes` TEXT NOT NULL, PRIMARY KEY(`patientId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `health_daily` (`dateKey` TEXT NOT NULL, `patientId` TEXT NOT NULL, `restingHeartRateBpm` REAL, `heartRateMeanBpm` REAL, `heartRateMinBpm` REAL, `heartRateMaxBpm` REAL, `hrvRmssdMs` REAL, `sleepTotalMinutes` INTEGER, `sleepDeepMinutes` INTEGER, `sleepRemMinutes` INTEGER, `sleepLightMinutes` INTEGER, `sleepAwakeMinutes` INTEGER, `sleepOnsetMs` INTEGER, `wakeTimeMs` INTEGER, `stepCount` INTEGER, `activeCaloriesBurned` REAL, `totalCaloriesBurned` REAL, `activeMinutes` INTEGER, `exerciseSessionMinutes` INTEGER, `spO2MeanPercent` REAL, `spO2MinPercent` REAL, `syncedToCloud` INTEGER NOT NULL, PRIMARY KEY(`dateKey`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `patient_profile` (`patientId` TEXT NOT NULL, `enrollmentCode` TEXT NOT NULL, `enrolledAtMs` INTEGER NOT NULL, `yearOfBirth` INTEGER NOT NULL, `biologicalSex` TEXT NOT NULL, `dominantHand` TEXT NOT NULL, `primaryDiagnosis` TEXT NOT NULL, `secondaryDiagnoses` TEXT NOT NULL, `diagnosisStage` TEXT NOT NULL, `medications` TEXT NOT NULL, `mobilityAids` TEXT NOT NULL, `dominantHandDisability` INTEGER NOT NULL, `visionImpairment` INTEGER NOT NULL, `hearingImpairment` INTEGER NOT NULL, `activeModules` TEXT NOT NULL, `baselinePhaseDays` INTEGER NOT NULL, `clinicianId` TEXT NOT NULL, `supabaseUserId` TEXT NOT NULL, PRIMARY KEY(`patientId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `medication_schedules` (`scheduleId` TEXT NOT NULL, `patientId` TEXT NOT NULL, `drugName` TEXT NOT NULL, `doseMg` REAL NOT NULL, `frequencyPerDay` INTEGER NOT NULL, `doseTimesJson` TEXT NOT NULL, `conditionTarget` TEXT NOT NULL, `medicationModel` TEXT NOT NULL, `expectedOnsetMinutes` INTEGER NOT NULL, `expectedDurationMinutes` INTEGER NOT NULL, `notes` TEXT NOT NULL, `active` INTEGER NOT NULL, `createdAtMs` INTEGER NOT NULL, `syncedToCloud` INTEGER NOT NULL, PRIMARY KEY(`scheduleId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `dose_events` (`eventId` TEXT NOT NULL, `patientId` TEXT NOT NULL, `scheduleId` TEXT NOT NULL, `drugName` TEXT NOT NULL, `doseMg` REAL NOT NULL, `takenAtMs` INTEGER NOT NULL, `loggedAtMs` INTEGER NOT NULL, `logMethod` TEXT NOT NULL, `hrvAtDoseMs` REAL, `hrAtDoseBpm` REAL, `predictedWearOffMs` INTEGER, `actualWearOffMs` INTEGER, `syncedToCloud` INTEGER NOT NULL, PRIMARY KEY(`eventId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `diagnosis_profiles` (`profileId` TEXT NOT NULL, `patientId` TEXT NOT NULL, `conditionType` TEXT NOT NULL, `ageGroup` TEXT NOT NULL, `subtype` TEXT, `diagnosisDateMs` INTEGER, `caregiverId` TEXT, `notifyTarget` TEXT NOT NULL, `anomalyModel` TEXT NOT NULL, `medicationModel` TEXT NOT NULL, `riskWeightsJson` TEXT NOT NULL, `createdAtMs` INTEGER NOT NULL, `syncedToCloud` INTEGER NOT NULL, PRIMARY KEY(`profileId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `posture_baselines` (`baselineId` TEXT NOT NULL, `patientId` TEXT NOT NULL, `capturedAtMs` INTEGER NOT NULL, `sessionType` TEXT NOT NULL, `armSwingAsymmetryIndex` REAL NOT NULL, `gaitInitiationDelayMs` REAL NOT NULL, `trunkForwardLeanDeg` REAL NOT NULL, `stepSymmetryIndex` REAL NOT NULL, `cadenceStepsPerMin` REAL NOT NULL, `landmarkSeriesJson` TEXT NOT NULL, `qualityScore` REAL NOT NULL, `flaggedIssues` TEXT NOT NULL, `syncedToCloud` INTEGER NOT NULL, PRIMARY KEY(`baselineId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `assessment_sessions` (`sessionId` TEXT NOT NULL, `patientId` TEXT NOT NULL, `scheduledMs` INTEGER NOT NULL, `completedMs` INTEGER NOT NULL, `sessionType` TEXT NOT NULL, `postureBaselineId` TEXT NOT NULL, `rawVideoStoredRemotely` INTEGER NOT NULL, `armSwingDelta` REAL NOT NULL, `trunkLeanDelta` REAL NOT NULL, `gaitInitiationDelta` REAL NOT NULL, `flaggedIssues` TEXT NOT NULL, `syncedToCloud` INTEGER NOT NULL, PRIMARY KEY(`sessionId`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '04dcf540a9632d563edfdad4f459f928')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `keystroke_sessions`");
        db.execSQL("DROP TABLE IF EXISTS `tremor_readings`");
        db.execSQL("DROP TABLE IF EXISTS `gait_sessions`");
        db.execSQL("DROP TABLE IF EXISTS `structural_baseline`");
        db.execSQL("DROP TABLE IF EXISTS `health_daily`");
        db.execSQL("DROP TABLE IF EXISTS `patient_profile`");
        db.execSQL("DROP TABLE IF EXISTS `medication_schedules`");
        db.execSQL("DROP TABLE IF EXISTS `dose_events`");
        db.execSQL("DROP TABLE IF EXISTS `diagnosis_profiles`");
        db.execSQL("DROP TABLE IF EXISTS `posture_baselines`");
        db.execSQL("DROP TABLE IF EXISTS `assessment_sessions`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsKeystrokeSessions = new HashMap<String, TableInfo.Column>(19);
        _columnsKeystrokeSessions.put("sessionId", new TableInfo.Column("sessionId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeystrokeSessions.put("patientId", new TableInfo.Column("patientId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeystrokeSessions.put("startMs", new TableInfo.Column("startMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeystrokeSessions.put("endMs", new TableInfo.Column("endMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeystrokeSessions.put("ikiMeanMs", new TableInfo.Column("ikiMeanMs", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeystrokeSessions.put("ikiCvPercent", new TableInfo.Column("ikiCvPercent", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeystrokeSessions.put("ikiP10Ms", new TableInfo.Column("ikiP10Ms", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeystrokeSessions.put("ikiP90Ms", new TableInfo.Column("ikiP90Ms", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeystrokeSessions.put("flightTimeMeanMs", new TableInfo.Column("flightTimeMeanMs", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeystrokeSessions.put("flightTimeCvPercent", new TableInfo.Column("flightTimeCvPercent", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeystrokeSessions.put("backspaceRatePercent", new TableInfo.Column("backspaceRatePercent", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeystrokeSessions.put("correctionBurstCount", new TableInfo.Column("correctionBurstCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeystrokeSessions.put("wordBoundaryPauseMeanMs", new TableInfo.Column("wordBoundaryPauseMeanMs", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeystrokeSessions.put("withinWordPauseMeanMs", new TableInfo.Column("withinWordPauseMeanMs", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeystrokeSessions.put("longPauseCount", new TableInfo.Column("longPauseCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeystrokeSessions.put("estimatedWpm", new TableInfo.Column("estimatedWpm", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeystrokeSessions.put("totalKeystrokes", new TableInfo.Column("totalKeystrokes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeystrokeSessions.put("context", new TableInfo.Column("context", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsKeystrokeSessions.put("syncedToCloud", new TableInfo.Column("syncedToCloud", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysKeystrokeSessions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesKeystrokeSessions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoKeystrokeSessions = new TableInfo("keystroke_sessions", _columnsKeystrokeSessions, _foreignKeysKeystrokeSessions, _indicesKeystrokeSessions);
        final TableInfo _existingKeystrokeSessions = TableInfo.read(db, "keystroke_sessions");
        if (!_infoKeystrokeSessions.equals(_existingKeystrokeSessions)) {
          return new RoomOpenHelper.ValidationResult(false, "keystroke_sessions(health.cogniware.data.models.KeystrokeSession).\n"
                  + " Expected:\n" + _infoKeystrokeSessions + "\n"
                  + " Found:\n" + _existingKeystrokeSessions);
        }
        final HashMap<String, TableInfo.Column> _columnsTremorReadings = new HashMap<String, TableInfo.Column>(15);
        _columnsTremorReadings.put("readingId", new TableInfo.Column("readingId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTremorReadings.put("patientId", new TableInfo.Column("patientId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTremorReadings.put("timestampMs", new TableInfo.Column("timestampMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTremorReadings.put("durationMs", new TableInfo.Column("durationMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTremorReadings.put("peakFrequencyHz", new TableInfo.Column("peakFrequencyHz", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTremorReadings.put("powerAtPeakDb", new TableInfo.Column("powerAtPeakDb", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTremorReadings.put("power4to7Hz", new TableInfo.Column("power4to7Hz", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTremorReadings.put("power4to12Hz", new TableInfo.Column("power4to12Hz", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTremorReadings.put("power2to4Hz", new TableInfo.Column("power2to4Hz", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTremorReadings.put("totalPowerDb", new TableInfo.Column("totalPowerDb", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTremorReadings.put("rmsAmplitude", new TableInfo.Column("rmsAmplitude", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTremorReadings.put("laterality", new TableInfo.Column("laterality", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTremorReadings.put("tremorBandHint", new TableInfo.Column("tremorBandHint", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTremorReadings.put("context", new TableInfo.Column("context", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTremorReadings.put("syncedToCloud", new TableInfo.Column("syncedToCloud", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTremorReadings = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTremorReadings = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoTremorReadings = new TableInfo("tremor_readings", _columnsTremorReadings, _foreignKeysTremorReadings, _indicesTremorReadings);
        final TableInfo _existingTremorReadings = TableInfo.read(db, "tremor_readings");
        if (!_infoTremorReadings.equals(_existingTremorReadings)) {
          return new RoomOpenHelper.ValidationResult(false, "tremor_readings(health.cogniware.data.models.TremorReading).\n"
                  + " Expected:\n" + _infoTremorReadings + "\n"
                  + " Found:\n" + _existingTremorReadings);
        }
        final HashMap<String, TableInfo.Column> _columnsGaitSessions = new HashMap<String, TableInfo.Column>(12);
        _columnsGaitSessions.put("sessionId", new TableInfo.Column("sessionId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGaitSessions.put("patientId", new TableInfo.Column("patientId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGaitSessions.put("startMs", new TableInfo.Column("startMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGaitSessions.put("endMs", new TableInfo.Column("endMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGaitSessions.put("stepCount", new TableInfo.Column("stepCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGaitSessions.put("cadenceStepsPerMin", new TableInfo.Column("cadenceStepsPerMin", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGaitSessions.put("strideRegularityIndex", new TableInfo.Column("strideRegularityIndex", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGaitSessions.put("stepAsymmetryIndex", new TableInfo.Column("stepAsymmetryIndex", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGaitSessions.put("walkingSpeedProxy", new TableInfo.Column("walkingSpeedProxy", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGaitSessions.put("verticalImpactG", new TableInfo.Column("verticalImpactG", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGaitSessions.put("context", new TableInfo.Column("context", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGaitSessions.put("syncedToCloud", new TableInfo.Column("syncedToCloud", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysGaitSessions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesGaitSessions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoGaitSessions = new TableInfo("gait_sessions", _columnsGaitSessions, _foreignKeysGaitSessions, _indicesGaitSessions);
        final TableInfo _existingGaitSessions = TableInfo.read(db, "gait_sessions");
        if (!_infoGaitSessions.equals(_existingGaitSessions)) {
          return new RoomOpenHelper.ValidationResult(false, "gait_sessions(health.cogniware.data.models.GaitSession).\n"
                  + " Expected:\n" + _infoGaitSessions + "\n"
                  + " Found:\n" + _existingGaitSessions);
        }
        final HashMap<String, TableInfo.Column> _columnsStructuralBaseline = new HashMap<String, TableInfo.Column>(15);
        _columnsStructuralBaseline.put("patientId", new TableInfo.Column("patientId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStructuralBaseline.put("capturedAtMs", new TableInfo.Column("capturedAtMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStructuralBaseline.put("forwardHeadPostureDeg", new TableInfo.Column("forwardHeadPostureDeg", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStructuralBaseline.put("shoulderAsymmetryDeg", new TableInfo.Column("shoulderAsymmetryDeg", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStructuralBaseline.put("spinalLateralCurvatureDeg", new TableInfo.Column("spinalLateralCurvatureDeg", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStructuralBaseline.put("leftArmLengthRatio", new TableInfo.Column("leftArmLengthRatio", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStructuralBaseline.put("rightArmLengthRatio", new TableInfo.Column("rightArmLengthRatio", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStructuralBaseline.put("leftLegLengthRatio", new TableInfo.Column("leftLegLengthRatio", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStructuralBaseline.put("rightLegLengthRatio", new TableInfo.Column("rightLegLengthRatio", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStructuralBaseline.put("hipLevelAsymmetryDeg", new TableInfo.Column("hipLevelAsymmetryDeg", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStructuralBaseline.put("kneeValgusAngleLeftDeg", new TableInfo.Column("kneeValgusAngleLeftDeg", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStructuralBaseline.put("kneeValgusAngleRightDeg", new TableInfo.Column("kneeValgusAngleRightDeg", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStructuralBaseline.put("postureSymmetryScore", new TableInfo.Column("postureSymmetryScore", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStructuralBaseline.put("captureQualityScore", new TableInfo.Column("captureQualityScore", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsStructuralBaseline.put("captureNotes", new TableInfo.Column("captureNotes", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysStructuralBaseline = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesStructuralBaseline = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoStructuralBaseline = new TableInfo("structural_baseline", _columnsStructuralBaseline, _foreignKeysStructuralBaseline, _indicesStructuralBaseline);
        final TableInfo _existingStructuralBaseline = TableInfo.read(db, "structural_baseline");
        if (!_infoStructuralBaseline.equals(_existingStructuralBaseline)) {
          return new RoomOpenHelper.ValidationResult(false, "structural_baseline(health.cogniware.data.models.StructuralBaseline).\n"
                  + " Expected:\n" + _infoStructuralBaseline + "\n"
                  + " Found:\n" + _existingStructuralBaseline);
        }
        final HashMap<String, TableInfo.Column> _columnsHealthDaily = new HashMap<String, TableInfo.Column>(22);
        _columnsHealthDaily.put("dateKey", new TableInfo.Column("dateKey", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHealthDaily.put("patientId", new TableInfo.Column("patientId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHealthDaily.put("restingHeartRateBpm", new TableInfo.Column("restingHeartRateBpm", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHealthDaily.put("heartRateMeanBpm", new TableInfo.Column("heartRateMeanBpm", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHealthDaily.put("heartRateMinBpm", new TableInfo.Column("heartRateMinBpm", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHealthDaily.put("heartRateMaxBpm", new TableInfo.Column("heartRateMaxBpm", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHealthDaily.put("hrvRmssdMs", new TableInfo.Column("hrvRmssdMs", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHealthDaily.put("sleepTotalMinutes", new TableInfo.Column("sleepTotalMinutes", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHealthDaily.put("sleepDeepMinutes", new TableInfo.Column("sleepDeepMinutes", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHealthDaily.put("sleepRemMinutes", new TableInfo.Column("sleepRemMinutes", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHealthDaily.put("sleepLightMinutes", new TableInfo.Column("sleepLightMinutes", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHealthDaily.put("sleepAwakeMinutes", new TableInfo.Column("sleepAwakeMinutes", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHealthDaily.put("sleepOnsetMs", new TableInfo.Column("sleepOnsetMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHealthDaily.put("wakeTimeMs", new TableInfo.Column("wakeTimeMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHealthDaily.put("stepCount", new TableInfo.Column("stepCount", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHealthDaily.put("activeCaloriesBurned", new TableInfo.Column("activeCaloriesBurned", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHealthDaily.put("totalCaloriesBurned", new TableInfo.Column("totalCaloriesBurned", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHealthDaily.put("activeMinutes", new TableInfo.Column("activeMinutes", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHealthDaily.put("exerciseSessionMinutes", new TableInfo.Column("exerciseSessionMinutes", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHealthDaily.put("spO2MeanPercent", new TableInfo.Column("spO2MeanPercent", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHealthDaily.put("spO2MinPercent", new TableInfo.Column("spO2MinPercent", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsHealthDaily.put("syncedToCloud", new TableInfo.Column("syncedToCloud", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysHealthDaily = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesHealthDaily = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoHealthDaily = new TableInfo("health_daily", _columnsHealthDaily, _foreignKeysHealthDaily, _indicesHealthDaily);
        final TableInfo _existingHealthDaily = TableInfo.read(db, "health_daily");
        if (!_infoHealthDaily.equals(_existingHealthDaily)) {
          return new RoomOpenHelper.ValidationResult(false, "health_daily(health.cogniware.data.models.HealthDailySummary).\n"
                  + " Expected:\n" + _infoHealthDaily + "\n"
                  + " Found:\n" + _existingHealthDaily);
        }
        final HashMap<String, TableInfo.Column> _columnsPatientProfile = new HashMap<String, TableInfo.Column>(18);
        _columnsPatientProfile.put("patientId", new TableInfo.Column("patientId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPatientProfile.put("enrollmentCode", new TableInfo.Column("enrollmentCode", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPatientProfile.put("enrolledAtMs", new TableInfo.Column("enrolledAtMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPatientProfile.put("yearOfBirth", new TableInfo.Column("yearOfBirth", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPatientProfile.put("biologicalSex", new TableInfo.Column("biologicalSex", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPatientProfile.put("dominantHand", new TableInfo.Column("dominantHand", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPatientProfile.put("primaryDiagnosis", new TableInfo.Column("primaryDiagnosis", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPatientProfile.put("secondaryDiagnoses", new TableInfo.Column("secondaryDiagnoses", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPatientProfile.put("diagnosisStage", new TableInfo.Column("diagnosisStage", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPatientProfile.put("medications", new TableInfo.Column("medications", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPatientProfile.put("mobilityAids", new TableInfo.Column("mobilityAids", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPatientProfile.put("dominantHandDisability", new TableInfo.Column("dominantHandDisability", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPatientProfile.put("visionImpairment", new TableInfo.Column("visionImpairment", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPatientProfile.put("hearingImpairment", new TableInfo.Column("hearingImpairment", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPatientProfile.put("activeModules", new TableInfo.Column("activeModules", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPatientProfile.put("baselinePhaseDays", new TableInfo.Column("baselinePhaseDays", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPatientProfile.put("clinicianId", new TableInfo.Column("clinicianId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPatientProfile.put("supabaseUserId", new TableInfo.Column("supabaseUserId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPatientProfile = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPatientProfile = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPatientProfile = new TableInfo("patient_profile", _columnsPatientProfile, _foreignKeysPatientProfile, _indicesPatientProfile);
        final TableInfo _existingPatientProfile = TableInfo.read(db, "patient_profile");
        if (!_infoPatientProfile.equals(_existingPatientProfile)) {
          return new RoomOpenHelper.ValidationResult(false, "patient_profile(health.cogniware.data.models.PatientProfile).\n"
                  + " Expected:\n" + _infoPatientProfile + "\n"
                  + " Found:\n" + _existingPatientProfile);
        }
        final HashMap<String, TableInfo.Column> _columnsMedicationSchedules = new HashMap<String, TableInfo.Column>(14);
        _columnsMedicationSchedules.put("scheduleId", new TableInfo.Column("scheduleId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMedicationSchedules.put("patientId", new TableInfo.Column("patientId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMedicationSchedules.put("drugName", new TableInfo.Column("drugName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMedicationSchedules.put("doseMg", new TableInfo.Column("doseMg", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMedicationSchedules.put("frequencyPerDay", new TableInfo.Column("frequencyPerDay", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMedicationSchedules.put("doseTimesJson", new TableInfo.Column("doseTimesJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMedicationSchedules.put("conditionTarget", new TableInfo.Column("conditionTarget", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMedicationSchedules.put("medicationModel", new TableInfo.Column("medicationModel", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMedicationSchedules.put("expectedOnsetMinutes", new TableInfo.Column("expectedOnsetMinutes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMedicationSchedules.put("expectedDurationMinutes", new TableInfo.Column("expectedDurationMinutes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMedicationSchedules.put("notes", new TableInfo.Column("notes", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMedicationSchedules.put("active", new TableInfo.Column("active", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMedicationSchedules.put("createdAtMs", new TableInfo.Column("createdAtMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMedicationSchedules.put("syncedToCloud", new TableInfo.Column("syncedToCloud", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysMedicationSchedules = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesMedicationSchedules = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoMedicationSchedules = new TableInfo("medication_schedules", _columnsMedicationSchedules, _foreignKeysMedicationSchedules, _indicesMedicationSchedules);
        final TableInfo _existingMedicationSchedules = TableInfo.read(db, "medication_schedules");
        if (!_infoMedicationSchedules.equals(_existingMedicationSchedules)) {
          return new RoomOpenHelper.ValidationResult(false, "medication_schedules(health.cogniware.data.models.MedicationSchedule).\n"
                  + " Expected:\n" + _infoMedicationSchedules + "\n"
                  + " Found:\n" + _existingMedicationSchedules);
        }
        final HashMap<String, TableInfo.Column> _columnsDoseEvents = new HashMap<String, TableInfo.Column>(13);
        _columnsDoseEvents.put("eventId", new TableInfo.Column("eventId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDoseEvents.put("patientId", new TableInfo.Column("patientId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDoseEvents.put("scheduleId", new TableInfo.Column("scheduleId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDoseEvents.put("drugName", new TableInfo.Column("drugName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDoseEvents.put("doseMg", new TableInfo.Column("doseMg", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDoseEvents.put("takenAtMs", new TableInfo.Column("takenAtMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDoseEvents.put("loggedAtMs", new TableInfo.Column("loggedAtMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDoseEvents.put("logMethod", new TableInfo.Column("logMethod", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDoseEvents.put("hrvAtDoseMs", new TableInfo.Column("hrvAtDoseMs", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDoseEvents.put("hrAtDoseBpm", new TableInfo.Column("hrAtDoseBpm", "REAL", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDoseEvents.put("predictedWearOffMs", new TableInfo.Column("predictedWearOffMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDoseEvents.put("actualWearOffMs", new TableInfo.Column("actualWearOffMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDoseEvents.put("syncedToCloud", new TableInfo.Column("syncedToCloud", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysDoseEvents = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesDoseEvents = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoDoseEvents = new TableInfo("dose_events", _columnsDoseEvents, _foreignKeysDoseEvents, _indicesDoseEvents);
        final TableInfo _existingDoseEvents = TableInfo.read(db, "dose_events");
        if (!_infoDoseEvents.equals(_existingDoseEvents)) {
          return new RoomOpenHelper.ValidationResult(false, "dose_events(health.cogniware.data.models.DoseEvent).\n"
                  + " Expected:\n" + _infoDoseEvents + "\n"
                  + " Found:\n" + _existingDoseEvents);
        }
        final HashMap<String, TableInfo.Column> _columnsDiagnosisProfiles = new HashMap<String, TableInfo.Column>(13);
        _columnsDiagnosisProfiles.put("profileId", new TableInfo.Column("profileId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDiagnosisProfiles.put("patientId", new TableInfo.Column("patientId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDiagnosisProfiles.put("conditionType", new TableInfo.Column("conditionType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDiagnosisProfiles.put("ageGroup", new TableInfo.Column("ageGroup", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDiagnosisProfiles.put("subtype", new TableInfo.Column("subtype", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDiagnosisProfiles.put("diagnosisDateMs", new TableInfo.Column("diagnosisDateMs", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDiagnosisProfiles.put("caregiverId", new TableInfo.Column("caregiverId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDiagnosisProfiles.put("notifyTarget", new TableInfo.Column("notifyTarget", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDiagnosisProfiles.put("anomalyModel", new TableInfo.Column("anomalyModel", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDiagnosisProfiles.put("medicationModel", new TableInfo.Column("medicationModel", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDiagnosisProfiles.put("riskWeightsJson", new TableInfo.Column("riskWeightsJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDiagnosisProfiles.put("createdAtMs", new TableInfo.Column("createdAtMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDiagnosisProfiles.put("syncedToCloud", new TableInfo.Column("syncedToCloud", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysDiagnosisProfiles = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesDiagnosisProfiles = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoDiagnosisProfiles = new TableInfo("diagnosis_profiles", _columnsDiagnosisProfiles, _foreignKeysDiagnosisProfiles, _indicesDiagnosisProfiles);
        final TableInfo _existingDiagnosisProfiles = TableInfo.read(db, "diagnosis_profiles");
        if (!_infoDiagnosisProfiles.equals(_existingDiagnosisProfiles)) {
          return new RoomOpenHelper.ValidationResult(false, "diagnosis_profiles(health.cogniware.data.models.DiagnosisProfile).\n"
                  + " Expected:\n" + _infoDiagnosisProfiles + "\n"
                  + " Found:\n" + _existingDiagnosisProfiles);
        }
        final HashMap<String, TableInfo.Column> _columnsPostureBaselines = new HashMap<String, TableInfo.Column>(13);
        _columnsPostureBaselines.put("baselineId", new TableInfo.Column("baselineId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPostureBaselines.put("patientId", new TableInfo.Column("patientId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPostureBaselines.put("capturedAtMs", new TableInfo.Column("capturedAtMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPostureBaselines.put("sessionType", new TableInfo.Column("sessionType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPostureBaselines.put("armSwingAsymmetryIndex", new TableInfo.Column("armSwingAsymmetryIndex", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPostureBaselines.put("gaitInitiationDelayMs", new TableInfo.Column("gaitInitiationDelayMs", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPostureBaselines.put("trunkForwardLeanDeg", new TableInfo.Column("trunkForwardLeanDeg", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPostureBaselines.put("stepSymmetryIndex", new TableInfo.Column("stepSymmetryIndex", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPostureBaselines.put("cadenceStepsPerMin", new TableInfo.Column("cadenceStepsPerMin", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPostureBaselines.put("landmarkSeriesJson", new TableInfo.Column("landmarkSeriesJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPostureBaselines.put("qualityScore", new TableInfo.Column("qualityScore", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPostureBaselines.put("flaggedIssues", new TableInfo.Column("flaggedIssues", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPostureBaselines.put("syncedToCloud", new TableInfo.Column("syncedToCloud", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPostureBaselines = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPostureBaselines = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPostureBaselines = new TableInfo("posture_baselines", _columnsPostureBaselines, _foreignKeysPostureBaselines, _indicesPostureBaselines);
        final TableInfo _existingPostureBaselines = TableInfo.read(db, "posture_baselines");
        if (!_infoPostureBaselines.equals(_existingPostureBaselines)) {
          return new RoomOpenHelper.ValidationResult(false, "posture_baselines(health.cogniware.data.models.PostureBaseline).\n"
                  + " Expected:\n" + _infoPostureBaselines + "\n"
                  + " Found:\n" + _existingPostureBaselines);
        }
        final HashMap<String, TableInfo.Column> _columnsAssessmentSessions = new HashMap<String, TableInfo.Column>(12);
        _columnsAssessmentSessions.put("sessionId", new TableInfo.Column("sessionId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssessmentSessions.put("patientId", new TableInfo.Column("patientId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssessmentSessions.put("scheduledMs", new TableInfo.Column("scheduledMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssessmentSessions.put("completedMs", new TableInfo.Column("completedMs", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssessmentSessions.put("sessionType", new TableInfo.Column("sessionType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssessmentSessions.put("postureBaselineId", new TableInfo.Column("postureBaselineId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssessmentSessions.put("rawVideoStoredRemotely", new TableInfo.Column("rawVideoStoredRemotely", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssessmentSessions.put("armSwingDelta", new TableInfo.Column("armSwingDelta", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssessmentSessions.put("trunkLeanDelta", new TableInfo.Column("trunkLeanDelta", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssessmentSessions.put("gaitInitiationDelta", new TableInfo.Column("gaitInitiationDelta", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssessmentSessions.put("flaggedIssues", new TableInfo.Column("flaggedIssues", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAssessmentSessions.put("syncedToCloud", new TableInfo.Column("syncedToCloud", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAssessmentSessions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesAssessmentSessions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoAssessmentSessions = new TableInfo("assessment_sessions", _columnsAssessmentSessions, _foreignKeysAssessmentSessions, _indicesAssessmentSessions);
        final TableInfo _existingAssessmentSessions = TableInfo.read(db, "assessment_sessions");
        if (!_infoAssessmentSessions.equals(_existingAssessmentSessions)) {
          return new RoomOpenHelper.ValidationResult(false, "assessment_sessions(health.cogniware.data.models.AssessmentSession).\n"
                  + " Expected:\n" + _infoAssessmentSessions + "\n"
                  + " Found:\n" + _existingAssessmentSessions);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "04dcf540a9632d563edfdad4f459f928", "846d881e6cdfe13b954ce2e4aa8a17ca");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "keystroke_sessions","tremor_readings","gait_sessions","structural_baseline","health_daily","patient_profile","medication_schedules","dose_events","diagnosis_profiles","posture_baselines","assessment_sessions");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `keystroke_sessions`");
      _db.execSQL("DELETE FROM `tremor_readings`");
      _db.execSQL("DELETE FROM `gait_sessions`");
      _db.execSQL("DELETE FROM `structural_baseline`");
      _db.execSQL("DELETE FROM `health_daily`");
      _db.execSQL("DELETE FROM `patient_profile`");
      _db.execSQL("DELETE FROM `medication_schedules`");
      _db.execSQL("DELETE FROM `dose_events`");
      _db.execSQL("DELETE FROM `diagnosis_profiles`");
      _db.execSQL("DELETE FROM `posture_baselines`");
      _db.execSQL("DELETE FROM `assessment_sessions`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(KeystrokeDao.class, KeystrokeDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(TremorDao.class, TremorDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(GaitDao.class, GaitDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(HealthDao.class, HealthDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ProfileDao.class, ProfileDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(MedicationDao.class, MedicationDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(DiagnosisProfileDao.class, DiagnosisProfileDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(PostureBaselineDao.class, PostureBaselineDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(AssessmentSessionDao.class, AssessmentSessionDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public KeystrokeDao keystrokeDao() {
    if (_keystrokeDao != null) {
      return _keystrokeDao;
    } else {
      synchronized(this) {
        if(_keystrokeDao == null) {
          _keystrokeDao = new KeystrokeDao_Impl(this);
        }
        return _keystrokeDao;
      }
    }
  }

  @Override
  public TremorDao tremorDao() {
    if (_tremorDao != null) {
      return _tremorDao;
    } else {
      synchronized(this) {
        if(_tremorDao == null) {
          _tremorDao = new TremorDao_Impl(this);
        }
        return _tremorDao;
      }
    }
  }

  @Override
  public GaitDao gaitDao() {
    if (_gaitDao != null) {
      return _gaitDao;
    } else {
      synchronized(this) {
        if(_gaitDao == null) {
          _gaitDao = new GaitDao_Impl(this);
        }
        return _gaitDao;
      }
    }
  }

  @Override
  public HealthDao healthDao() {
    if (_healthDao != null) {
      return _healthDao;
    } else {
      synchronized(this) {
        if(_healthDao == null) {
          _healthDao = new HealthDao_Impl(this);
        }
        return _healthDao;
      }
    }
  }

  @Override
  public ProfileDao profileDao() {
    if (_profileDao != null) {
      return _profileDao;
    } else {
      synchronized(this) {
        if(_profileDao == null) {
          _profileDao = new ProfileDao_Impl(this);
        }
        return _profileDao;
      }
    }
  }

  @Override
  public MedicationDao medicationDao() {
    if (_medicationDao != null) {
      return _medicationDao;
    } else {
      synchronized(this) {
        if(_medicationDao == null) {
          _medicationDao = new MedicationDao_Impl(this);
        }
        return _medicationDao;
      }
    }
  }

  @Override
  public DiagnosisProfileDao diagnosisProfileDao() {
    if (_diagnosisProfileDao != null) {
      return _diagnosisProfileDao;
    } else {
      synchronized(this) {
        if(_diagnosisProfileDao == null) {
          _diagnosisProfileDao = new DiagnosisProfileDao_Impl(this);
        }
        return _diagnosisProfileDao;
      }
    }
  }

  @Override
  public PostureBaselineDao postureBaselineDao() {
    if (_postureBaselineDao != null) {
      return _postureBaselineDao;
    } else {
      synchronized(this) {
        if(_postureBaselineDao == null) {
          _postureBaselineDao = new PostureBaselineDao_Impl(this);
        }
        return _postureBaselineDao;
      }
    }
  }

  @Override
  public AssessmentSessionDao assessmentSessionDao() {
    if (_assessmentSessionDao != null) {
      return _assessmentSessionDao;
    } else {
      synchronized(this) {
        if(_assessmentSessionDao == null) {
          _assessmentSessionDao = new AssessmentSessionDao_Impl(this);
        }
        return _assessmentSessionDao;
      }
    }
  }
}
