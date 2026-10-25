# Thiết kế hệ thống

## Mục tiêu

Mô phỏng cùng một tập tiến trình bằng FCFS, SJF, SRTF, Priority và Round Robin; xuất biểu đồ Gantt và các chỉ số để so sánh tính công bằng, thời gian chờ và thông lượng.

## Luồng xử lý

```mermaid
flowchart LR
    I[Input: process list + algorithm config] --> V[Validate input]
    V --> S[Scheduler]
    S --> T[Execution timeline / Gantt]
    T --> M[Metrics calculator]
    M --> O[Output: timeline + comparison table]
```

## Dữ liệu vào

Mỗi tiến trình có `processId`, `arrivalTime`, `burstTime` và `priority`; CSV UTF-8 có header `processId,arrivalTime,burstTime,priority`. `processId` phải duy nhất, `arrivalTime` không âm, `burstTime` phải lớn hơn 0. Priority có số nhỏ hơn là độ ưu tiên cao hơn. Round Robin nhận thêm `quantum` nguyên dương từ giao diện, không phải từ CSV.

## Các thành phần

- `model`: `Process`, `ScheduleResult`, `ExecutionSlice` và cấu hình thuật toán.
- `algorithm`: giao diện lập lịch và năm cài đặt thuật toán; chỉ quyết định tiến trình kế tiếp/chạy tiếp.
- `simulation`: mô phỏng thời gian, xử lý tiến trình đến và CPU idle, ghi timeline.
- `metrics`: tính completion, turnaround, waiting, response, throughput và CPU utilization từ timeline.
- `gui`: nhận workload/cấu hình và trình bày kết quả qua giao diện Swing.

Tách bộ mô phỏng khỏi thuật toán giúp dùng chung cách ghi timeline và tính chỉ số; nhờ đó kết quả các thuật toán có thể so sánh trên cùng quy ước.

## Kết quả

Mỗi lần chạy trả về thứ tự các execution slice, thời điểm hoàn thành của từng tiến trình, Gantt chart và bảng chỉ số. Tiến trình đang chạy không tạo slice mới nếu tiếp tục chạy liên tục; các đoạn CPU idle được biểu diễn riêng. Khi chạy từ thư mục `Code/`, GUI có thể nạp workload mẫu từ `input/data.csv` và xuất CSV kết quả gồm các dòng `TRACE`, `PROCESS`, `SUMMARY` đến vị trí người dùng chọn. Expected trace cho workload mẫu của các thuật toán được đối chiếu trong unit test, không lưu thêm CSV kết quả trùng dữ liệu.
