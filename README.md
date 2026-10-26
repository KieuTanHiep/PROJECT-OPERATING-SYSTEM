# PROJECT-OPERATING-SYSTEM

# CPU Scheduling Algorithm Comparison

Ứng dụng desktop Java Swing mô phỏng và so sánh năm thuật toán lập lịch CPU: FCFS, SJF, Priority, SRTF và Round Robin. Chương trình hiển thị Gantt chart, kết quả từng tiến trình và các chỉ số tổng hợp; hỗ trợ nạp workload và xuất kết quả CSV.

## Cấu trúc dự án

- `Code/`: mã nguồn ứng dụng, workload CSV, test, cấu hình Maven và thư mục build.
- `DOCX/docs/`: tài liệu thiết kế, quy tắc, mã giả và lưu đồ thuật toán.
- `Extra/`: tài liệu bổ sung.
- `PPTX/`: tài liệu trình bày.

## Thuật toán

- **FCFS:** không ngắt, theo thời điểm đến; nếu hòa thì theo `processId`.
- **SJF:** không ngắt, chọn burst ngắn nhất trong các tiến trình đã đến.
- **SRTF:** có ngắt, chọn thời gian còn lại ngắn nhất; giữ tiến trình đang chạy nếu vẫn tối ưu.
- **Priority:** không ngắt; số priority nhỏ hơn có độ ưu tiên cao hơn.
- **Round Robin:** có ngắt theo `quantum`, phục vụ theo FIFO.

Quy tắc tie-break, CPU idle, preemption và công thức chỉ số nằm trong [scheduling-rules.md](./DOCX/docs/scheduling-rules.md).

## Chạy ứng dụng

Cần cài Java 21 và Maven. Từ thư mục gốc repository:

```powershell
cd Code
mvn clean test
mvn package
java -cp target/classes gui.SchedulingGUI
```

Hoặc chạy test mà không đổi thư mục hiện tại:

```powershell
mvn -f "Code/pom.xml" clean test
```

GUI khởi động với danh sách tiến trình trống. Dùng **Thêm tiến trình** để nhập thủ công hoặc chọn **Nạp CSV** để nạp `Code/input/data.csv`. Khi nạp, dữ liệu hiện tại được thay thế hoàn toàn. Chọn thuật toán, đặt quantum khi chạy Round Robin, rồi bấm **Chạy mô phỏng**. **Xuất kết quả CSV** mở hộp thoại lưu kết quả, mặc định tại `Code/output/schedule-results.csv`.

## Định dạng workload CSV

CSV đầu vào dùng UTF-8 và có header theo đúng thứ tự:

```csv
processId,arrivalTime,burstTime,priority
```

Mỗi `processId` phải duy nhất; `arrivalTime` là số nguyên không âm; `burstTime` là số nguyên dương. Priority nhỏ hơn nghĩa là ưu tiên cao hơn. Quantum là cấu hình Round Robin trên GUI, không phải cột trong CSV.

Chỉ có một workload CSV mẫu tại [data.csv](./Code/input/data.csv). Unit test dùng workload này để so sánh timeline mong đợi và thực tế cho cả năm thuật toán (quantum 2 cho Round Robin). Kế hoạch các ca kiểm thử thủ công nằm trong [test-plan.csv](./Code/tests/test-plan.csv).

CSV kết quả gồm ba loại dòng: `TRACE` (các đoạn chạy và `IDLE`), `PROCESS` (thời điểm và chỉ số từng tiến trình), `SUMMARY` (các chỉ số trung bình, CPU utilization dạng tỷ lệ 0–1 và throughput).

## Mã nguồn và test

- [SchedulingGUI.java](<./Code/src/main/java/GUI/SchedulingGUI.java>): giao diện desktop.
- [CpuScheduler.java](<./Code/src/main/java/GUI/CpuScheduler.java>): kiểm tra dữ liệu, điều phối thuật toán và tổng hợp kết quả.
- `Code/src/main/java/Algorithm/`: 5 thuật toán trong package riêng `Algorithm`.
- `Code/src/main/java/GUI/CsvScheduleFiles.java`: đọc workload và ghi kết quả CSV.
- `Code/src/test/java/`: kiểm thử tự động cho thuật toán và CSV; chạy bằng `mvn test`.

## Tài liệu

- [system-design.md](./DOCX/docs/system-design.md): luồng xử lý và thành phần hệ thống.
- [pseudocode.md](./DOCX/docs/pseudocode.md): mã giả năm thuật toán.
- [scheduling-rules.md](./DOCX/docs/scheduling-rules.md): quy ước và công thức.
- [Flowcharts](./DOCX/docs/flowcharts/): lưu đồ FCFS, SJF, SRTF, Priority và Round Robin.

## Công nghệ và giới hạn

- Java 21, Maven, Swing, JUnit Jupiter 5.
- Mô hình một CPU, biết trước CPU burst, không có I/O blocking, priority aging hoặc chi phí chuyển ngữ cảnh.
- Kế hoạch stress/performance test nằm trong `Code/tests/test-plan.csv`; cần chạy và ghi số liệu, cấu hình máy, phân tích độ phức tạp vào báo cáo. Báo cáo cuối cùng và video demo công khai do nhóm bổ sung.

## Cấu trúc thư mục

```text
PROJECT-OPERATING-SYSTEM/
├── Code/
│   ├── input/data.csv
│   ├── output/
│   ├── src/
│   │   ├── main/java/
│   │   │   ├── Algorithm/
│   │   │   └── GUI/
│   │   └── test/java/
│   ├── tests/test-plan.csv
│   └── pom.xml
├── DOCX/docs/
│   ├── flowcharts/
│   ├── pseudocode.md
│   ├── scheduling-rules.md
│   └── system-design.md
├── Extra/
├── PPTX/
└── README.md
```
