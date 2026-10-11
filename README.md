# PROJECT-OPERATING-SYSTEM
# CPU Scheduling Algorithm Comparison

Dự án mô phỏng và so sánh 5 thuật toán lập lịch CPU: FCFS, SJF không ưu tiên ngắt, SRTF, Priority không ưu tiên ngắt và Round Robin.

## Tài liệu

- `docs/system-design.md`: mô hình Input -> Scheduler -> Output.
- `docs/pseudocode.md`: mã giả của 5 thuật toán.
- `docs/flowcharts/`: lưu đồ từng thuật toán.
- `docs/scheduling-rules.md`: quy ước tie-break, CPU idle, preemption và công thức chỉ số.
- `input/sample.csv`: workload mẫu dùng để chạy mô phỏng.
- `tests/test-plan.csv`: các ca kiểm thử.
- `tests/expected-results.csv`: kết quả chuẩn cho các workload chính.

## Java

Dùng Java 21 và Maven. Mã nguồn đặt dưới `src/main/java/`, kiểm thử Java đặt dưới `src/test/java/`; kiến trúc package dự kiến được mô tả trong `docs/system-design.md`.

## Chạy kiểm thử

```bash
mvn test
```

