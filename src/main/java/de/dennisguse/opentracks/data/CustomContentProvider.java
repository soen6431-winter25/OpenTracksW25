/*
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */


package de.dennisguse.opentracks.data;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.Arrays;

import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.data.tables.MarkerColumns;
import de.dennisguse.opentracks.data.tables.TrackPointsColumns;
import de.dennisguse.opentracks.data.tables.TracksColumns;
import de.dennisguse.opentracks.settings.PreferencesUtils;

/**
 * A {@link ContentProvider} that handles access to track points, tracks, and markers tables.
 * <p>
 * Data consistency is enforced using Foreign Key Constraints within the database incl. cascading deletes.
 *
 * @author Leif Hendrik Wilden
 */
public class CustomContentProvider extends ContentProvider {

    private static final String TAG = CustomContentProvider.class.getSimpleName();

    private static final String SQL_LIST_DELIMITER = ",";

    private static final int TOTAL_DELETED_ROWS_VACUUM_THRESHOLD = 10000;

    private final UriMatcher uriMatcher;

    private SQLiteDatabase db;

    private static final String TIME_SELECT_CLAUSE = ", (SELECT time_value FROM time_select)), t.";

    private static final String COALESCE_MAX = " * (COALESCE(MAX(t.";

    private static final String COALESCE_MAX_TIME_DIFF = ") - t.";

    /**
     * The string representing the query that compute sensor stats from trackpoints table.
     * It computes the average for heart rate, cadence and power (duration-based average) and the maximum for heart rate, cadence and power.
     * Finally, it ignores manual pause (SEGMENT_START_MANUAL).
     */
    private final String SENSOR_STATS_QUERY =
            "WITH time_select as " +
                    "(SELECT t1." + TrackPointsColumns.TIME + " * (t1." + TrackPointsColumns.TYPE + " NOT IN (" + TrackPoint.Type.SEGMENT_START_MANUAL.type_db + ")) time_value " +
                    "FROM " + TrackPointsColumns.TABLE_NAME + " t1 " +
                    "WHERE t1." + TrackPointsColumns._ID + " > t." + TrackPointsColumns._ID + " AND t1." + TrackPointsColumns.TRACKID + " = ? ORDER BY _id LIMIT 1) " +

                    "SELECT " +
                    "SUM(t." + TrackPointsColumns.SENSOR_HEARTRATE + COALESCE_MAX + TrackPointsColumns.TIME + TIME_SELECT_CLAUSE + TrackPointsColumns.TIME + COALESCE_MAX_TIME_DIFF + TrackPointsColumns.TIME + ")) " +
                    "/ " +
                    "SUM(COALESCE(MAX(t." + TrackPointsColumns.TIME + TIME_SELECT_CLAUSE + TrackPointsColumns.TIME + COALESCE_MAX_TIME_DIFF + TrackPointsColumns.TIME + ") " + TrackPointsColumns.ALIAS_AVG_HR + ", " +

                    "MAX(t." + TrackPointsColumns.SENSOR_HEARTRATE + ") " + TrackPointsColumns.ALIAS_MAX_HR + ", " +

                    "SUM(t." + TrackPointsColumns.SENSOR_CADENCE + COALESCE_MAX + TrackPointsColumns.TIME + TIME_SELECT_CLAUSE + TrackPointsColumns.TIME + COALESCE_MAX_TIME_DIFF + TrackPointsColumns.TIME + ")) " +
                    "/ " +
                    "SUM(COALESCE(MAX(t." + TrackPointsColumns.TIME + TIME_SELECT_CLAUSE + TrackPointsColumns.TIME + COALESCE_MAX_TIME_DIFF + TrackPointsColumns.TIME + ") " + TrackPointsColumns.ALIAS_AVG_CADENCE + ", " +

                    "MAX(t." + TrackPointsColumns.SENSOR_CADENCE + ") " + TrackPointsColumns.ALIAS_MAX_CADENCE + ", " +

                    "SUM(t." + TrackPointsColumns.SENSOR_POWER + COALESCE_MAX + TrackPointsColumns.TIME + TIME_SELECT_CLAUSE + TrackPointsColumns.TIME + COALESCE_MAX_TIME_DIFF + TrackPointsColumns.TIME + ")) " +
                    "/ " +
                    "SUM(COALESCE(MAX(t." + TrackPointsColumns.TIME + TIME_SELECT_CLAUSE + TrackPointsColumns.TIME + COALESCE_MAX_TIME_DIFF + TrackPointsColumns.TIME + ") " + TrackPointsColumns.ALIAS_AVG_POWER + ", " +

                    "MAX(t." + TrackPointsColumns.SENSOR_POWER + ") " + TrackPointsColumns.ALIAS_MAX_POWER + " " +

                    "FROM " + TrackPointsColumns.TABLE_NAME + " t " +
                    "WHERE t." + TrackPointsColumns.TRACKID + " = ? " +
                    "AND t." + TrackPointsColumns.TYPE + " NOT IN (" + TrackPoint.Type.SEGMENT_START_MANUAL.type_db + ")";

    public CustomContentProvider() {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

        // Access the authority via the method
        uriMatcher.addURI(ContentProviderUtils.getAuthorityPackage(), TrackPointsColumns.CONTENT_URI_BY_ID.getPath(), UrlType.TRACKPOINTS.ordinal());
        uriMatcher.addURI(ContentProviderUtils.getAuthorityPackage(), TrackPointsColumns.CONTENT_URI_BY_ID.getPath() + "/#", UrlType.TRACKPOINTS_BY_ID.ordinal());
        uriMatcher.addURI(ContentProviderUtils.getAuthorityPackage(), TrackPointsColumns.CONTENT_URI_BY_TRACKID.getPath() + "/*", UrlType.TRACKPOINTS_BY_TRACKID.ordinal());
        uriMatcher.addURI(ContentProviderUtils.getAuthorityPackage(), TracksColumns.CONTENT_URI.getPath(), UrlType.TRACKS.ordinal());
        uriMatcher.addURI(ContentProviderUtils.getAuthorityPackage(), TracksColumns.CONTENT_URI_SENSOR_STATS.getPath() + "/#", UrlType.TRACKS_SENSOR_STATS.ordinal());
        uriMatcher.addURI(ContentProviderUtils.getAuthorityPackage(), TracksColumns.CONTENT_URI.getPath() + "/*", UrlType.TRACKS_BY_ID.ordinal());
        uriMatcher.addURI(ContentProviderUtils.getAuthorityPackage(), MarkerColumns.CONTENT_URI.getPath(), UrlType.MARKERS.ordinal());
        uriMatcher.addURI(ContentProviderUtils.getAuthorityPackage(), MarkerColumns.CONTENT_URI.getPath() + "/#", UrlType.MARKERS_BY_ID.ordinal());
        uriMatcher.addURI(ContentProviderUtils.getAuthorityPackage(), MarkerColumns.CONTENT_URI_BY_TRACKID.getPath() + "/*", UrlType.MARKERS_BY_TRACKID.ordinal());
    }

    @Override
    public boolean onCreate() {
        return onCreate(getContext());
    }

        /**
         * Helper method to make onCreate is testable.
         *
         * @param context context to creates database
         * @return true means run successfully
         */
        @VisibleForTesting
        boolean onCreate(Context context) {
            CustomSQLiteOpenHelper databaseHelper = new CustomSQLiteOpenHelper(context);
            try {
                db = databaseHelper.getWritableDatabase();
                // Necessary to enable cascade deletion from Track to TrackPoints and Markers
                db.setForeignKeyConstraintsEnabled(true);
            } catch (SQLiteException e) {
                Log.e(TAG, "Unable to open database for writing.", e);
            }
            return db != null;
        }
    
        @Override
        public int delete(@NonNull Uri url, String where, String[] selectionArgs) {
            String table = switch (getUrlType(url)) {
                case TRACKPOINTS -> TrackPointsColumns.TABLE_NAME;
                case TRACKS -> TracksColumns.TABLE_NAME;
                case MARKERS -> MarkerColumns.TABLE_NAME;
                default -> throw new IllegalArgumentException("Unknown URL " + url);
            };
    
            Log.w(TAG, "Deleting from table " + table);



            int totalChangesBefore = getTotalChanges();
            int deletedRowsFromTable;
            try {
                db.beginTransaction();
                deletedRowsFromTable = db.delete(table, where, getSafeSelectionArgs(selectionArgs));
                Log.i(TAG, "Deleted " + deletedRowsFromTable + " rows of table " + table);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            getContext().getContentResolver().notifyChange(url, null, false);
    
            int totalChanges = getTotalChanges() - totalChangesBefore;
            Log.i(TAG, "Deleted " + totalChanges + " total rows from database");
    
            PreferencesUtils.addTotalRowsDeleted(totalChanges);
            int totalRowsDeleted = PreferencesUtils.getTotalRowsDeleted();
            if (totalRowsDeleted > TOTAL_DELETED_ROWS_VACUUM_THRESHOLD) {
                Log.i(TAG, "TotalRowsDeleted " + totalRowsDeleted + ", starting to vacuum the database.");
                db.execSQL("VACUUM");
                PreferencesUtils.resetTotalRowsDeleted();
            }
    
            return deletedRowsFromTable;
        }
    
        private int getTotalChanges() {
            int totalCount;
            try (Cursor cursor = db.rawQuery("SELECT total_changes()", null)) {
                cursor.moveToNext();
                totalCount = cursor.getInt(0);
            }
            return totalCount;
        }
    
        @Override
        public String getType(@NonNull Uri url) {
            return switch (getUrlType(url)) {
                case TRACKPOINTS -> TrackPointsColumns.CONTENT_TYPE;
                case TRACKPOINTS_BY_ID, TRACKPOINTS_BY_TRACKID -> TrackPointsColumns.CONTENT_ITEMTYPE;
                case TRACKS -> TracksColumns.CONTENT_TYPE;
                case TRACKS_BY_ID -> TracksColumns.CONTENT_ITEMTYPE;
                case MARKERS -> MarkerColumns.CONTENT_TYPE;
                case MARKERS_BY_ID, MARKERS_BY_TRACKID -> MarkerColumns.CONTENT_ITEMTYPE;
                default -> throw new IllegalArgumentException("Unknown URL " + url);
            };
        }
    
        @Override
        public Uri insert(@NonNull Uri url, ContentValues initialValues) {
            if (initialValues == null) {
                initialValues = new ContentValues();
            }
            Uri result;
            try {
                db.beginTransaction();
                result = insertContentValues(url, getUrlType(url), initialValues);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            getContext().getContentResolver().notifyChange(url, null, false);
            return result;
        }
    
        @Override
        public int bulkInsert(@NonNull Uri url, @NonNull ContentValues[] valuesBulk) {
            int numInserted;
            try {
                // Use a transaction in order to make the insertions run as a single batch
                db.beginTransaction();
    
                UrlType urlType = getUrlType(url);
                for (numInserted = 0; numInserted < valuesBulk.length; numInserted++) {
                    ContentValues contentValues = valuesBulk[numInserted];
                    if (contentValues == null) {
                        contentValues = new ContentValues();
                    }
                    insertContentValues(url, urlType, contentValues);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            getContext().getContentResolver().notifyChange(url, null, false);
            return numInserted;
        }
    
        private String[] validateProjection(String[] projection, String tableName) {
            if (projection == null) {
                return null;
            }
        
            // Define allowed columns for each table
            Set<String> allowedColumns;
            switch (tableName) {
                case TrackPointsColumns.TABLE_NAME:
                    allowedColumns = new HashSet<>(Arrays.asList(TrackPointsColumns.ALL_COLUMNS));
                    break;
                case TracksColumns.TABLE_NAME:
                    allowedColumns = new HashSet<>(Arrays.asList(TracksColumns.ALL_COLUMNS));
                    break;
                case MarkerColumns.TABLE_NAME:
                    allowedColumns = new HashSet<>(Arrays.asList(MarkerColumns.ALL_COLUMNS));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown table: " + tableName);
            }
        
            // Filter projection: Allow only known columns
            List<String> filteredProjection = new ArrayList<>();
            for (String column : projection) {
                if (allowedColumns.contains(column)) {
                    filteredProjection.add(column);
                }
            }
        
            return filteredProjection.isEmpty() ? null : filteredProjection.toArray(new String[0]);
        }

        private String validateSelection(String selection) {
            if (selection == null) {
                return null;
            }
        
            // Prevent dangerous characters like ; -- ' " or OR/AND without parameters
            if (selection.matches(".*['\";].*") || selection.toLowerCase().matches(".*\\b(or|and)\\b.*")) {
                throw new IllegalArgumentException("Invalid selection parameter detected");
            }
        
            return selection;
        }

        private String[] validateSelectionArgs(String[] selectionArgs) {
            if (selectionArgs == null) {
                return null;
            }
        
            String[] sanitizedArgs = new String[selectionArgs.length];
            for (int i = 0; i < selectionArgs.length; i++) {
                if (selectionArgs[i] == null) {
                    throw new IllegalArgumentException("Null value detected in selectionArgs");
                }
        
                String arg = selectionArgs[i].trim();

                if (arg.matches("\\d+")) { 
                    sanitizedArgs[i] = arg;
                } 
                else if (arg.matches("[a-zA-Z0-9_\\-@.]+")) { 
                    sanitizedArgs[i] = arg;
                } 
                else {
                    throw new IllegalArgumentException("Invalid selectionArgs parameter detected: " + arg);
                }
            }
            return sanitizedArgs;
        }

        private String validateSortOrder(String sortOrder, String[] allowedColumns) {
            if (sortOrder == null || sortOrder.isEmpty()) {
                return null;
            }
        
            // Convert allowed columns into a Set for easy lookup
            Set<String> allowedColumnsSet = new HashSet<>(Arrays.asList(allowedColumns));
        
            // Split sortOrder by commas to support multiple columns (e.g., "name ASC, age DESC")
            String[] parts = sortOrder.split(",");
            List<String> validatedParts = new ArrayList<>();
        
            for (String part : parts) {
                String[] tokens = part.trim().split("\\s+");
                if (tokens.length < 1 || tokens.length > 2) {
                    throw new IllegalArgumentException("Invalid sort order: " + sortOrder);
                }
        
                String columnName = tokens[0].trim();
                String sortDirection = (tokens.length == 2) ? tokens[1].trim().toUpperCase() : "ASC"; // Default to ASC
        
                // Validate column name and sorting direction
                if (!allowedColumnsSet.contains(columnName) || (!sortDirection.equals("ASC") && !sortDirection.equals("DESC"))) {
                    throw new IllegalArgumentException("Invalid sort order: " + sortOrder);
                }
        
                validatedParts.add(columnName + " " + sortDirection);
            }
        
            return String.join(", ", validatedParts);
        }

        private String[] getSafeSelectionArgs(String[] selectionArgs) {
            if (selectionArgs == null) {
                return null;
            }
        
            List<String> sanitizedList = new ArrayList<>();
            
            for (String arg : selectionArgs) {
                if (arg == null) {
                    throw new IllegalArgumentException("Null value detected in selectionArgs");
                }
        
                String sanitizedArg = arg.trim();
        
                if (sanitizedArg.matches("\\d+") || sanitizedArg.matches("[a-zA-Z0-9_\\-@.]+")) { 
                    sanitizedList.add(sanitizedArg);
                } else {
                    throw new IllegalArgumentException("Invalid selectionArgs parameter detected: " + sanitizedArg);
                }
            }
        
            return sanitizedList.toArray(new String[0]);
        }
        
        @Override
        public Cursor query(@NonNull Uri url, String[] projection, String selection, String[] selectionArgs, String sort) {
            SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
            String sortOrder = null;
            switch (getUrlType(url)) {
                case TRACKPOINTS: {
                    queryBuilder.setTables(TrackPointsColumns.TABLE_NAME);
                    sortOrder = sort != null ? validateSortOrder(sort, TrackPointsColumns.ALL_COLUMNS) : TrackPointsColumns.DEFAULT_SORT_ORDER;
                    break;
                }
                case TRACKPOINTS_BY_ID: {
                    queryBuilder.setTables(TrackPointsColumns.TABLE_NAME);
                    queryBuilder.appendWhere(TrackPointsColumns._ID + "= ?");
                    queryBuilder.appendWhereEscapeString(String.valueOf(ContentUris.parseId(url)));
                    break;
                }
                case TRACKPOINTS_BY_TRACKID: {
                    queryBuilder.setTables(TrackPointsColumns.TABLE_NAME);
                    String[] trackIds = ContentProviderUtils.parseTrackIdsFromUri(url);
                    String placeholders = TextUtils.join(",", Collections.nCopies(trackIds.length, "?"));
                    queryBuilder.appendWhere(TrackPointsColumns.TRACKID + " IN (" + placeholders + ")");
                    for(String id : trackIds) {
                        queryBuilder.appendWhereEscapeString(id);
                    }
                    break;
                }
                case TRACKS: {
                    if (projection != null && Arrays.asList(projection).contains(TracksColumns.MARKER_COUNT)) {
                        queryBuilder.setTables(TracksColumns.TABLE_NAME + " LEFT OUTER JOIN (SELECT " + MarkerColumns.TRACKID + " AS markerTrackId, COUNT(*) AS " + TracksColumns.MARKER_COUNT + " FROM " + MarkerColumns.TABLE_NAME + " GROUP BY " + MarkerColumns.TRACKID + ") ON (" + TracksColumns.TABLE_NAME + "." + TracksColumns._ID + "= markerTrackId)");
                    } else {
                        queryBuilder.setTables(TracksColumns.TABLE_NAME);
                    }
                    sortOrder = sort != null ? validateSortOrder(sort, TrackPointsColumns.ALL_COLUMNS) : TracksColumns.DEFAULT_SORT_ORDER;
                    break;
                }
                case TRACKS_BY_ID: {
                    queryBuilder.setTables(TracksColumns.TABLE_NAME);
                    String[] trackIds = ContentProviderUtils.parseTrackIdsFromUri(url);
                    String placeholders = TextUtils.join(",", Collections.nCopies(trackIds.length, "?"));
                    queryBuilder.appendWhere(TracksColumns._ID + " IN (" + placeholders + ")");
                    for (String id : trackIds) {
                        queryBuilder.appendWhereEscapeString(id);
                    }
                    break;
                }
                case TRACKS_SENSOR_STATS: {
                    long trackId = ContentUris.parseId(url);
                    return db.rawQuery(SENSOR_STATS_QUERY, new String[]{String.valueOf(trackId), String.valueOf(trackId)});
                }
                case MARKERS: {
                    queryBuilder.setTables(MarkerColumns.TABLE_NAME);
                    sortOrder = sort != null ? validateSortOrder(sort, MarkerColumns.ALL_COLUMNS) : MarkerColumns.DEFAULT_SORT_ORDER;
                    break;
                }
                case MARKERS_BY_ID: {
                    queryBuilder.setTables(MarkerColumns.TABLE_NAME);
                    queryBuilder.appendWhere(MarkerColumns._ID + "= ?");
                    queryBuilder.appendWhereEscapeString(String.valueOf(ContentUris.parseId(url)));
                    break;
                }
                case MARKERS_BY_TRACKID: {
                    queryBuilder.setTables(MarkerColumns.TABLE_NAME);
                    trackIds = ContentProviderUtils.parseTrackIdsFromUri(url);
                    placeholders = TextUtils.join(",", Collections.nCopies(trackIds.length, "?"));
                    queryBuilder.appendWhere(MarkerColumns.TRACKID + " IN (" + placeholders + ")");
                    for (String id : trackIds) {
                        queryBuilder.appendWhereEscapeString(id);
                    }
                    break;
                }
                default -> throw new IllegalArgumentException("Unknown url " + url);
            }
            String[] safeProjection = validateProjection(projection, queryBuilder.getTables());
            String safeSelection = validateSelection(selection);
            String[] safeSelectionArgs = getSafeSelectionArgs(selectionArgs);
            Cursor cursor = queryBuilder.query(db, safeProjection, safeSelection, safeSelectionArgs, null, null, sortOrder);
            cursor.setNotificationUri(getContext().getContentResolver(), url);
            return cursor;
        }

    @Override
    public int update(@NonNull Uri url, ContentValues values, String where, String[] selectionArgs) {
        String table;
        String whereClause = where;
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        switch (getUrlType(url)) {
            case TRACKPOINTS -> table = TrackPointsColumns.TABLE_NAME;
            case TRACKPOINTS_BY_ID -> {
                table = TrackPointsColumns.TABLE_NAME;
                qb.appendWhere(TrackPointsColumns._ID + "=?");
                selectionArgs = appendSelectionArg(selectionArgs, String.valueOf(ContentUris.parseId(url)));
            }
            case TRACKS -> table = TracksColumns.TABLE_NAME;
            case TRACKS_BY_ID -> {
                table = TracksColumns.TABLE_NAME;
                qb.appendWhere(TracksColumns._ID + "=?");
                selectionArgs = appendSelectionArg(selectionArgs, String.valueOf(ContentUris.parseId(url)));
            }
            case MARKERS -> table = MarkerColumns.TABLE_NAME;
            case MARKERS_BY_ID -> {
                table = MarkerColumns.TABLE_NAME;
                qb.appendWhere(MarkerColumns._ID + "=?");
                selectionArgs = appendSelectionArg(selectionArgs, String.valueOf(ContentUris.parseId(url)));
            }
            default -> throw new IllegalArgumentException("Unknown url " + url);
        }
        qb.setTables(table);
        int count;

        try {
            db.beginTransaction();

            count = safeUpdate(db, qb.getTables(),values,whereClause,selectionArgs);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        getContext().getContentResolver().notifyChange(url, null, false);
        return count;
    }

    /**
     * A safe update method that rejects unsafe whereClause.
     *
     * @param db          the database
     * @param table       the table name
     * @param values      the content values
     * @param whereClause the where clause
     * @param selectionArgs the selection arguments
     * @return the number of rows affected
     */
    private int safeUpdate(SQLiteDatabase db, String table, ContentValues values, String whereClause, String[] selectionArgs) {
        // Reject unsafe whereClause if it contains suspicious characters (e.g., semicolons, quotes, comments)
        if (whereClause != null && !whereClause.matches("^[\\w\\s=?.()]+$")) {
            throw new IllegalArgumentException("Unsafe whereClause detected.");
        }

        return db.update(table, values, whereClause, selectionArgs);
    }

    private String[] appendSelectionArg(String[] selectionArgs, String id) {
        if (selectionArgs == null) {
            return new String[]{id};
        }
        String[] newArgs = new String[selectionArgs.length + 1];
        System.arraycopy(selectionArgs, 0, newArgs, 0, selectionArgs.length);
        newArgs[selectionArgs.length] = id;
        return newArgs;
    }

    @NonNull
    private UrlType getUrlType(Uri url) {
        UrlType[] urlTypes = UrlType.values();
        int matchIndex = uriMatcher.match(url);
        if (0 <= matchIndex && matchIndex < urlTypes.length) {
            return urlTypes[matchIndex];
        }

        throw new IllegalArgumentException("Unknown URL " + url);
    }

    /**
     * Inserts a content based on the url type.
     *
     * @param url           the content url
     * @param urlType       the url type
     * @param contentValues the content values
     */
    private Uri insertContentValues(Uri url, UrlType urlType, ContentValues contentValues) {
        return switch (urlType) {
            case TRACKPOINTS -> insertTrackPoint(url, contentValues);
            case TRACKS -> insertTrack(url, contentValues);
            case MARKERS -> insertMarker(url, contentValues);
            default -> throw new IllegalArgumentException("Unknown url " + url);
        };
    }

    private Uri insertTrackPoint(Uri url, ContentValues values) {
        boolean hasTime = values.containsKey(TrackPointsColumns.TIME);
        if (!hasTime) {
            throw new IllegalArgumentException("Latitude, longitude, and time values are required.");
        }
        long rowId = db.insert(TrackPointsColumns.TABLE_NAME, TrackPointsColumns._ID, values);
        if (rowId >= 0) {
            return ContentUris.appendId(TrackPointsColumns.CONTENT_URI_BY_ID.buildUpon(), rowId).build();
        }
        throw new SQLiteException("Failed to insert a track point " + url);

    }

    private Uri insertTrack(Uri url, ContentValues contentValues) {
        long rowId = db.insert(TracksColumns.TABLE_NAME, TracksColumns._ID, contentValues);
        if (rowId >= 0) {
            return ContentUris.appendId(TracksColumns.CONTENT_URI.buildUpon(), rowId).build();
        }
        throw new SQLException("Failed to insert a track " + url);
    }

    private Uri insertMarker(Uri url, ContentValues contentValues) {
        long rowId = db.insert(MarkerColumns.TABLE_NAME, MarkerColumns._ID, contentValues);
        if (rowId >= 0) {
            return ContentUris.appendId(MarkerColumns.CONTENT_URI.buildUpon(), rowId).build();
        }
        throw new SQLException("Failed to insert a marker " + url);
    }

    @VisibleForTesting
    enum UrlType {
        TRACKPOINTS,
        TRACKPOINTS_BY_ID,
        TRACKPOINTS_BY_TRACKID,
        TRACKS,
        TRACKS_BY_ID,
        TRACKS_SENSOR_STATS,
        MARKERS,
        MARKERS_BY_ID,
        MARKERS_BY_TRACKID
    }
}