# Mô tả ngắn 5 thuật toán lập lịch

## Quy ước chung

Khởi tạo `remainingTime = burstTime`; `startTime` và `completionTime` chưa có. Mô phỏng bắt đầu tại 0. Khi không có tiến trình sẵn sàng, CPU idle đến arrival kế tiếp và ghi khoảng `IDLE` vào execution trace. Ghi riêng từng khoảng chạy `[start, end)`; lần chạy đầu xác định `startTime`, còn `completionTime` được ghi khi `remainingTime = 0`. Mỗi lượt chạy cập nhật thời gian hiện tại và trừ thời lượng chạy khỏi `remainingTime`.

Đầu vào: `processId` duy nhất, `arrivalTime >= 0`, `burstTime > 0`; `quantum > 0` cho Round Robin. Thời gian là số nguyên; priority nhỏ hơn nghĩa là ưu tiên cao hơn.

## FCFS (không ngắt)

Chạy lần lượt theo arrival tăng dần, hòa theo `processId` tăng dần. Nếu CPU rỗi, ghi idle đến arrival kế tiếp. Mỗi tiến trình chạy liên tục đến hoàn tất; ghi start, completion và trace.

## SJF (không ngắt)

Chọn trong số tiến trình đã đến tiến trình có `burstTime` nhỏ nhất; hòa theo arrival sớm hơn rồi `processId` tăng dần. Nếu chưa có tiến trình sẵn sàng, ghi idle đến arrival kế tiếp. Tiến trình được chọn chạy đến hoàn tất rồi mới chọn lại.

## Priority (không ngắt)

Chọn tiến trình đã đến có priority nhỏ nhất; hòa theo arrival sớm hơn rồi `processId` tăng dần. Nếu chưa có tiến trình sẵn sàng, ghi idle đến arrival kế tiếp. Tiến trình chạy đến hoàn tất, không bị ngắt; không dùng aging.

## SRTF (có ngắt)

Chọn tiến trình đã đến có `remainingTime` nhỏ nhất. Nếu tiến trình đang chạy vẫn tối ưu, kể cả khi hòa, tiếp tục chạy; nếu không, hòa theo arrival sớm hơn rồi `processId` tăng dần. Chạy đến khi hoàn tất hoặc có tiến trình mới đến, ghi trace và cập nhật thời gian còn lại; sau mỗi arrival hoặc completion, chọn lại. CPU idle đến arrival kế tiếp nếu không có tiến trình sẵn sàng.

## Round Robin (có ngắt)

Với `quantum > 0`, phục vụ FIFO; tiến trình cùng arrival được xếp theo `processId` tăng dần. Nếu hàng đợi rỗng, ghi idle đến arrival kế tiếp. Mỗi lượt chạy kéo dài `min(quantum, remainingTime)`; cập nhật trace, start và remaining time. Sau lượt chạy, đưa các tiến trình mới đến vào hàng đợi trước tiến trình vừa hết quantum, kể cả arrival đúng lúc quantum kết thúc. Ghi completion nếu tiến trình đã xong; nếu chưa, đưa nó về cuối hàng đợi.
