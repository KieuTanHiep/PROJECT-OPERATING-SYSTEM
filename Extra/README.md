# PROJECT-OPERATING-SYSTEM
# CPU Scheduling Algorithm Comparison

Dự án mô phỏng và so sánh 5 thuật toán lập lịch CPU: FCFS, SJF, Priority không ngắt, SRTF, Round Robin có ngắt. Mục tiêu là chạy các thuật toán trên cùng workload, quan sát execution trace/Gantt chart và so sánh các chỉ số lập lịch.

> **Trạng thái:** Đã có ứng dụng desktop Swing chạy được, năm thuật toán lập lịch, Gantt/trace, bảng chỉ số, nạp workload CSV và xuất kết quả CSV. Có unit test JUnit cho lịch chạy và xử lý CSV.

## Năm thuật toán

- **FCFS:** không ngắt, theo thứ tự đến; hòa theo `processId`.
- **SJF:** không ngắt, chọn burst ngắn nhất trong các tiến trình đã đến.
- **SRTF:** có ngắt, chọn thời gian còn lại ngắn nhất; giữ tiến trình đang chạy nếu vẫn tối ưu.
- **Priority:** không ngắt; số priority nhỏ hơn có độ ưu tiên cao hơn.
- **Round Robin:** có ngắt theo `quantum`, phục vụ theo hàng đợi FIFO.

Quy tắc tie-break, CPU idle, preemption, công thức chỉ số và các giả định mô hình được mô tả trong [scheduling-rules.md](./CPU%20Scheduling%20algorithm/docs/scheduling-rules.md).

## Chạy ứng dụng

Cần cài Java 21 và Maven. Từ thư mục dự án:

```powershell
cd "CPU Scheduling algorithm"
mvn test
mvn package
java -cp target/classes GUI.SchedulingGUI
```

GUI mở với danh sách tiến trình và kết quả trống. Dùng **Thêm tiến trình** để nhập thủ công hoặc chọn **Nạp CSV** để nạp workload từ `input/sample.csv`. Khi nạp CSV, danh sách hiện tại được thay thế hoàn toàn. Chọn thuật toán, đặt quantum khi chạy Round Robin, rồi nhấn **Chạy mô phỏng**. **Xuất kết quả CSV** lưu trace, chỉ số từng tiến trình và tổng hợp vào `output/schedule-results.csv` (có thể chọn đường dẫn khác).

## Định dạng CSV

Tệp đầu vào dùng UTF-8, có dòng tiêu đề theo đúng thứ tự:

```csv
processId,arrivalTime,burstTime,priority
P1,0,7,3
P2,2,4,1
```

Mỗi `processId` phải duy nhất; `arrivalTime` là số nguyên không âm; `burstTime` là số nguyên dương. Priority nhỏ hơn có độ ưu tiên cao hơn. Quantum là cấu hình của Round Robin trên GUI, không phải một cột của workload.

Chỉ duy trì một CSV dữ liệu mẫu là `CPU Scheduling algorithm/input/sample.csv`. Test dùng cùng workload này để đối chiếu expected trace của cả năm thuật toán (quantum 2 cho Round Robin); expected được giữ trong unit test để tránh có nhiều CSV kết quả mẫu trùng lặp. Khi chạy GUI và xuất kết quả, CSV output được tạo tại vị trí người dùng chọn, mặc định `output/schedule-results.csv`; file chứa các dòng `TRACE`, `PROCESS`, `SUMMARY`.

## Tài liệu

Các đường dẫn dưới đây tính từ thư mục `CPU Scheduling algorithm/`:

- `docs/system-design.md`: mô hình Input → Scheduler → Output.
- `docs/pseudocode.md`: mã giả của 5 thuật toán.
- `docs/flowcharts/`: lưu đồ [FCFS](./CPU%20Scheduling%20algorithm/docs/flowcharts/FCFS.jpg), [SJF](./CPU%20Scheduling%20algorithm/docs/flowcharts/SJF.jpg), [SRTF](./CPU%20Scheduling%20algorithm/docs/flowcharts/SRTF.jpg), [Priority](./CPU%20Scheduling%20algorithm/docs/flowcharts/Priority.jpg) và [Round Robin](./CPU%20Scheduling%20algorithm/docs/flowcharts/RoundRobin.jpg).
- `docs/scheduling-rules.md`: quy ước tie-break, CPU idle, preemption và công thức chỉ số.
- `input/sample.csv`: workload mẫu gồm các tiến trình đến cùng lúc, độ ưu tiên khác nhau và một tiến trình đến muộn để kiểm tra khoảng CPU idle.
- `tests/test-plan.csv`: kế hoạch kiểm thử chức năng, dữ liệu lỗi, expected/actual và xuất CSV.

## Công nghệ

- Java 21
- Maven
- JUnit Jupiter 5 (phụ thuộc kiểm thử đã khai báo trong `CPU Scheduling algorithm/pom.xml`)

Mã nguồn ứng dụng và kiểm thử Java nằm dưới `src/main/java/` và `src/test/java/`.


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
    │   ├── main/java/
    │   │   ├── Algorithm/       # Các thuật toán lập lịch
    │   │   └── GUI/             # GUI, điều phối, CSV và kết quả
    │   └── test/java/           # Kiểm thử JUnit
    ├── tests/
    │   └── test-plan.csv
    └── pom.xml
```
