//package mqtt;
//
//
//import android.content.ContentValues;
//import android.content.Context;
//import android.database.sqlite.SQLiteDatabase;
//import android.graphics.Bitmap;
//import android.graphics.BitmapFactory;
//
//import java.io.ByteArrayInputStream;
//
//public class ImageRepository {
//    public static void saveImage(Context context, byte[] imageBytes) {
//        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
//        if (bitmap != null) {
//            DatabaseHelper dbHelper = new DatabaseHelper(context);
//            SQLiteDatabase db = dbHelper.getWritableDatabase();
//            ContentValues values = new ContentValues();
//            values.put("image_data", imageBytes);
//            db.insert("images", null, values);
//            db.close();
//        }
//    }
//}
//
