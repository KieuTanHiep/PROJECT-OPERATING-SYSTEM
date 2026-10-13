# PROJECT-OPERATING-SYSTEM
# CPU Scheduling Algorithm Comparison

Dự án mô phỏng và so sánh 5 thuật toán lập lịch CPU: FCFS, SJF, Priority không ngắt, SRTF, Round Robin có ngắt. Mục tiêu là chạy các thuật toán trên cùng workload, quan sát execution trace/Gantt chart và so sánh các chỉ số lập lịch.

> **Trạng thái:** Hiện có tài liệu thiết kế, mã giả, quy tắc lập lịch và kế hoạch kiểm thử. Các thuật toán, ứng dụng chạy mô phỏng, Gantt chart và kiểm thử Java chưa được triển khai; các chức năng dưới đây là dự kiến.

## Năm thuật toán

- **FCFS:** không ngắt, theo thứ tự đến; hòa theo `processId`.
- **SJF:** không ngắt, chọn burst ngắn nhất trong các tiến trình đã đến.
- **SRTF:** có ngắt, chọn thời gian còn lại ngắn nhất; giữ tiến trình đang chạy nếu vẫn tối ưu.
- **Priority:** không ngắt; số priority nhỏ hơn có độ ưu tiên cao hơn.
- **Round Robin:** có ngắt theo `quantum`, phục vụ theo hàng đợi FIFO.

Quy tắc tie-break, CPU idle, preemption, công thức chỉ số và các giả định mô hình được mô tả trong [scheduling-rules.md](./CPU%20Scheduling%20algorithm/docs/scheduling-rules.md).

## Tài liệu

Các đường dẫn dưới đây tính từ thư mục `CPU Scheduling algorithm/`:

- `docs/system-design.md`: mô hình Input → Scheduler → Output và kiến trúc dự kiến.
- `docs/pseudocode.md`: mã giả của 5 thuật toán.
- `docs/flowcharts/`: thư mục dành cho lưu đồ thuật toán.
- `docs/scheduling-rules.md`: quy ước tie-break, CPU idle, preemption và công thức chỉ số.
- `input/sample.csv`: vị trí workload mẫu (tệp hiện đang trống).
- `tests/test-plan.csv`: kế hoạch các ca kiểm thử.
- `tests/expected-results.csv`: kết quả chuẩn dự kiến cho workload.

## Công nghệ

- Java 21
- Maven
- JUnit Jupiter 5 (phụ thuộc kiểm thử đã khai báo trong `CPU Scheduling algorithm/pom.xml`)

Mã nguồn ứng dụng và kiểm thử Java dự kiến lần lượt đặt dưới `src/main/java/` và `src/test/java/`. 

## Cấu trúc thư mục

```text
PROJECT-OPERATING-SYSTEM/
├── README.md
└── CPU Scheduling algorithm/
    ├── docs/
    │   ├── flowcharts/          
    │   ├── pseudocode.md
    │   ├── scheduling-rules.md
    │   └── system-design.md
    ├── input/
    │   └── sample.csv           
    ├── src/
    │   ├── main/java/           # Mã nguồn dự kiến
    │   └── test/java/           # Kiểm thử Java dự kiến
    ├── tests/
    │   ├── expected-results.csv
    │   └── test-plan.csv
    └── pom.xml
```

## Chức năng dự kiến

- Kiểm tra dữ liệu tiến trình và cấu hình thuật toán.
- Mô phỏng năm thuật toán, ghi nhận CPU idle, thời điểm bắt đầu/hoàn tất và execution trace để dựng Gantt chart.
- Tính turnaround, waiting, response time, throughput và CPU utilization.
- Trình bày, so sánh kết quả trên cùng workload.

Đây là mục tiêu phát triển, không phải các chức năng đã được triển khai.


## Chạy kiểm thử

Cần cài Java 21 và Maven. Từ thư mục dự án:

```bash
cd "CPU Scheduling algorithm"
mvn test
```


