# CPU Scheduling — chạy, kiểm thử và đo hiệu năng

Ứng dụng desktop Java Swing mô phỏng FCFS, SJF, SRTF, Priority và Round Robin trên cùng một CPU. Tất cả lệnh dưới đây chạy từ thư mục `Code/`, trừ khi có ghi khác.

## Cài đặt và chạy

Cần **JDK 21** và **Maven** có trong `PATH`:

```powershell
java -version
mvn -version
mvn clean test package
java -jar target/cpu-scheduling-1.0-SNAPSHOT.jar
```

Có thể chạy trực tiếp lớp giao diện:

```powershell
java -cp target/classes gui.SchedulingGUI
```

Từ thư mục gốc repository, dùng `mvn -f Code/pom.xml clean test package`. Sau khi clone, Maven tự tải dependency trong lần build đầu tiên; cần kết nối mạng ở lần đó.

## Dữ liệu đầu vào

CSV UTF-8, dấu phẩy, header bắt buộc theo thứ tự:

```csv
processId,arrivalTime,burstTime,priority
P1,0,7,3
P2,2,4,1
```

- Mã tiến trình được bỏ khoảng trắng đầu/cuối, phải duy nhất, không rỗng và không chứa ký tự điều khiển. `IDLE` (không phân biệt hoa/thường) được dành riêng cho CPU rỗi.
- `arrivalTime` là số nguyên không âm; `burstTime` là số nguyên dương; `priority` là số nguyên, số nhỏ hơn nghĩa là ưu tiên cao hơn.
- Quantum nhập trên giao diện, là số nguyên dương; không nằm trong CSV.
- Thời gian mô phỏng nằm trong miền `int` đến `2147483647`; workload vượt miền này bị từ chối trước khi chạy.
- Tên có dấu phẩy hoặc dấu nháy kép dùng quy tắc quoted CSV, ví dụ `"P,1"`. Không hỗ trợ mã tiến trình nhiều dòng.

Các workload mẫu:

| File | Mục đích |
|---|---|
| `input/data.csv` | Bộ mẫu chung, có khoảng idle giữa phiên |
| `input/ties.csv` | Cùng arrival/burst/priority để kiểm tra tie-break |
| `input/preemption.csv` | SRTF ngắt khi tiến trình ngắn hơn đến |
| `input/initial-idle.csv` | CPU rỗi đầu phiên và giữa các nhóm tiến trình |
| `input/fairness.csv` | Một tiến trình dài và nhiều tiến trình ngắn để so sánh thời gian chờ |

FCFS hòa theo arrival rồi ID. SJF hòa burst theo arrival rồi ID. Priority không ngắt, hòa priority theo arrival rồi ID. SRTF giữ tiến trình đang chạy khi remaining time hòa; nếu không có tiến trình đang chạy, hòa theo arrival rồi ID. Round Robin là FIFO; tiến trình đến đúng lúc hết quantum được xếp trước tiến trình vừa bị ngắt.

## Giao diện và kết quả

Nạp một CSV hoặc nhập/sửa danh sách tiến trình, chọn thuật toán và quantum rồi bấm **Chạy thuật toán**. Tab **Chi tiết và Gantt** có biểu đồ theo trục thời gian, bảng ST/CT/TAT/WT/RT và các chỉ số tổng hợp. Gantt có mức phóng **Vừa khung**, 2× đến 32×; rê chuột lên đoạn chạy để xem thời gian chính xác.

Bấm **So sánh cả 5** để chạy cả năm thuật toán trên cùng workload. Tab **So sánh 5 thuật toán** giữ bảng kết quả; chọn một dòng cập nhật chi tiết tương ứng, nhấp đúp chuyển sang tab chi tiết.

Mô phỏng chạy ở luồng nền, có trạng thái đang xử lý và thao tác hủy. Khi thay đổi input hoặc cấu hình, kết quả trước đó phải chạy lại trước khi xuất. Nhập/xuất CSV có thông báo lỗi; không nạp một phần workload lỗi.

**Xuất kết quả đang xem** lưu chi tiết của thuật toán được chọn, mặc định `schedule-results.csv`, gồm `TRACE`, `PROCESS`, `SUMMARY`. **Xuất so sánh CSV** cho chọn **Bảng so sánh chỉ số** (`comparison-summary.csv`) hoặc **Toàn bộ trace và kết quả** (`comparison-details.csv`). Có xác nhận trước khi ghi đè tệp. Đơn vị utilization trong CSV là tỷ lệ `0..1`, trên GUI là phần trăm. Throughput dùng đơn vị tiến trình / đơn vị thời gian mô phỏng.

## Kiểm thử tự động

```powershell
mvn clean test
```

Build bật `javac -Xlint:all -Werror` cho mã chính và test: cảnh báo compiler làm build thất bại. `.editorconfig` quy định UTF-8, LF và thụt lề 4 dấu cách cho Java/XML.

- Các test mẫu kiểm tra timeline, công thức chỉ số và CSV.
- Các test biên kiểm tra ID dành riêng, dữ liệu không hợp lệ, tie-break, idle, preemption, ranh giới quantum, giới hạn thời gian và burst lớn.
- `SchedulerOracleTest` chạy **300 workload seed cố định × 5 thuật toán**, đối chiếu đầy đủ timeline, ST/CT/TAT/WT/RT và 5 chỉ số tổng hợp với oracle độc lập mô phỏng từng đơn vị thời gian. Một số ca oracle được kiểm chứng thêm bằng kết quả tính tay.
- Kết quả JUnit nằm ở `target/surefire-reports/`. Kế hoạch kiểm thử ở `tests/test-plan.csv` phân biệt ca tự động và ca GUI cần thao tác thực tế; kế hoạch không thay cho bằng chứng đã chạy.

Trong phiên desktop Windows, có thể kiểm tra thêm luồng GUI bằng cửa sổ ẩn:

```powershell
java -cp target/classes tests/GuiSmokeProbe.java
```

Công cụ kiểm tra chạy so sánh bằng luồng nền, nhập quantum, chọn dòng sau khi sắp xếp, vô hiệu kết quả cũ, hủy và chạy lại. Kết quả nằm ở `output/gui-smoke.txt`, cùng ảnh tab chi tiết và so sánh. Đây là thao tác tự động trên cửa sổ ẩn; hộp thoại chọn tệp, xác nhận ghi đè và báo lỗi vẫn cần kiểm tra trực tiếp. Lệnh này cần môi trường desktop và không thuộc bộ test headless của Maven.

## Chạy kiểm chứng và tạo kết quả có thể tái lập

Sau `mvn package`:

```powershell
java -cp target/classes tools.SchedulerVerification all output
```

Hoặc từ thư mục gốc repository:

```powershell
java -cp Code/target/classes tools.SchedulerVerification all Code/output
```

Chỉ kiểm chứng tính đúng hoặc chỉ đo hiệu năng:

```powershell
java -cp target/classes tools.SchedulerVerification check output
java -cp target/classes tools.SchedulerVerification benchmark output
```

Có thể thay tham số; mỗi lần chạy ghi đè các tệp kết quả tương ứng trong thư mục đã chọn:

```powershell
java -cp target/classes tools.SchedulerVerification all output --seed=20261108 --cases=300 --warmups=3 --repeats=7 --sizes=100,500,1000
```

`check` mặc định chạy 5 workload CSV mẫu × 5 thuật toán, 300 workload ngẫu nhiên × 5 thuật toán và 3 quantum bổ sung cho Round Robin: **1528 lượt đối chiếu**. Oracle dùng từng tick và chỉ dành cho dữ liệu nhỏ; stress test dùng bất biến để không mở rộng từng tick của workload lớn.

| Kết quả được tạo | Nội dung |
|---|---|
| `output/correctness-results.csv` | Expected và actual timeline, ST/CT/TAT/WT/RT, các chỉ số tổng hợp, PASS/FAIL và lỗi cụ thể |
| `output/correctness-environment.txt` | Seed, số ca và môi trường của lần kiểm chứng |
| `output/sample-results/*.csv` | Trace và kết quả chi tiết từng thuật toán trên workload mẫu |
| `output/comparisons/*-summary.csv` | Bảng thực nghiệm so sánh cả 5 thuật toán trên mỗi workload mẫu, gồm các chỉ số fairness |
| `output/comparisons/*-details.csv` | Trace, kết quả từng tiến trình và tổng hợp của cả 5 thuật toán |
| `output/benchmark-inputs/*.csv` | Workload chính xác dùng trong lần đo, có thể nạp vào GUI |
| `output/benchmark-raw.csv` | Từng lần đo, seed, kích thước, max burst, quantum, nanosecond và trạng thái bất biến |
| `output/benchmark-summary.csv` | Trung vị, nhỏ nhất, lớn nhất, milliseconds, số đoạn trace và makespan |
| `output/benchmark-environment.txt` | Java/OS/CPU/RAM/heap, seed, số warmup/repeat và phương pháp đo |

Nếu oracle hoặc bất biến sai, công cụ trả exit code khác 0 và ghi lỗi thực tế. Các kết quả GUI thao tác thủ công không được công cụ này tự xác nhận.

## Phương pháp đo và giới hạn

Benchmark mặc định dùng 100, 500, 1000 tiến trình; max burst 10, 100, 1000; quantum 5; seed gốc `20261108`. Mỗi cặp kích thước/burst dùng workload riêng với seed được ghi trong CSV. Có **3 warmup + 7 lần đo** cho mỗi thuật toán, thứ tự thuật toán được trộn bằng seed trong từng vòng đo. Tổng cộng **315 lần đo có kiểm tra bất biến**. Trung vị được dùng để giảm ảnh hưởng của các lần nhiễu.

Đồng hồ là `System.nanoTime()`. Khoảng đo gồm toàn bộ `CpuScheduler.schedule`: kiểm tra input, chuyển đổi dữ liệu, thuật toán, tính chỉ số và cấp phát timeline/kết quả. Sinh workload, đọc/ghi CSV, oracle/bất biến và vẽ GUI nằm ngoài khoảng đo. Không đặt ngưỡng thời gian PASS/FAIL vì JIT, GC và tải máy khác nhau. Đây là phép đo trong một JVM phục vụ so sánh dự án; không phải benchmark cô lập cấp hệ điều hành.

Gọi `n` là số tiến trình, `s` là số đoạn trace, `k` là số lượt Round Robin thực sự phải xử lý:

| Thuật toán | Thời gian | Bộ nhớ |
|---|---|---|
| FCFS | `O(n log n + s)` | `O(n + s)` |
| SJF / Priority | `O(n log n + s)` với hàng đợi ưu tiên | `O(n + s)` |
| SRTF | `O(n log n + s)`, xử lý arrival/completion, không lặp từng tick | `O(n + s)` |
| Round Robin | `O(n log n + k)`, gộp lượt khi chỉ có một tiến trình chạy | `O(n + s)` |

Một mô phỏng giới hạn tối đa **250000 đoạn trace** để tránh cạn bộ nhớ. Khi vượt giới hạn, tăng quantum của Round Robin hoặc giảm workload; không cắt bỏ trace rồi báo kết quả thành công. Có thể hủy tác vụ nền.

## Đọc các chỉ số so sánh

- `TAT = CT − AT`; `WT = TAT − BT`; `RT = ST − AT`.
- `throughput = số tiến trình / makespan`; `utilization = tổng burst / makespan`. Mô phỏng bắt đầu từ 0, gồm cả idle đầu phiên.
- Maximum WT cho biết tiến trình phải chờ lâu nhất; độ lệch chuẩn WT mô tả độ phân tán thời gian chờ.
- Jain fairness dùng `x_i = BT_i / TAT_i` (nghịch đảo slowdown), `J = (Σx_i)² / (n × Σx_i²)`. Chỉ số gần 1 nghĩa là các tiến trình có tỷ lệ thời gian được phục vụ so với thời gian trong hệ thống tương đồng hơn. Đây là một góc nhìn, cần đọc cùng WT/TAT/RT và priority; không phải kết luận duy nhất về công bằng.

Mô hình giả định biết trước CPU burst, một CPU, không I/O blocking, không aging, không chi phí chuyển ngữ cảnh. Vì các thuật toán đều chạy khi có tiến trình ready và bỏ qua overhead, **throughput/utilization có thể giống nhau** trên cùng workload dù waiting/response/fairness khác nhau. Dữ liệu `fairness.csv` và bảng `comparisons/fairness-summary.csv` cho phép xem trực tiếp khác biệt đó.
