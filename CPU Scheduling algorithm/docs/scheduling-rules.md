# Quy tắc lập lịch

## Quy ước đầu vào

- `processId` là duy nhất; `arrivalTime >= 0`; `burstTime > 0`.
- Số priority nhỏ hơn biểu thị độ ưu tiên cao hơn.
- Round Robin yêu cầu `quantum > 0`.
- Mọi thời điểm và thời lượng dùng đơn vị thời gian nguyên.

## Tie-break

- FCFS: arrival time tăng dần, sau đó `processId` tăng dần.
- SJF: burst time nhỏ nhất; hòa thì arrival time sớm hơn, sau đó `processId`.
- SRTF: remaining time nhỏ nhất; hòa giữ tiến trình đang chạy nếu vẫn tối ưu, nếu không thì arrival time sớm hơn rồi `processId`.
- Priority: priority nhỏ nhất; hòa thì arrival time sớm hơn rồi `processId`.
- Round Robin: FIFO. Tiến trình đến trong một quantum được đưa vào hàng đợi trước tiến trình vừa hết quantum.

## CPU idle và preemption

Nếu không có tiến trình ready, CPU idle đến arrival kế tiếp; khoảng idle phải có mặt trên Gantt chart. FCFS, SJF và Priority không bị ngắt sau khi bắt đầu chạy. SRTF xét lại lựa chọn khi có tiến trình mới đến. Round Robin ngắt khi hết quantum hoặc tiến trình hoàn tất.

## Công thức

- `completionTime`: thời điểm tiến trình kết thúc.
- `turnaroundTime = completionTime - arrivalTime`.
- `waitingTime = turnaroundTime - burstTime`.
- `responseTime = firstStartTime - arrivalTime`.
- `throughput = số tiến trình hoàn tất / tổng thời gian mô phỏng`, tính từ thời điểm bắt đầu mô phỏng đến khi tiến trình cuối cùng hoàn tất.
- `CPU utilization = tổng burstTime / tổng thời gian mô phỏng`; nếu tổng thời gian bằng 0 thì không xác định.

Trung bình các chỉ số thời gian được tính trên toàn bộ tiến trình và làm tròn 2 chữ số thập phân khi hiển thị.

## Fairness và throughput

Không có thuật toán tối ưu mọi chỉ số. Đánh giá nên xét đồng thời turnaround, waiting, response, throughput, utilization và phân phối thời gian chờ giữa các tiến trình.

- **FCFS** công bằng theo thứ tự đến và ít overhead, nhưng tiến trình dài ở đầu hàng đợi có thể gây convoy effect.
- **SJF** thường giảm waiting/turnaround trung bình nếu biết trước CPU burst, nhưng tiến trình dài có thể bị đói khi liên tục có tiến trình ngắn hơn.
- **SRTF** phản hồi nhanh với công việc ngắn mới đến, đổi lại có thể ngắt thường xuyên và làm tiến trình dài bị đói.
- **Priority** phục vụ công việc ưu tiên cao trước; không có aging, tiến trình priority thấp có nguy cơ bị đói.
- **Round Robin** thường cho response công bằng hơn trong môi trường chia sẻ. Quantum quá lớn gần giống FCFS; quantum quá nhỏ làm tăng chuyển ngữ cảnh (mô hình này không tính overhead đó).

Throughput là số tiến trình hoàn tất trên một đơn vị thời gian; nó phụ thuộc workload và khoảng idle, không đồng nghĩa với fairness. Với workload CPU-bound liên tục và bỏ qua overhead, nhiều thuật toán có thể có cùng throughput/utilization dù waiting và response khác nhau. Nên báo cáo cả Gantt chart và nhiều workload thay vì kết luận từ một chỉ số.

Mô hình giả định burst time đã biết, một CPU, không có I/O blocking, priority không có aging và chi phí chuyển ngữ cảnh bằng 0; cần nêu các giới hạn này khi so sánh với hệ điều hành thực.
