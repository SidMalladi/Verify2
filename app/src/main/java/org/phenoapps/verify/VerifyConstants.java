package org.phenoapps.verify;

import android.Manifest;

class VerifyConstants {

    final static String[] permissions = new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.CAMERA};

    //request
    final static int PERM_REQ = 100;
    final static int LOADER_INTENT_REQ = 101;
    final static int CAMERA_INTENT_REQ = 102;
    final static int SETTINGS_INTENT_REQ = 103;
    final static int DEFAULT_CONTENT_REQ = 104;

    //extras
    final static String CSV_URI = "org.phenoapps.verify.CSV_URI";
    final static String ID_ARRAY_EXTRA = "org.phenoapps.verify.ID_ARRAY";
    final static String COL_ARRAY_EXTRA = "org.phenoapps.verify.COL_ARRAY";

    final static String USER_ARRAY_EXTRA = "org.phenoapps.verify.USER_ARRAY";
    final static String DATE_ARRAY_EXTRA = "org.phenoapps.verify.DATE_ARRAY";
    final static String CHECKED_ARRAY_EXTRA = "org.phenoapps.verify.CHECKED_ARRAY";
    final static String SCAN_ARRAY_EXTRA = "org.phenoapps.verify.SCAN_ARRAY";

    final static String LIST_ID_EXTRA = "org.phenoapps.verify.LIST_ID_EXTRA";
    final static String HEADER_LIST_EXTRA = "org.phenoapps.verify.HEADER_LIST_EXTRA";
    final static String HEADER_DELIMETER_EXTRA = "org.phenoapps.verify.HEADER_DELIMITER_EXTRA";

    final static String CAMERA_RETURN_ID = "org.phenoapps.verify.CAMERA_RETURN_ID";
    final static String PAIR_COL_EXTRA = "org.phenoapps.verify.PAIR_COL_EXTRA";
    final static String PAIR_MODE_LOADER = "org.phenoapps.verify.PAIR_MODE_LOADER";

    final static String FILE_NAME = "org.phenoapps.verify.FILE_NAME";
}
