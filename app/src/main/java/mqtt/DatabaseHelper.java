//package mqtt;
//
//import android.content.Context;
//import android.database.sqlite.SQLiteDatabase;
//import android.database.sqlite.SQLiteOpenHelper;
//
//public class DatabaseHelper extends SQLiteOpenHelper {
//    private static final String DB_NAME = "mqtt_images.db";
//    private static final int DB_VERSION = 1;
//
//    public DatabaseHelper(Context context) {
//        super(context, DB_NAME, null, DB_VERSION);
//    }
//
//    @Override
//    public void onCreate(SQLiteDatabase db) {
//        db.execSQL("CREATE TABLE images (id INTEGER PRIMARY KEY AUTOINCREMENT, image_data BLOB)");
//    }
//
//    @Override
//    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
//        db.execSQL("DROP TABLE IF EXISTS images");
//        onCreate(db);
//    }
//}
