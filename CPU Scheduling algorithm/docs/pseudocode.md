# Mã giả 5 thuật toán

Các thuật toán chọn tiến trình trong hàng đợi sẵn sàng; quy tắc xử lý khi các tiến trình ngang nhau và cách xử lý thời điểm được định nghĩa tại `scheduling-rules.md`.

## FCFS (không ngắt)

```text
sắp xếp tiến trình theo thời điểm đến tăng dần, sau đó theo mã tiến trình
cho mỗi tiến trình theo thứ tự đã sắp xếp:
    nếu CPU đang rỗi, chuyển thời gian đến lúc tiến trình đến và ghi nhận khoảng rỗi
    chạy tiến trình đến khi thời gian CPU còn lại bằng 0
    ghi nhận quá trình thực thi và thời điểm hoàn tất
```

## SJF (không ngắt)

```text
lặp khi vẫn còn tiến trình chưa hoàn tất:
    thêm các tiến trình đã đến vào hàng đợi sẵn sàng
    nếu hàng đợi sẵn sàng trống: chuyển thời gian đến lúc tiến trình tiếp theo đến và ghi nhận CPU rỗi
    ngược lại:
        chọn tiến trình có thời gian CPU cần chạy ngắn nhất
        nếu bằng nhau, chọn tiến trình đến sớm hơn, sau đó chọn theo mã tiến trình
        chạy tiến trình đến khi hoàn tất
        ghi nhận quá trình thực thi và thời điểm hoàn tất
```

## SRTF (có ngắt)

```text
lặp khi vẫn còn tiến trình chưa hoàn tất:
    thêm các tiến trình mới đến vào hàng đợi sẵn sàng
    nếu hàng đợi sẵn sàng trống: chuyển thời gian đến lúc tiến trình tiếp theo đến và ghi nhận CPU rỗi
    ngược lại:
        chọn tiến trình có thời gian CPU còn lại ngắn nhất
        chạy tiến trình đến khi hoàn tất hoặc có tiến trình mới đến
        ghi nhận quá trình thực thi và cập nhật thời gian CPU còn lại
        chọn lại tiến trình mỗi khi có tiến trình mới đến
```

## Ưu tiên (Priority, không ngắt)

```text
lặp khi vẫn còn tiến trình chưa hoàn tất:
    thêm các tiến trình đã đến vào hàng đợi sẵn sàng
    nếu hàng đợi sẵn sàng trống: chuyển thời gian đến lúc tiến trình tiếp theo đến và ghi nhận CPU rỗi
    ngược lại:
        chọn tiến trình có số độ ưu tiên nhỏ nhất
        nếu bằng nhau, chọn tiến trình đến sớm hơn, sau đó chọn theo mã tiến trình
        chạy tiến trình đến khi hoàn tất
        ghi nhận quá trình thực thi và thời điểm hoàn tất
```

## Round Robin (có ngắt)

```text
sắp xếp tiến trình theo thời điểm đến tăng dần, sau đó theo mã tiến trình
lặp khi vẫn còn tiến trình chưa hoàn tất:
    đưa vào hàng đợi các tiến trình đã đến tính đến thời điểm hiện tại
    nếu hàng đợi sẵn sàng trống: chuyển thời gian đến lúc tiến trình tiếp theo đến và ghi nhận CPU rỗi
    ngược lại:
        lấy tiến trình ở đầu hàng đợi
        chạy trong khoảng thời gian nhỏ hơn giữa lát thời gian và thời gian CPU còn lại của tiến trình
        đưa các tiến trình đến trong lúc chạy vào hàng đợi trước khi đưa lại tiến trình vừa chạy
        nếu tiến trình vẫn còn thời gian CPU cần chạy: đưa tiến trình vào cuối hàng đợi
        ngược lại: ghi nhận thời điểm hoàn tất
```
