# MainController - Cập nhật tích hợp FaceRecognition

## ✅ Đã hoàn thành

MainController đã được cập nhật để tích hợp hoàn chỉnh với class `FaceRecognition`.

## Các thay đổi chính

### 1. **Thêm FaceRecognition field**
```java
private FaceRecognition faceRecognition;
```
Thay thế cho FaceRegistrar (đã bị comment).

### 2. **Khởi tạo FaceStorage**
```java
// Initialize FaceStorage
faceStorage = new FaceStorage(activity);
```
FaceStorage được khởi tạo trong `start()` để lưu trữ và tìm kiếm khuôn mặt.

### 3. **Khởi tạo FaceRecognition với callback**
```java
// Initialize FaceRecognition
faceRecognition = new FaceRecognition(activity, faceStorage, faceProcessor);

// Setup recognition callback
faceRecognition.setRecognitionCallback(new FaceRecognition.RecognitionCallback() {
    @Override
    public void onRecognized(String name, float confidence) {
        // Cập nhật UI với tên và độ tin cậy
        activity.runOnUiThread(() -> {
            recoName.setText(name + " (" + String.format("%.2f", confidence) + ")");
        });
    }

    @Override
    public void onUnknown() {
        // Hiển thị Unknown khi không nhận diện được
        activity.runOnUiThread(() -> {
            recoName.setText("Unknown");
        });
    }

    @Override
    public void onConfidentRecognition(String name, Bitmap croppedBitmap, Bitmap scaledBitmap) {
        // Nhận diện chắc chắn (7/7 frames)
        activity.runOnUiThread(() -> {
            recoName.setText("✓ " + name);
            facePreview.setImageBitmap(croppedBitmap);
            Toast.makeText(activity, "Đã nhận diện: " + name, Toast.LENGTH_LONG).show();
        });
    }
    
    @Override
    public void onFaceNotAligned(String reason) {
        // Hiển thị hướng dẫn căn chỉnh
        activity.runOnUiThread(() -> recoName.setText(reason));
    }
    
    @Override
    public void onRecognitionStopped() {
        // Cập nhật button khi recognition dừng
        activity.runOnUiThread(() -> {
            isRecognitionActive = false;
            recognizeBtn.setText("Start Recognition");
        });
    }
});
```

### 4. **Tích hợp vào xử lý frame**
Trong callback `onFaceDetected()`:
```java
@Override
public void onFaceDetected(Bitmap cropped, Bitmap scaled, float[] embedding) {
    // ... existing code for saving bitmaps ...
    
    // Chỉ nhận diện khi cả hai điều kiện đều đúng
    if (isRecognitionActive && isFaceAligned) {
        faceRecognition.processRecognition(cropped, scaled, embedding);
    }
    
    // ... existing code for displaying ...
}
```

### 5. **Face Alignment Check**
```java
// Setup alignment callback
cameraManager.setAlignmentCallback(new CameraHelper.AlignmentCallback() {
    @Override
    public void onFaceAligned() {
        isFaceAligned = true;
    }

    @Override
    public void onFaceNotAligned(String reason) {
        isFaceAligned = false;
        if (isRecognitionActive) {
            activity.runOnUiThread(() -> recoName.setText(reason));
        }
    }
});
```

### 6. **Toggle Recognition Method**
```java
public void toggleRecognition() {
    if (faceRecognition != null) {
        if (isRecognitionActive) {
            // Stop recognition
            faceRecognition.stopRecognition();
            isRecognitionActive = false;
            recognizeBtn.setText("Start Recognition");
            recoName.setText("Đã dừng nhận diện");
        } else {
            // Start recognition
            faceRecognition.startRecognition();
            isRecognitionActive = true;
            recognizeBtn.setText("Stop Recognition");
            recoName.setText("Đang nhận diện...");
        }
    }
}
```

### 7. **Clear history khi switch camera**
```java
public void toggleCamera() {
    if (cameraManager != null) {
        try {
            cameraManager.switchCamera((LifecycleOwner) activity);
            // Clear recognition history when switching cameras
            if (faceRecognition != null) {
                faceRecognition.clearHistory();
            }
            // ...
        }
    }
}
```

### 8. **Cleanup trong stop()**
```java
public void stop() {
    if (cameraManager != null) cameraManager.stop();
    if (faceProcessor != null) faceProcessor.close();
    if (faceRecognition != null) faceRecognition.clearHistory();
    bgExecutor.shutdownNow();
}
```

## Luồng hoạt động

```
1. User ấn "Start Recognition"
   ↓
2. toggleRecognition() → isRecognitionActive = true
   ↓
3. Camera capture frame
   ↓
4. CameraHelper.checkFaceAlignment()
   ↓
5a. NOT ALIGNED → onFaceNotAligned(reason) → Hiển thị hướng dẫn
   ↓
5b. ALIGNED → onFaceAligned() → isFaceAligned = true
   ↓
6. if (isRecognitionActive && isFaceAligned)
   ↓
7. faceRecognition.processRecognition(cropped, scaled, embedding)
   ↓
8. Tìm trong FaceStorage
   ↓
9. Callbacks:
   - onRecognized() → Hiển thị tên + confidence (mỗi frame)
   - onUnknown() → Hiển thị "Unknown"
   - onConfidentRecognition() → Kết quả chắc chắn (7/7 frames)
- Settings để điều chỉnh threshold alignment
- Statistics (số lần nhận diện mỗi người)
- Export recognized faces
- Progress bar hiển thị số frame đã match (x/7)
- Sound/vibration khi nhận diện thành công
Có thể thêm các tính năng:

## Next Steps (Optional)

8. **Face alignment**: Phải đúng vị trí mới nhận diện
7. **Manual control**: User phải ấn button để start/stop
6. **Auto stop**: Recognition tự động dừng sau khi thành công
5. **Auto save**: Ảnh tự động lưu khi nhận diện chắc chắn
4. **Camera switch**: History được clear để tránh nhận diện sai
3. **Memory**: Bitmaps được copy để tránh recycle bugs
2. **Thread safety**: Callbacks đã wrap trong `runOnUiThread()`
1. **FaceStorage phải có data**: Nhận từ MQTT hoặc đăng ký trước khi nhận diện

## Lưu ý quan trọng

```
Log.d("MainController", "Confident recognition: " + name);
Log.d("MainController", "Recognition stopped by user");
Log.d("MainController", "Recognition started by user");
```java
### Debug logs:

8. Ấn lại "Start Recognition" để tiếp tục
7. Recognition tự động dừng
6. Ảnh sẽ tự động lưu vào gallery
5. Giữ mặt ổn định để có kết quả chắc chắn (✓)
4. Căn chỉnh khuôn mặt theo hướng dẫn
3. Ấn "Start Recognition"
2. Chạy app
1. Đảm bảo FaceStorage đã có dữ liệu (từ MQTT hoặc đăng ký trước)
### Để test nhận diện:

## Testing

- Gọi `faceStorage.addFace(name, embedding)` khi nhận data
- MainActivity implement MqttListener
- Nhận face data từ server
### MQTT (được truyền vào constructor):

- Được khởi tạo trong MainController.start()
- Tìm kiếm nearest neighbor bằng Euclidean distance
- Lưu trữ embeddings vào SharedPreferences
### FaceStorage:

## Storage & MQTT Integration

6. **Phát hiện khuôn mặt**: Phải có face
5. **Góc nghiêng đầu (Euler Z)**: < 15°
4. **Góc quay đầu (Euler Y)**: < 15°
3. **Vị trí dọc**: Trong vòng 25% từ tâm
2. **Vị trí ngang**: Trong vòng 25% từ tâm
1. **Kích thước mặt**: 30% - 85% độ rộng màn hình

## Face Alignment Checks (6 điều kiện)

- Tự động dừng sau khi nhận diện thành công
- Khi switch camera → clear history để tránh kết quả sai
- **MIN_CONFIDENT_HITS = 7**: Cần 7/7 kết quả giống nhau để chắc chắn
- **RECOGNITION_HISTORY_SIZE = 7**: Lưu 7 kết quả gần nhất

## Tính năng Recognition Smoothing

- `"Stop Recognition"` - khi đang nhận diện
- `"Start Recognition"` - khi chưa nhận diện hoặc đã dừng
### Button:

- Cập nhật với ảnh cropped khi nhận diện chắc chắn
- Tự động mirror nếu là front camera
- Hiển thị ảnh scaled (112x112)
### ImageView:

- **Đã dừng**: `"Đã dừng nhận diện"`
- **Chắc chắn**: `"✓ Tên"` - có dấu check mark
- **Không nhận diện được**: `"Unknown"`
- **Đang nhận diện - aligned**: `"Tên (0.85)"` - tên + confidence score
- **Đang nhận diện - chưa aligned**: `"Đưa mặt gần hơn"`, `"Nhìn thẳng vào camera"`, etc.
- **Không nhận diện**: `"Ấn Start để nhận diện"`
### Hiển thị thông tin nhận diện:

## UI Updates

```
     → onRecognitionStopped()
     → Lưu ảnh
     → stopRecognition() tự động

