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
- Ngang: không còn hình nổi, thay bằng **thanh vuốt nhỏ ở rìa** (mặc định cạnh trái, đổi trong Tùy chỉnh > Thanh vuốt). Vuốt vào hoặc chạm để mở; kéo dọc để dời thanh lên/xuống.
- Mở ra là **hình chữ nhật gần nửa màn hình** và **thanh dọc** bên trái nó: 3 tab **Đọc / YouTube / Google** và 3 nút **Phát / Dừng / Tua** (Tua: chạm = tới 10 giây, nhấn giữ = lùi 10 giây). Chạm ra ngoài hoặc nút mũi tên xuống để thu lại.
- Khi khung thu lại, thanh vuốt chỉ còn thanh nhỏ, **không còn nút Phát / Dừng / Tua ở ngoài**; muốn điều khiển thì vuốt mở khung rồi dùng thanh dọc bên trong.
- Tab **Google** mở trang google.com ngay trong khung, giống tab YouTube (dọc cũng có).
- YouTube đã bấm phát thì giữ phát khi thu nhỏ / phóng to / xoay; chỉ dừng khi bấm Dừng (hoặc tự chạm tạm dừng trong video).
- Nếu video bị dừng không do người dùng (vào app khác, app khác chiếm âm thanh...), app tự phát lại ngay thay vì dừng hẳn.
