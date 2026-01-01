# Hướng dẫn sử dụng FaceRecognition

## Giới thiệu
Class `FaceRecognition` được thiết kế tương tự như `FaceRegistrar` nhưng dành cho việc **nhận diện khuôn mặt** thay vì đăng ký.

## Tính năng chính

1. **Nhận diện khuôn mặt với độ tin cậy cao**: Sử dụng lịch sử 7 kết quả gần nhất để đảm bảo độ chính xác
2. **Lưu ảnh khuôn mặt đã nhận diện**: Tự động lưu ảnh vào thư viện khi nhận diện thành công
3. **Xử lý ảnh từ gallery**: Hỗ trợ nhận diện từ ảnh được chọn từ thư viện
4. **Xử lý quyền storage**: Tự động yêu cầu quyền khi cần thiết

## Cách sử dụng

### 1. Khởi tạo

```java
// Trong Activity của bạn (ví dụ: MainActivity.java)
FaceStorage faceStorage = new FaceStorage(this);
FaceProcessor faceProcessor = new FaceProcessor(this, "mobile_face_net.tflite", 112, 192, false);

FaceRecognition faceRecognition = new FaceRecognition(this, faceStorage, faceProcessor);
```

### 2. Thiết lập Callback để nhận kết quả

```java
faceRecognition.setRecognitionCallback(new FaceRecognition.RecognitionCallback() {
    @Override
    public void onRecognized(String name, float confidence) {
        // Được gọi mỗi frame khi phát hiện khuôn mặt
        Log.d("Recognition", "Detected: " + name + " (confidence: " + confidence + ")");
        textViewName.setText(name);
    }

    @Override
    public void onUnknown() {
        // Được gọi khi không nhận diện được
        textViewName.setText("Unknown");
    }

    @Override
    public void onConfidentRecognition(String name, Bitmap croppedBitmap, Bitmap scaledBitmap) {
        // Được gọi khi có kết quả chắc chắn (7/7 frames giống nhau)
        Toast.makeText(MainActivity.this, "Đã nhận diện: " + name, Toast.LENGTH_LONG).show();
        imageView.setImageBitmap(croppedBitmap);
        
        // Ảnh sẽ được tự động lưu vào thư viện
        // Có thể dừng camera hoặc thực hiện hành động khác
    }
    
    @Override
    public void onFaceNotAligned(String reason) {
        // Được gọi khi khuôn mặt không đúng vị trí
        textViewName.setText(reason);
    }
    
    @Override
    public void onRecognitionStopped() {
        // Được gọi khi recognition tự động dừng
        button.setText("Start Recognition");
    }
});
```

### 3. Start/Stop Recognition

```java
// Bắt đầu nhận diện
faceRecognition.startRecognition();

// Dừng nhận diện
faceRecognition.stopRecognition();

// Kiểm tra trạng thái
boolean isActive = faceRecognition.isRecognizing();
```

### 4. Xử lý từng frame camera

Trong callback của `FaceProcessor` khi xử lý frame:

```java
faceProcessor.processFrame(imageProxy, executor, new FaceProcessor.Callback() {
    @Override
    public void onNoFace() {
        // Không có khuôn mặt
    }

    @Override
    public void onFaceDetected(Bitmap cropped, Bitmap scaled, float[] embedding) {
        // Có khuôn mặt - gọi FaceRecognition để nhận diện
        // Chỉ nhận diện khi isRecognizing = true
        runOnUiThread(() -> {
            faceRecognition.processRecognition(cropped, scaled, embedding);
        });
    }

    @Override
    public void onError(Exception e) {
        Log.e("FaceProcessor", "Error", e);
    }
}, false);
```

### 5. Nhận diện từ ảnh Gallery

```java
// Khi người dùng chọn ảnh từ gallery
Bitmap selectedBitmap = ...; // Ảnh từ gallery
boolean flipX = false; // Có mirror ảnh không

faceRecognition.processBitmapAndRecognize(selectedBitmap, flipX);
```

### 6. Xử lý quyền trong Activity

```java
@Override
public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    
    // Để FaceRecognition xử lý quyền storage
    if (faceRecognition.onRequestPermissionsResult(requestCode, grantResults)) {
        return; // Đã xử lý
    }
    
    // Xử lý các quyền khác...
}
```

### 7. Clear lịch sử nhận diện (optional)

```java
// Reset lịch sử khi cần (ví dụ: khi switch camera)
faceRecognition.clearHistory();

// Kiểm tra số lượng nhận diện trong lịch sử
int historySize = faceRecognition.getHistorySize();
```

## Cấu hình

Các tham số có thể điều chỉnh trong class:

```java
private static final int RECOGNITION_HISTORY_SIZE = 7; // Số frame lưu lại
private static final int MIN_CONFIDENT_HITS = 7;       // Số frame giống nhau để chắc chắn
```

- Tăng `RECOGNITION_HISTORY_SIZE` để nhận diện chậm hơn nhưng chính xác hơn
- Giảm để nhận diện nhanh hơn nhưng có thể kém chính xác

## Lưu ý

1. **Ảnh tự động lưu**: Khi nhận diện thành công, ảnh sẽ được tự động lưu vào `Pictures/FaceRecognition/` với tên `recognized_[name]_[timestamp].jpg`

2. **Quyền cần thiết**:
   - Android < Q: `WRITE_EXTERNAL_STORAGE`
   - Android >= Q: Không cần quyền đặc biệt (Scoped Storage)

3. **Thread-safe**: Luôn gọi `processRecognition()` trên UI thread nếu cần cập nhật UI

4. **FaceStorage**: Đảm bảo `FaceStorage` đã có dữ liệu khuôn mặt từ MQTT hoặc đăng ký trước

## So sánh với FaceRegistrar

| Feature | FaceRegistrar | FaceRecognition |
|---------|--------------|-----------------|
| Mục đích | Đăng ký khuôn mặt mới | Nhận diện khuôn mặt |
| Input | User nhập ID | Tự động tìm trong DB |
| Output | Gửi lên MQTT | Trả về tên người |
| Dialog | Hiển thị input dialog | Hiển thị kết quả |
| Smoothing | Không | Có (7 frames) |
| Save image | Lưu với ID | Lưu với tên nhận diện |
| Manual Control | Luôn hoạt động | Start/Stop với button |

