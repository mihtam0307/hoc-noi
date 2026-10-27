# Học Nổi

App Android (Kotlin, không thư viện ngoài): app gốc + app nổi đọc tự động, lưu Drive, tự cập nhật.

## 1. Máy chủ Drive (Apps Script)
1. script.google.com > Dự án mới > dán nội dung `server/Code.gs`.
2. Chạy hàm `setup` 1 lần, cấp quyền Gmail + Drive.
3. Deploy > New deployment > Web app > Execute as: **Me**, Who has access: **Anyone**.
4. Copy link `…/exec`, dán vào ô "Link máy chủ" trong app (hoặc điền `Config.SERVER_URL` rồi build).
Dữ liệu lưu trong thư mục **HocNoi** trên Drive của chủ script (mỗi email 1 file).

## 2. Build + tự cập nhật (GitHub Actions)
Secrets cần có (Settings > Secrets > Actions) để ký APK, **dùng cùng 1 khóa cho mọi bản**:
- `KEYSTORE_BASE64` (nội dung file .jks mã hóa base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`

Mỗi lần push, workflow build APK ký sẵn rồi tạo Release `v<số lần chạy>` kèm `hoc-noi.apk`.
App mở lên sẽ hỏi GitHub bản mới nhất, thấy số lớn hơn thì hiện nút "Cập nhật ngay" kèm thanh tiến độ.

- Kho mã nguồn **công khai**: không cần làm gì thêm, sửa `Config.UPDATE_REPO` cho đúng `user/repo`.
- Kho mã nguồn **riêng tư**: tạo thêm 1 kho công khai chứa Releases, đặt biến Actions `RELEASE_REPO` = `user/kho-releases`,
  secret `RELEASES_TOKEN` = token có quyền ghi Releases của kho đó, và sửa `Config.UPDATE_REPO`.

## 3. Giao diện mới (v2)
- Mặc định màu **trắng**; mọi icon đều tự vẽ (`Widgets.kt`), không dùng icon/emoji của hệ thống.
- App nổi có 2 tab: **Đọc** (đọc tự động, lặp, công thức) và **YouTube** (WebView m.youtube.com, ô tìm kiếm + gợi ý thời gian thực).
- Thu nhỏ khung: tiếng đọc / video vẫn chạy; hình nổi hiện **thanh nhạc chuyển động** khi đang phát. Chạm hình nổi để mở menu nhỏ (phát/tạm dừng, dừng đọc, Đọc tự động, YouTube, Cài đặt, Tắt app nổi).
- Khung phủ kín màn hình, cách rìa 10dp, tự theo hướng xoay (cửa sổ MATCH_PARENT + đệm theo vùng an toàn); WebView được giữ nguyên khi xoay nên video không bị ngắt.

## 4. Màn hình ngang (v3)
- Xoay ngang: hình nổi tròn ẩn đi, thay bằng **thanh nhỏ ở mép trái** (kéo lên/xuống để dời chỗ). Vuốt thanh sang phải (hoặc chạm) để mở khung.
- Khung chiếm khoảng **nửa màn hình** (tab Đọc / YouTube giữ nguyên). Chạm vùng trống bên ngoài khung để đóng.
- Xoay dọc thì quay lại hình nổi tròn như cũ.
