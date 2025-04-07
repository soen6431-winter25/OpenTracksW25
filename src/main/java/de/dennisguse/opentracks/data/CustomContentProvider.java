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

import de.dennisguse.opentracks.data.ContentProviderUtils;
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
import java.util.List;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.Arrays;

import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.data.models.TrackPoint.Type;
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
    public enum UrlType {
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

    public class CustomContentProvider extends ContentProvider {
    
        private static final String TAG = CustomContentProvider.class.getSimpleName();
    
        private static final String SQL_LIST_DELIMITER = ",";

        private static final String AND_CLAUSE_PREFIX = " AND (";
    
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
                "WITH time_select AS " +
                "(SELECT t1." + TrackPointsColumns.TIME + " * (t1." + TrackPointsColumns.TYPE + " NOT IN (?)) AS time_value " +
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
                "AND t." + TrackPointsColumns.TYPE + " NOT IN (?)";

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
                deletedRowsFromTable = db.delete(table, where, selectionArgs);
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

        // [REMAINDER OF THE FILE OMITTED FOR BREVITY] (keep your original code structure)
        
        @Override
        public Cursor query(@NonNull Uri url, String[] projection, String selection, String[] selectionArgs, String sort) {
            SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
            String sortOrder = null;
        
            switch (getUrlType(url)) {
                case TRACKPOINTS -> {
                    queryBuilder.setTables(TrackPointsColumns.TABLE_NAME);
                    sortOrder = sort != null ? sort : TrackPointsColumns.DEFAULT_SORT_ORDER;
                }
                case TRACKPOINTS_BY_ID -> {
                    queryBuilder.setTables(TrackPointsColumns.TABLE_NAME);
                    queryBuilder.appendWhere(TrackPointsColumns._ID + "=?");
                    selectionArgs = mergeArgs(new String[]{String.valueOf(ContentUris.parseId(url))}, selectionArgs);
                }
                case TRACKPOINTS_BY_TRACKID -> {
                    queryBuilder.setTables(TrackPointsColumns.TABLE_NAME);
                    String[] trackIds = ContentProviderUtils.parseTrackIdsFromUri(url);
                    StringBuilder inClause = new StringBuilder();
                    for (int i = 0; i < trackIds.length; i++) {
                        if (i > 0) inClause.append(", ");
                        inClause.append("?");
                    }
                    queryBuilder.appendWhere(TrackPointsColumns.TRACKID + " IN (" + inClause + ")");
                    selectionArgs = selectionArgs != null ? mergeArgs(trackIds, selectionArgs) : trackIds;
                }
                case TRACKS -> {
                    if (projection != null && Arrays.asList(projection).contains(TracksColumns.MARKER_COUNT)) {
                        queryBuilder.setTables(TracksColumns.TABLE_NAME + " LEFT OUTER JOIN (SELECT " + MarkerColumns.TRACKID + " AS markerTrackId, COUNT(*) AS " + TracksColumns.MARKER_COUNT + " FROM " + MarkerColumns.TABLE_NAME + " GROUP BY " + MarkerColumns.TRACKID + ") ON (" + TracksColumns.TABLE_NAME + "." + TracksColumns._ID + "= markerTrackId)");
                    } else {
                        queryBuilder.setTables(TracksColumns.TABLE_NAME);
                    }
                    sortOrder = sort != null ? sort : TracksColumns.DEFAULT_SORT_ORDER;
                }
                case TRACKS_BY_ID -> {
                    queryBuilder.setTables(TracksColumns.TABLE_NAME);
                    queryBuilder.appendWhere(TracksColumns._ID + "=?");
                    selectionArgs = mergeArgs(new String[]{String.valueOf(ContentUris.parseId(url))}, selectionArgs);
                }
                case TRACKS_SENSOR_STATS -> {
                    long trackId = ContentUris.parseId(url);
                    return db.rawQuery(SENSOR_STATS_QUERY, 
                        new String[]{
                            String.valueOf(TrackPoint.Type.SEGMENT_START_MANUAL.type_db),
                            String.valueOf(trackId),
                            String.valueOf(trackId),
                            String.valueOf(TrackPoint.Type.SEGMENT_START_MANUAL.type_db)
                        });
                }
                case MARKERS -> {
                    queryBuilder.setTables(MarkerColumns.TABLE_NAME);
                    sortOrder = sort != null ? sort : MarkerColumns.DEFAULT_SORT_ORDER;
                }
                case MARKERS_BY_ID -> {
                    queryBuilder.setTables(MarkerColumns.TABLE_NAME);
                    queryBuilder.appendWhere(MarkerColumns._ID + "=?");
                    selectionArgs = mergeArgs(new String[]{String.valueOf(ContentUris.parseId(url))}, selectionArgs);
                }
                case MARKERS_BY_TRACKID -> {
                    queryBuilder.setTables(MarkerColumns.TABLE_NAME);
                    String[] trackIds = ContentProviderUtils.parseTrackIdsFromUri(url);
                    // Validate all track IDs are numeric
                    for (String trackId : trackIds) {
                        if (!trackId.matches("^\\d+$")) {
                            throw new IllegalArgumentException("Invalid track ID format");
                        }
                    }
                    
                    StringBuilder inClause = new StringBuilder();
                    for (int i = 0; i < trackIds.length; i++) {
                        if (i > 0) inClause.append(", ");
                        inClause.append("?");
                    }
                    queryBuilder.appendWhere(MarkerColumns.TRACKID + " IN (" + inClause + ")");
                    selectionArgs = selectionArgs != null ? mergeArgs(trackIds, selectionArgs) : trackIds;
                }
                default -> throw new IllegalArgumentException("Unknown url " + url);
            }
        
            Cursor cursor = queryBuilder.query(db, projection, selection, selectionArgs, null, null, sortOrder);
            cursor.setNotificationUri(getContext().getContentResolver(), url);
            return cursor;
        }        
        
        @Override
        public int update(@NonNull Uri url, ContentValues values, String where, String[] selectionArgs) {
            String table;
            String whereClause = null;
            String[] safeArgs = null;

            switch (getUrlType(url)) {
                case TRACKPOINTS_BY_ID -> {
                    table = TrackPointsColumns.TABLE_NAME;
                    String id = String.valueOf(ContentUris.parseId(url));
                    whereClause = TrackPointsColumns._ID + "=?";
                    safeArgs = !TextUtils.isEmpty(where) ? mergeArgs(new String[]{id}, new String[]{escapeWhereClause(where)}) : new String[]{id};
                }
                case TRACKS_BY_ID -> {
                    table = TracksColumns.TABLE_NAME;
                    String id = String.valueOf(ContentUris.parseId(url));
                    whereClause = TracksColumns._ID + "=?";
                    safeArgs = !TextUtils.isEmpty(where) ? mergeArgs(new String[]{id}, new String[]{escapeWhereClause(where)}) : new String[]{id};
                }
                case MARKERS_BY_ID -> {
                    table = MarkerColumns.TABLE_NAME;
                    String id = String.valueOf(ContentUris.parseId(url));
                    whereClause = MarkerColumns._ID + "=?";
                    safeArgs = !TextUtils.isEmpty(where) ? mergeArgs(new String[]{id}, new String[]{escapeWhereClause(where)}) : new String[]{id};
                }
                case TRACKPOINTS, TRACKS, MARKERS -> {
                    table = switch (getUrlType(url)) {
                        case TRACKPOINTS -> TrackPointsColumns.TABLE_NAME;
                        case TRACKS -> TracksColumns.TABLE_NAME;
                        case MARKERS -> MarkerColumns.TABLE_NAME;
                        default -> throw new IllegalStateException();
                    };

                    if (!TextUtils.isEmpty(where)) {
                        whereClause = escapeWhereClause(where);
                    }
                    safeArgs = selectionArgs;
                }
                default -> throw new IllegalArgumentException("Unknown url " + url);
            }

            int count;
            try {
                db.beginTransaction();
                count = db.update(table, values, whereClause, safeArgs);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

            getContext().getContentResolver().notifyChange(url, null, false);
            return count;
        }

                /**
         * Merges two arrays of SQL selection arguments.
         */
        private String[] mergeArgs(String[] first, String[] second) {
            if (second == null || second.length == 0) return first;
            String[] result = new String[first.length + second.length];
            System.arraycopy(first, 0, result, 0, first.length);
            System.arraycopy(second, 0, result, first.length, second.length);
            return result;
        }

        /**
         * Escapes dangerous characters in WHERE clauses to prevent SQL injection.
         */
        private String escapeWhereClause(String input) {
            if (input == null) return null;
        
            // Validate input against strict allowlist
            if (!input.matches("^[a-zA-Z0-9_=<> \\(\\)\\+\\-\\*\\/\\.,:]+$")) {
                throw new IllegalArgumentException("Invalid characters detected in WHERE clause.");
            }
            
            // Additional checks for dangerous patterns
            String lower = input.toLowerCase();
            if (lower.contains(";") || lower.contains("--") || lower.contains("/*") || 
                lower.contains("*/") || lower.contains("xp_") || lower.contains("exec") ||
                lower.contains("union") || lower.contains("select") || lower.contains("insert") ||
                lower.contains("update") || lower.contains("delete") || lower.contains("drop") ||
                lower.contains("alter") || lower.contains("create") || lower.contains("truncate")) {
                throw new IllegalArgumentException("Potentially dangerous SQL pattern detected.");
            }
        
            return input;
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
    
    }
