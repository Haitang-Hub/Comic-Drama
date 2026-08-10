package com.comicdrama.common.queue;

import com.comicdrama.common.constant.QueueConstants;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.MessageQueueSelector;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * RocketMQ 任务队列实现（Phase-5）。
 * <p>
 * 使用 RocketMQ 实现分布式任务队列：
 * <ul>
 *   <li>Producer 根据 priority 路由到不同 queue index（低优先级数字 → 低 queue index → 高优先级）</li>
 *   <li>PushConsumer 消费所有队列，按优先级排序放入本地缓冲</li>
 *   <li>dequeue() 从本地缓冲区取队首，保证同节点优先级顺序</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "task.queue.type", havingValue = "rocketmq")
public class RocketMQTaskQueue implements TaskQueue {

    private static final String TOPIC = "COMIC_TASK_QUEUE";
    private static final int QUEUE_NUM = 10;
    private static final String PRODUCER_GROUP = "comic-task-producer-group";
    private static final String CONSUMER_GROUP = "comic-task-consumer-group";

    @Value("${rocketmq.namesrv-addr:127.0.0.1:9876}")
    private String namesrvAddr;

    @Value("${rocketmq.producer.send-timeout-ms:10000}")
    private int sendTimeoutMs;

    private final ObjectMapper objectMapper;

    private DefaultMQProducer producer;
    private DefaultMQPushConsumer consumer;

    private final PriorityBlockingQueue<TaskQueueEntry> localBuffer =
            new PriorityBlockingQueue<>(QueueConstants.QUEUE_CAPACITY,
                    Comparator.comparingInt((TaskQueueEntry e) -> e.getPriority() == null ? QueueConstants.DEFAULT_PRIORITY : e.getPriority())
                            .thenComparing(e -> e.getEnqueuedTime() == null ? LocalDateTime.now() : e.getEnqueuedTime()));

    private final ReentrantLock lock = new ReentrantLock();

    public RocketMQTaskQueue(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() throws MQClientException {
        producer = new DefaultMQProducer(PRODUCER_GROUP);
        producer.setNamesrvAddr(namesrvAddr);
        producer.setSendMsgTimeout(sendTimeoutMs);
        producer.setRetryTimesWhenSendFailed(2);
        producer.start();

        consumer = new DefaultMQPushConsumer(CONSUMER_GROUP);
        consumer.setNamesrvAddr(namesrvAddr);
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.setConsumeThreadMin(4);
        consumer.setConsumeThreadMax(16);
        consumer.subscribe(TOPIC, "*");

        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                try {
                    TaskQueueEntry entry = parseEntry(msg);
                    if (entry != null) {
                        localBuffer.offer(entry);
                    }
                } catch (Exception e) {
                    log.error("RocketMQ 消息解析失败: msgId={}, error={}", msg.getMsgId(), e.getMessage());
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });

        consumer.start();
        log.info("[RocketMQTaskQueue] 初始化完成，namesrv={}, topic={}, queues={}", namesrvAddr, TOPIC, QUEUE_NUM);
    }

    @PreDestroy
    public void destroy() {
        try {
            if (producer != null) {
                producer.shutdown();
            }
            if (consumer != null) {
                consumer.shutdown();
            }
            log.info("[RocketMQTaskQueue] 生产者/消费者已关闭");
        } catch (Exception e) {
            log.error("[RocketMQTaskQueue] 关闭异常", e);
        }
    }

    @Override
    public boolean enqueue(TaskQueueEntry entry) {
        if (entry == null || entry.getTaskId() == null) {
            return false;
        }
        try {
            if (entry.getPriority() == null) {
                entry.setPriority(QueueConstants.DEFAULT_PRIORITY);
            }
            if (entry.getEnqueuedTime() == null) {
                entry.setEnqueuedTime(LocalDateTime.now());
            }

            int queueIndex = priorityToQueueIndex(entry.getPriority());
            String json = objectMapper.writeValueAsString(entry);

            Message message = new Message(TOPIC, "TASK", entry.getTaskId().toString(), json.getBytes());

            producer.send(message, new MessageQueueSelector() {
                @Override
                public MessageQueue select(List<MessageQueue> mqs, Message msg, Object arg) {
                    int idx = (Integer) arg;
                    return mqs.get(Math.min(idx, mqs.size() - 1));
                }
            }, queueIndex);

            log.debug("[RocketMQTaskQueue] 任务入队 taskId={}, priority={}, queueIndex={}",
                    entry.getTaskId(), entry.getPriority(), queueIndex);
            return true;
        } catch (Exception e) {
            log.error("[RocketMQTaskQueue] 任务入队失败 taskId={}: {}", entry.getTaskId(), e.getMessage());
            return false;
        }
    }

    @Override
    public TaskQueueEntry dequeue() {
        TaskQueueEntry entry = localBuffer.poll();
        if (entry != null) {
            refreshPositions();
        }
        return entry;
    }

    @Override
    public TaskQueueEntry peek() {
        return localBuffer.peek();
    }

    @Override
    public int size() {
        return localBuffer.size();
    }

    @Override
    public int getPosition(Long taskId) {
        if (taskId == null) {
            return 0;
        }
        List<TaskQueueEntry> sorted = sortedSnapshot();
        for (int i = 0; i < sorted.size(); i++) {
            if (taskId.equals(sorted.get(i).getTaskId())) {
                return i + 1;
            }
        }
        return 0;
    }

    @Override
    public boolean remove(Long taskId) {
        if (taskId == null) {
            return false;
        }
        boolean removed = localBuffer.removeIf(e -> taskId.equals(e.getTaskId()));
        if (removed) {
            refreshPositions();
        }
        return removed;
    }

    @Override
    public List<TaskQueueEntry> waitingList() {
        return sortedSnapshot();
    }

    private int priorityToQueueIndex(int priority) {
        int normalized = Math.max(0, Math.min(QUEUE_NUM - 1, priority / 10));
        return normalized;
    }

    private TaskQueueEntry parseEntry(MessageExt msg) throws JsonProcessingException {
        String body = new String(msg.getBody());
        return objectMapper.readValue(body, TaskQueueEntry.class);
    }

    private List<TaskQueueEntry> sortedSnapshot() {
        lock.lock();
        try {
            return new ArrayList<>(localBuffer);
        } finally {
            lock.unlock();
        }
    }

    private void refreshPositions() {
        lock.lock();
        try {
            List<TaskQueueEntry> sorted = new ArrayList<>(localBuffer);
            sorted.sort(localBuffer.comparator());
            for (int i = 0; i < sorted.size(); i++) {
                sorted.get(i).setQueuePosition(i + 1);
            }
        } finally {
            lock.unlock();
        }
    }
}