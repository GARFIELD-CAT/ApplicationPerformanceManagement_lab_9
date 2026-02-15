package ru.dayagunov.lab_9.model;

import lombok.Getter;

import java.nio.ByteBuffer;

@Getter
public class TaskRecord {
    private static final int RECORD_SIZE = 128; // Фиксированный размер записи
    private static final int DESCRIPTION_START = 9; // Смещение после ID (4 байта) + completed (1 байт) + amount (4 байта)

    private Integer id;
    private Boolean completed;
    private Integer amount;
    private String description;

    // Конструктор для генерации
    public TaskRecord(Integer id, String description, boolean completed, Integer amount) {
        this.id = id;
        this.completed = completed;
        this.amount = amount;
        this.description = description.length() > 100 ? description.substring(0, 100) : description;
    }

    // Конструктор для чтения из буфера
    public TaskRecord(ByteBuffer buffer) {
        // 1. ID (4 байта)
        this.id = buffer.getInt();
        // 2. Completed (1 байт)
        this.completed = buffer.get() == 1;
        // 3. Amount (4 байта)
        this.amount = buffer.getInt();

        // 4. Description (оставшаяся часть буфера)
        byte[] descBytes = new byte[RECORD_SIZE - DESCRIPTION_START - 4];
        buffer.get(descBytes);
        // Декодируем, игнорируя нули
        this.description = new String(descBytes).trim();
    }

    // Сериализация в ByteBuffer
    public ByteBuffer toByteBuffer() {
        ByteBuffer buffer = ByteBuffer.allocate(RECORD_SIZE);

        buffer.putInt(this.id != null ? this.id : 0); // ID
        buffer.put((byte) (this.completed ? 1 : 0)); // Completed
        buffer.putInt(this.amount != null ? this.amount : 0); // Amount

        // Description (заполняем остаток буфера)
        byte[] descBytes = this.description.getBytes();
        buffer.put(descBytes);
        // Заполняем оставшееся место нулями (чтобы размер был фиксированным)
        for (int i = descBytes.length; i < RECORD_SIZE - DESCRIPTION_START - 4; i++) {
            buffer.put((byte) 0);
        }

        buffer.flip(); // Подготовка к чтению/записи
        return buffer;
    }

    public static int getRecordSize() {
        return RECORD_SIZE;
    }
}